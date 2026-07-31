package com.necromagik.pureclock.data

import android.content.Context
import android.icu.util.TimeZone as IcuTimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray

object WorldClockRepository {

    // ============================================================================
// СЕКЦИЯ 1: ОФЛАЙН ПОЛУЧЕНИЕ СИСТЕМНЫХ ЧАСОВЫХ ПОЯСОВ И СТОЛИЦ
// ============================================================================
    fun getSystemCities(): List<WorldCity> {
        val ruLocale = Locale.forLanguageTag("ru")
        val zoneIds = ZoneId.getAvailableZoneIds()

        return zoneIds.mapNotNull { zoneIdStr ->
            if (!zoneIdStr.contains("/")) return@mapNotNull null

            val parts = zoneIdStr.split("/")
            val rawCity = parts.last().replace("_", " ")
            val rawRegion = parts.first()

            val icuZone = IcuTimeZone.getTimeZone(zoneIdStr)
            val displayName = icuZone.getDisplayName(false, IcuTimeZone.LONG, ruLocale)
            val countryName = try {
                Locale("", rawRegion).getDisplayCountry(ruLocale).ifEmpty { rawRegion }
            } catch (e: Exception) {
                rawRegion
            }

            val russianCityName = translateCityName(rawCity)

            WorldCity(
                id = zoneIdStr.lowercase().replace("/", "_"),
                cityName = russianCityName,
                countryName = countryName,
                timeZoneId = zoneIdStr,
                searchKeywords = "$rawCity $russianCityName $zoneIdStr $displayName $countryName ${translit(russianCityName)}".lowercase()
            )
        }.distinctBy { it.cityName.lowercase() }.sortedBy { it.cityName }
    }

    // ============================================================================
// СЕКЦИЯ 2: ОНЛАЙН-ПОИСК ГОРОДОВ ЧЕРЕЗ OPEN-METEO GEOCODING API
// ============================================================================
    suspend fun searchCityOnline(query: String): List<WorldCity> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=10&language=ru&format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                val results = json.optJSONArray("results") ?: return@withContext emptyList()

                val cities = mutableListOf<WorldCity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val cityName = item.optString("name")
                    val country = item.optString("country", "")
                    val timeZone = item.optString("timezone", "")

                    if (timeZone.isNotEmpty()) {
                        val id = "online_${cityName.lowercase().replace(" ", "_")}_${timeZone.replace("/", "_")}"
                        cities.add(
                            WorldCity(
                                id = id,
                                cityName = cityName,
                                countryName = country,
                                timeZoneId = timeZone,
                                searchKeywords = "$cityName $country $timeZone".lowercase()
                            )
                        )
                    }
                }
                return@withContext cities
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    // ============================================================================
// СЕКЦИЯ 3: ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ПЕРЕВОДА И ТРАНСЛИТЕРАЦИИ
// ============================================================================
    private fun translateCityName(englishName: String): String {
        return mapOf(
            "Brasilia" to "Бразилия",
            "Sao Paulo" to "Сан-Паулу",
            "Moscow" to "Москва",
            "Saint Petersburg" to "Санкт-Петербург",
            "London" to "Лондон",
            "New York" to "Нью-Йорк",
            "Tokyo" to "Токио",
            "Paris" to "Париж",
            "Berlin" to "Берлин",
            "Rome" to "Рим",
            "Madrid" to "Мадрид",
            "Beijing" to "Пекин",
            "Seoul" to "Сеул",
            "Cairo" to "Каир", "Dubai" to "Дубай",
            "Istanbul" to "Стамбул", "Athens" to "Афины"
        )[englishName] ?: englishName
    }

    private fun translit(text: String): String {
        val abcCyr = charArrayOf('а','б','в','г','д','е','ё','ж','з','и','й','к','л','м','н','о','п','р','с','т','у','ф','х','ц','ч','ш','щ','ъ','ы','ь','э','ю','я')
        val abcLat = arrayOf("a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","sch","","y","","e","yu","ya")
        var builder = StringBuilder()
        for (ch in text.lowercase()) {
            val idx = abcCyr.indexOf(ch)
            if (idx >= 0) builder.append(abcLat[idx]) else builder.append(ch)
        }
        return builder.toString()
    }
}