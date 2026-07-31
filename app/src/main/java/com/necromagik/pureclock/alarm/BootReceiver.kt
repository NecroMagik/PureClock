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

        // Обрабатываем перезагрузку, обновление пакета и смену времени/часового пояса
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            // Держим BroadcastReceiver активным для фоновых операций
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = AlarmScheduler(context)

                    // Вычитываем все активные будильники из Room
                    val enabledAlarms = db.alarmDao().getEnabledAlarmsSync()

                    enabledAlarms.forEach { alarm ->
                        scheduler.schedule(alarm)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // Вызывается СТРОГО один раз при завершении всех операций
                    pendingResult.finish()
                }
            }
        }
    }
}