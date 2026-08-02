package com.necromagik.pureclock.data

import android.content.Context
import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone as IcuTimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    // 1. ИНИЦИАЛИЗАЦИЯ И ПОИСК
    // ============================================================================

    suspend fun searchCities(query: String): List<WorldCity> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext getInitialSystemCities()

        // Убедимся, что база не пуста
        ensureDatabaseInitialized()

        val entities = cityDao.searchCities(q)
        entities.map { entity ->
            WorldCity(
                id = entity.id,
                cityName = entity.cityName,
                countryName = entity.countryName,
                timeZoneId = entity.timeZoneId,
                searchKeywords = entity.searchKeywords
            )
        }
    }

    suspend fun getSavedCities(savedIds: Set<String>): List<WorldCity> = withContext(Dispatchers.IO) {
        ensureDatabaseInitialized()
        val entities = cityDao.getCitiesByIds(savedIds)
        entities.map { entity ->
            WorldCity(
                id = entity.id,
                cityName = entity.cityName,
                countryName = entity.countryName,
                timeZoneId = entity.timeZoneId,
                searchKeywords = entity.searchKeywords
            )
        }
    }

    // ============================================================================
    // 2. ФОНОВАЯ СИНХРОНИЗАЦИЯ И ОБНОВЛЕНИЕ БАЗЫ
    // ============================================================================

    suspend fun checkAndSyncDatabase(remoteJsonUrl: String? = null) = withContext(Dispatchers.IO) {
        try {
            val currentVersion = prefs.getInt("cities_db_version", 1)

            // Здесь логика получения версии с твоего сервера/GitHub
            // Допустим, если передали URL для обновления:
            if (remoteJsonUrl != null) {
                val downloadedCities = downloadRemoteCities(remoteJsonUrl)
                if (downloadedCities.isNotEmpty()) {
                    cityDao.insertAll(downloadedCities)
                    prefs.edit().putInt("cities_db_version", currentVersion + 1).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ============================================================================
    // 3. ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (Инициализация по умолчанию)
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
            val rawRegion = parts.first()

            val icuExemplarCity = tzNames.getExemplarLocationName(zoneIdStr)
            val russianCityName = when {
                !icuExemplarCity.isNullOrEmpty() -> icuExemplarCity
                else -> translateCityFallback(rawCity)
            }

            val countryName = try {
                Locale("", rawRegion).getDisplayCountry(ruLocale).ifEmpty { rawRegion }
            } catch (_: Exception) { rawRegion }

            val keywords = "$rawCity $russianCityName $zoneIdStr $countryName ${translit(russianCityName)}".lowercase()

            list.add(
                CityEntity(
                    id = zoneIdStr.lowercase().replace("/", "_"),
                    cityName = russianCityName,
                    cityNameEn = rawCity,
                    countryName = countryName,
                    countryCode = rawRegion,
                    timeZoneId = zoneIdStr,
                    searchKeywords = keywords
                )
            )
        }

        // ЯВНО ДОБАВЛЯЕМ ПЕКИН, т.к. в IANA есть только Asia/Shanghai
        list.add(
            CityEntity(
                id = "asia_shanghai_beijing",
                cityName = "Пекин",
                cityNameEn = "Beijing",
                countryName = "Китай",
                countryCode = "CN",
                timeZoneId = "Asia/Shanghai",
                searchKeywords = "пекин beijing китай cn asia/shanghai pekin"
            )
        )

        return list.distinctBy { it.id }
    }

    private fun getInitialSystemCities(): List<WorldCity> {
        // Дефолтный вызов основных городов для экрана по умолчанию
        return listOf(
            WorldCity("asia_shanghai_beijing", "Пекин", "Китай", "Asia/Shanghai", "пекин beijing"),
            WorldCity("europe_moscow", "Москва", "Россия", "Europe/Moscow", "москва moscow"),
            WorldCity("utc", "UTC", "Всемирное время", "UTC", "utc")
        )
    }

    private fun downloadRemoteCities(urlString: String): List<CityEntity> {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        if (conn.responseCode != 200) return emptyList()

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        val result = mutableListOf<CityEntity>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val cityNameRu = obj.getString("name_ru")
            val cityNameEn = obj.optString("name_en", "")
            val countryRu = obj.optString("country_ru", "")
            val tz = obj.getString("timezone")

            result.add(
                CityEntity(
                    id = obj.optString("id", "${tz}_${cityNameEn}".lowercase().replace("/", "_")),
                    cityName = cityNameRu,
                    cityNameEn = cityNameEn,
                    countryName = countryRu,
                    countryCode = obj.optString("country_code", ""),
                    timeZoneId = tz,
                    latitude = obj.optDouble("lat", 0.0),
                    longitude = obj.optDouble("lon", 0.0),
                    searchKeywords = "$cityNameRu $cityNameEn $countryRu $tz ${translit(cityNameRu)}".lowercase()
                )
            )
        }
        return result
    }

    private fun translateCityFallback(englishName: String): String {
        val customMap = mapOf(
            "Brasilia" to "Бразилия", "Sao Paulo" to "Сан-Паулу", "Moscow" to "Москва",
            "Saint Petersburg" to "Санкт-Петербург", "London" to "Лондон", "New York" to "Нью-Йорк",
            "Tokyo" to "Токио", "Paris" to "Париж", "Berlin" to "Берлин", "Rome" to "Рим",
            "Madrid" to "Мадрид", "Beijing" to "Пекин", "Seoul" to "Сеул", "Cairo" to "Каир",
            "Dubai" to "Дубай", "Istanbul" to "Стамбул", "Athens" to "Афины"
        )
        return customMap[englishName] ?: englishName
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