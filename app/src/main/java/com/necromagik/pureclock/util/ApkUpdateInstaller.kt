package com.necromagik.pureclock.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApkUpdateInstaller {

    private const val TAG = "PureClock_UPDATER"

    /**
     * Проверяет, разрешена ли установка APK для нашего приложения
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Открывает системный экран включения галочки "Разрешить установку из этого источника"
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось открыть настройки установки пакетов: ${e.message}")
            }
        }
    }

    /**
     * Скачивает APK в фоне с отдачей прогресса (0f..1f) и возвращает готовый File
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        tagName: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        Log.i(TAG, "==> Начало фоновой загрузки APK: $downloadUrl")
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            // Очищаем старые скачанные APK, чтобы не засорять память
            updatesDir.listFiles()?.forEach { it.delete() }

            val safeTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outputFile = File(updatesDir, "PureClock_$safeTag.apk")

            var currentUrl = downloadUrl
            var redirects = 0
            var connection: HttpURLConnection

            // GitHub Releases перенаправляет (302 Redirect) на сервер объектов Amazon S3 / GitHub Objects
            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PureClock-Android-Updater")
                    connectTimeout = 15000
                    readTimeout = 20000
                }
                connection.connect()

                val code = connection.responseCode
                if (code in 301..308) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location != null && redirects < 5) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                break
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "!!!! Ошибка скачивания APK: HTTP ${connection.responseCode}")
                return@withContext null
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "==> APK успешно скачан: ${outputFile.absolutePath} (${outputFile.length()} байт)")
            return@withContext outputFile
        } catch (e: Exception) {
            Log.e(TAG, "!!!! Сбой при скачивании APK: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Запускает системное окно установки APK через FileProvider
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_SHORT).show()
                return
            }

            if (!canInstallPackages(context)) {
                Toast.makeText(
                    context,
                    "Разрешите установку обновлений для PureClock и нажмите «Установить» ещё раз",
                    Toast.LENGTH_LONG
                ).show()
                openInstallPermissionSettings(context)
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "!!!! Ошибка запуска установщика APK: ${e.message}", e)
            Toast.makeText(context, "Ошибка запуска установщика: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}