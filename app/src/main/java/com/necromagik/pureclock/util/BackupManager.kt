package com.necromagik.pureclock.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.ui.viewmodel.TimerItem
import com.necromagik.pureclock.ui.viewmodel.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Keep
data class PrefEntryBackup(
    val type: String,
    val value: Any?
)

@Keep
data class PureClockBackupPayload(
    val backupVersion: Int = 1,
    val createdAt: String,
    val appVersion: String,
    val alarms: List<AlarmEntity> = emptyList(),
    val settingsPrefs: Map<String, PrefEntryBackup> = emptyMap(),
    val uiPrefs: Map<String, PrefEntryBackup> = emptyMap(),
    val timersPrefs: Map<String, PrefEntryBackup> = emptyMap(),
    val scenaryPrefs: Map<String, PrefEntryBackup> = emptyMap()
)

object BackupManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun generateDefaultBackupFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm", Locale.ROOT)
        val timestamp = LocalDateTime.now().format(formatter)
        return "PureClock_Backup_$timestamp.json"
    }

    suspend fun exportBackupToUri(context: Context, targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            val alarms = db.alarmDao().getAllAlarms().firstOrNull().orEmpty()

            val settingsPrefs = dumpSharedPreferences(
                appContext.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE)
            )
            val uiPrefs = dumpSharedPreferences(
                appContext.getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE)
            )
            val timersPrefs = dumpSharedPreferences(
                appContext.getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
            )
            val scenaryPrefs = dumpSharedPreferences(
                appContext.getSharedPreferences("pureclock_scenary_prefs", Context.MODE_PRIVATE)
            )

            val pkgInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val versionName = pkgInfo.versionName ?: "1.35"

            val payload = PureClockBackupPayload(
                backupVersion = 1,
                createdAt = LocalDateTime.now().toString(),
                appVersion = versionName,
                alarms = alarms,
                settingsPrefs = settingsPrefs,
                uiPrefs = uiPrefs,
                timersPrefs = timersPrefs,
                scenaryPrefs = scenaryPrefs
            )

            val jsonString = gson.toJson(payload)
            val outputStream = appContext.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("Не удалось открыть поток для записи файла")

            outputStream.use { stream ->
                stream.write(jsonString.toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            alarms.size
        }
    }

    suspend fun importBackupFromUri(context: Context, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val inputStream = appContext.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Не удалось открыть файл резервной копии")

            val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val payload = gson.fromJson(jsonString, PureClockBackupPayload::class.java)
                ?: throw IllegalStateException("Повреждённый или неверный формат файла бэкапа")

            // 1. Восстанавливаем все SharedPreferences
            restoreSharedPreferences(
                appContext.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE),
                payload.settingsPrefs
            )
            restoreSharedPreferences(
                appContext.getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE),
                payload.uiPrefs
            )
            restoreSharedPreferences(
                appContext.getSharedPreferences("pureclock_scenary_prefs", Context.MODE_PRIVATE),
                payload.scenaryPrefs
            )

            // Нормализуем таймеры (сбрасываем RUNNING -> IDLE, чтобы не сработали старые дедлайны)
            restoreSharedPreferences(
                appContext.getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE),
                payload.timersPrefs
            )
            sanitizeRestoredTimers(appContext)

            // 2. Восстанавливаем будильники в Room и перепривязываем ID для сценариев
            val db = AppDatabase.getDatabase(appContext)
            val alarmDao = db.alarmDao()
            val scheduler = AlarmScheduler(appContext)

            val existingAlarms = alarmDao.getAllAlarms().firstOrNull().orEmpty()
            existingAlarms.forEach { oldAlarm ->
                scheduler.cancel(oldAlarm)
                alarmDao.deleteAlarm(oldAlarm)
            }

            val oldToNewAlarmIdMap = mutableMapOf<Long, Long>()
            for (backupAlarm in payload.alarms) {
                val oldId = backupAlarm.id
                val insertedId = alarmDao.insertAlarm(backupAlarm.copy(id = 0L))
                oldToNewAlarmIdMap[oldId] = insertedId

                val restoredAlarm = backupAlarm.copy(id = insertedId)
                if (restoredAlarm.isEnabled) {
                    scheduler.schedule(restoredAlarm)
                }
            }

            // 3. Обновляем привязки сценариев к новым ID будильников
            remapAlarmScenarioBindings(appContext, oldToNewAlarmIdMap)

            "Восстановлено будильников: ${payload.alarms.size}"
        }
    }

    private fun dumpSharedPreferences(prefs: SharedPreferences): Map<String, PrefEntryBackup> {
        val result = mutableMapOf<String, PrefEntryBackup>()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> result[key] = PrefEntryBackup("BOOLEAN", value)
                is Int -> result[key] = PrefEntryBackup("INT", value)
                is Long -> result[key] = PrefEntryBackup("LONG", value)
                is Float -> result[key] = PrefEntryBackup("FLOAT", value)
                is String -> result[key] = PrefEntryBackup("STRING", value)
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>()
                    result[key] = PrefEntryBackup("STRING_SET", stringSet)
                }
            }
        }
        return result
    }

    private fun restoreSharedPreferences(
        prefs: SharedPreferences,
        backupMap: Map<String, PrefEntryBackup>
    ) {
        if (backupMap.isEmpty()) return
        prefs.edit(commit = true) {
            clear()
            for ((key, entry) in backupMap) {
                val rawVal = entry.value ?: continue
                when (entry.type) {
                    "BOOLEAN" -> (rawVal as? Boolean)?.let { putBoolean(key, it) }
                    "INT" -> (rawVal as? Number)?.toInt()?.let { putInt(key, it) }
                    "LONG" -> (rawVal as? Number)?.toLong()?.let { putLong(key, it) }
                    "FLOAT" -> (rawVal as? Number)?.toFloat()?.let { putFloat(key, it) }
                    "STRING" -> (rawVal as? String)?.let { putString(key, it) }
                    "STRING_SET" -> {
                        if (rawVal is Collection<*>) {
                            putStringSet(key, rawVal.mapNotNull { it?.toString() }.toSet())
                        }
                    }
                }
            }
        }
    }

    private fun sanitizeRestoredTimers(context: Context) {
        val prefs = context.getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("saved_timers_list", null) ?: return
        try {
            val array = gson.fromJson(json, Array<TimerItem>::class.java) ?: return
            val sanitized = array.map { timer ->
                timer.copy(
                    state = TimerState.IDLE,
                    remainingSeconds = timer.initialTimeSeconds,
                    remainingMillis = timer.initialTimeSeconds * 1000L,
                    endTimestampMillis = 0L
                )
            }
            prefs.edit(commit = true) {
                putString("saved_timers_list", gson.toJson(sanitized))
            }
        } catch (_: Exception) {}
    }

    private fun remapAlarmScenarioBindings(context: Context, idMap: Map<Long, Long>) {
        if (idMap.isEmpty()) return
        val prefs = context.getSharedPreferences("pureclock_scenary_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("alarm_scenario_bindings_json", null) ?: return
        try {
            val jsonObj = JSONObject(rawJson)
            val updatedObj = JSONObject()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val keyStr = keys.next()
                val oldAlarmId = keyStr.toLongOrNull()
                val scenarioId = jsonObj.optString(keyStr, "")
                if (oldAlarmId != null && scenarioId.isNotEmpty()) {
                    val newId = idMap[oldAlarmId] ?: oldAlarmId
                    updatedObj.put(newId.toString(), scenarioId)
                }
            }
            prefs.edit(commit = true) {
                putString("alarm_scenario_bindings_json", updatedObj.toString())
            }
        } catch (_: Exception) {}
    }
}