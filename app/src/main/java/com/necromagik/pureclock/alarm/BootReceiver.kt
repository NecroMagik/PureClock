package com.necromagik.pureclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.necromagik.pureclock.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Обрабатываем только события загрузки и обновления пакета.
        // Смена времени/пояса обрабатывается системными интентами напрямую в AlarmReceiver/WidgetProvider.
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Безопасно продлеваем жизнь BroadcastReceiver для работы с Room в фоне
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = AlarmScheduler(context)

                    // Восстанавливаем только через AlarmManager (никаких startForegroundService!)
                    val enabledAlarms = db.alarmDao().getEnabledAlarmsSync()
                    enabledAlarms.forEach { alarm ->
                        scheduler.schedule(alarm)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}