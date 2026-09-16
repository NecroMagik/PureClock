package com.necromagik.pureclock.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RuStoreUpdateManager(private val context: Context) {

    data class RuStoreAppInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val whatsNew: String?
    )

    companion object {
        const val RUSTORE_PUBLIC_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC62lhQfSo6S3HqGnVzlqj1420+MtZtoImCQtGH3yrpX7eXgxRThNZQKhovpoGfOryRcJlGwGNJyGN2LpAK46We4kCa+v5biVmLOKIeYuR7dpJu7rj7D9AIfJ/1EAYRHxnUOryh+uG27nkqaIed1zMGIfuRGQH6QUPsR1ozJgdt8X7KxpUZAmifZX1W6UlqlRjr9wnudfHvLTqEnaaw5jFrTr29IKYpcrHrBxAu8jjmMZISIp8ucw7CmbDryoPXPwQAcc7ZoVNQ+3/MFOzd4aedw6NdWXHzSxrWcfy6QFXX7TYZ2aLe8HbIETKoIQMgC9A9MVTpcTLkWeI1EaiXQWb1AgMBAAECggEAC+kWHw9TbULY4IzONs+ANqhIEuJns7Y7fa+nrosFr7mXuNM0rPw3MwX0BFyPP6idU4IDazShP5dD68kdCmynZoDSkG52XzxallrGTryMBuiwfdy+stY3swrKoXLjiBzc5x/VXVfiImiCFbdzCZcpm/b3k7Ct5Rda7ok/0SnX6GST/PHWkj9SD6+IpQ0vb3Kxn5A+JXlP4mzt/eOdvEjXIT8FYDo7lGfvLcCY8ms+01rnUpnsI9ArGfPSbbAwOsbFXYLhK1w7NjPyDjDn/ZKEUXDwjsYPnQGRCRUcrExQdre5uXu1ywo9AwEyzau1H/qm5fym/irgQvzuEIDNNoRsMQKBgQDnNIgzIGF3Tst0xwK4fDxZeinhC68Zvna0CtXbpxE4GgSYv3BmyueNbj6ovmrSu8Qvis235hC4STF3dX66/eHE/RVT9tsKXMrGBHb0vKeInN6HVcq5qCjRqDx6SGf9II4GhcnCMqLBhOLS015Ux6WPOJhYctC3AQaqbBluV+FAHQKBgQDO5Cw2RdlVJAOhZBCTKNyelROen5XEcX+AFT4uA0UvHd2rlCgQJOCnjitr4aQnxp4fbQ7QcPa0Goyh6hQlkaKbcA7jO2xSDt5RaRA3R+UKI9b7/07XezKVfxco8yXpbbtDe1batqh2KLnE3Dxtd+F3XYsbe4G5sqgOytjO1Wm6uQKBgQCU8U+IeCSxwk3pRccn58jM75kJBAjiMcOwioZogUDjfkdVy4XygmYWw12UQU75wMJKykVqYciFn4lBZqykc6c8yrkxpQZIr6xlUjz7vojCxiPI1WjKn1zka1fCguCSvaUg6JEoDIyy1BN3M3lCbnbmQ9RIrnZsXjnYEdl0LyLJtQKBgDvAysQiCFL+w82pCOB3vMBQbgoyLR8/aIOlnv/LEgA5r/wOHkQLYpcCCKhv0ulqA+EvrkelWJtLz7Iz2P3AwiYCh0o5r19DzBHXNkfBC/WRxFWjtzGk80caHaZGE30HpD4pMTyZ5K27tWuV8B3tscC306VsVls7ri4Xh+a03ISBAoGAQM7TMQVeAHAqRQaOq3G5wCXttA0VvYSZ+Dr3syfXk3hdH2TzIyFd2AU0ijxI4WjkK866DQXXgJSJjQLkE470t2K4Qv4Sv5KnrHr2HGBY1PImb0yllJKGiMBquYzHS/KPGkBHtpfC3vCvjbsm6mC0DUhmUfA2rBS+7H2FVckwHbI="

        private const val RUSTORE_API_BASE = "https://public-api.rustore.ru/public/v1/application"
    }

    suspend fun checkUpdateAvailability(): RuStoreAppInfo? = withContext(Dispatchers.IO) {
        try {
            val packageName = context.packageName
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = packageInfo.longVersionCode.toInt()

            val requestUrl = URL("$RUSTORE_API_BASE/$packageName/version")
            val connection = (requestUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val body = json.optJSONObject("body") ?: json
                val remoteVersionCode = body.optInt("versionCode", 0)
                val remoteVersionName = body.optString("versionName", "")
                val whatsNew = if (body.has("whatsNew")) body.getString("whatsNew") else null
                val appUrl = "https://www.rustore.ru/catalog/app/$packageName"

                if (remoteVersionCode > currentVersionCode) {
                    return@withContext RuStoreAppInfo(
                        versionCode = remoteVersionCode,
                        versionName = remoteVersionName,
                        downloadUrl = appUrl,
                        whatsNew = whatsNew
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun openStorePage() {
        val packageName = context.packageName
        val storeIntent = Intent(Intent.ACTION_VIEW, "rustore://apps/details?id=$packageName".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(storeIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, "https://www.rustore.ru/catalog/app/$packageName".toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }
}