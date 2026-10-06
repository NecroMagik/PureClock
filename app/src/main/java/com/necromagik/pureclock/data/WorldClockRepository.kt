package com.necromagik.pureclock.data

import android.content.Context
import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone as IcuTimeZone
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.util.Locale

class WorldClockRepository(
    private val cityDao: CityDao,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("pure_clock_db_sync", Context.MODE_PRIVATE)
    private val ruLocale = Locale.forLanguageTag("ru")
    private val tzNames = TimeZoneNames.getInstance(ruLocale)

    // ============================================================================
    // 1. ПОИСК
    // ============================================================================

    suspend fun searchCities(query: String): List<WorldCity> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext getInitialSystemCities()

        ensureDatabaseInitialized()

        val localEntities = cityDao.searchCities(q.lowercase())
        if (localEntities.isNotEmpty()) {
            return@withContext localEntities.map { it.toWorldCity() }
        }

        if (q.length >= 2) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Поиск города в сети...", Toast.LENGTH_SHORT).show()
            }

            val remoteEntities = fetchCitiesOnline(q)
            if (remoteEntities.isNotEmpty()) {
                cityDao.insertAll(remoteEntities)
                return@withContext remoteEntities.map { it.toWorldCity() }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Город не найден", Toast.LENGTH_SHORT).show()
                }
            }
        }

        emptyList()
    }

    suspend fun getSavedCities(savedIds: Set<String>): List<WorldCity> = withContext(Dispatchers.IO) {
        ensureDatabaseInitialized()
        val entities = cityDao.getCitiesByIds(savedIds)
        entities.map { it.toWorldCity() }
    }

    // ============================================================================
    // 2. СЕТЕВОЙ ПОИСК С РУССКИМИ СТРАНАМИ И ГОРОДАМИ
    // ============================================================================

    private fun fetchCitiesOnline(query: String): List<CityEntity> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=10&language=ru&format=json"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                if (!root.has("results")) return emptyList()

                val results = root.getJSONArray("results")
                val found = mutableListOf<CityEntity>()

                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    val rawName = obj.optString("name", "")
                    val rawCountry = obj.optString("country", "")
                    val countryCode = obj.optString("country_code", "").uppercase()
                    val tz = obj.optString("timezone", "")
                    val lat = obj.optDouble("latitude", 0.0)
                    val lon = obj.optDouble("longitude", 0.0)

                    // Определяем точное русское название страны по её коду ISO (RU, IL, DE...)
                    val resolvedCountry = when {
                        countryCode.isNotEmpty() -> Locale("", countryCode).getDisplayCountry(ruLocale)
                        rawCountry.isNotEmpty() -> rawCountry
                        else -> "Земля"
                    }

                    if (rawName.isNotEmpty() && tz.isNotEmpty()) {
                        val cityId = "${tz}_${rawName}_${obj.optLong("id")}".lowercase().replace("/", "_")
                        val keywords = "$rawName $resolvedCountry $tz ${translit(rawName)}".lowercase()

                        found.add(
                            CityEntity(
                                id = cityId,
                                cityName = rawName,
                                cityNameEn = obj.optString("name", rawName),
                                countryName = resolvedCountry,
                                countryCode = countryCode,
                                timeZoneId = tz,
                                latitude = lat,
                                longitude = lon,
                                searchKeywords = keywords
                            )
                        )
                    }
                }
                return found.distinctBy { it.id }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    // ============================================================================
    // 3. ИНИЦИАЛИЗАЦИЯ И СИСТЕМНЫЙ МАППИНГ ГОРОДОВ И СТРАН
    // ============================================================================

    private suspend fun ensureDatabaseInitialized() {
        if (cityDao.getCount() == 0) {
            val systemCities = generateInitialCitiesFromSystem()
            cityDao.insertAll(systemCities)
        }
    }

    private fun generateInitialCitiesFromSystem(): List<CityEntity> {
        val zoneIds = ZoneId.getAvailableZoneIds()
        val list = mutableListOf<CityEntity>()

        for (zoneIdStr in zoneIds) {
            if (!zoneIdStr.contains("/")) continue

            val parts = zoneIdStr.split("/")
            val rawCity = parts.last().replace("_", " ")

            // 1. Получаем правильное русское название города из ICU базы Android
            val icuExemplarCity = tzNames.getExemplarLocationName(zoneIdStr)
            val russianCityName = when {
                !icuExemplarCity.isNullOrEmpty() -> icuExemplarCity
                else -> translateCityFallback(rawCity)
            }

            // 2. Получаем 2-буквенный ISO код страны часового пояса (напр. "Asia/Tel_Aviv" -> "IL")
            val countryIsoCode = try {
                IcuTimeZone.getRegion(zoneIdStr) ?: ""
            } catch (_: Exception) { "" }

            // 3. Получаем официальное русское название страны
            val resolvedCountry = if (countryIsoCode.isNotEmpty() && countryIsoCode.length == 2) {
                Locale("", countryIsoCode).getDisplayCountry(ruLocale)
            } else {
                when (parts.first()) {
                    "Etc" -> "Всемирное время"
                    else -> translateRegionFallback(parts.first())
                }
            }

            val keywords = "$rawCity $russianCityName $zoneIdStr $resolvedCountry ${translit(russianCityName)}".lowercase()

            list.add(
                CityEntity(
                    id = zoneIdStr.lowercase().replace("/", "_"),
                    cityName = russianCityName,
                    cityNameEn = rawCity,
                    countryName = resolvedCountry,
                    countryCode = countryIsoCode,
                    timeZoneId = zoneIdStr,
                    searchKeywords = keywords
                )
            )
        }

        // Популярные мегаполисы, отсутствующие напрямую как корневые IANA узлы
        val customMajorCities = listOf(
            CityEntity("asia_shanghai_beijing", "Пекин", "Beijing", "Китай", "CN", "Asia/Shanghai", 39.9, 116.4, "пекин beijing китай cn asia/shanghai pekin"),
            CityEntity("asia_jerusalem_tel_aviv", "Тель-Авив", "Tel Aviv", "Израиль", "IL", "Asia/Jerusalem", 32.08, 34.78, "тель авив tel aviv израиль il asia/jerusalem"),
            CityEntity("utc_standard", "UTC", "UTC", "Всемирное время", "", "UTC", 0.0, 0.0, "utc всемирное время gmt")
        )

        list.addAll(customMajorCities)
        return list.distinctBy { it.id }
    }

    private fun getInitialSystemCities(): List<WorldCity> {
        return listOf(
            WorldCity("europe_moscow", "Москва", "Россия", "Europe/Moscow", "москва moscow"),
            WorldCity("asia_shanghai_beijing", "Пекин", "Китай", "Asia/Shanghai", "пекин beijing"),
            WorldCity("utc_standard", "UTC", "Всемирное время", "UTC", "utc")
        )
    }

    private fun CityEntity.toWorldCity(): WorldCity = WorldCity(
        id = id,
        cityName = cityName,
        countryName = countryName,
        timeZoneId = timeZoneId,
        searchKeywords = searchKeywords
    )

    private fun translateCityFallback(englishName: String): String {
        val customMap = mapOf(
            "Brasilia" to "Бразилия", "Sao Paulo" to "Сан-Паулу", "Moscow" to "Москва",
            "Saint Petersburg" to "Санкт-Петербург", "London" to "Лондон", "New York" to "Нью-Йорк",
            "Tokyo" to "Токио", "Paris" to "Париж", "Berlin" to "Берлин", "Rome" to "Рим",
            "Madrid" to "Мадрид", "Beijing" to "Пекин", "Seoul" to "Сеул", "Cairo" to "Каир",
            "Dubai" to "Дубай", "Istanbul" to "Стамбул", "Athens" to "Афины", "Tel Aviv" to "Тель-Авив"
        )
        return customMap[englishName] ?: englishName
    }

    private fun translateRegionFallback(region: String): String = when (region) {
        "Europe" -> "Европа"
        "Asia" -> "Азия"
        "America" -> "Америка"
        "Africa" -> "Африка"
        "Australia" -> "Австралия"
        "Pacific" -> "Океания"
        "Atlantic" -> "Атлантика"
        "Indian" -> "Индийский океан"
        else -> region
    }

    private fun translit(text: String): String {
        val abcCyr = charArrayOf('а','б','в','г','д','е','ё','ж','з','и','й','к','л','м','н','о','п','р','с','т','у','ф','х','ц','ч','ш','щ','ъ','ы','ь','э','ю','я')
        val abcLat = arrayOf("a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","sch","","y","","e","yu","ya")
        val builder = StringBuilder()
        for (ch in text.lowercase()) {
            val idx = abcCyr.indexOf(ch)
            if (idx >= 0) builder.append(abcLat[idx]) else builder.append(ch)
        }
        return builder.toString()
    }
}