package com.necromagik.pureclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class TimerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimerReceiver"
        const val ACTION_TIMER_ALARM = "com.necromagik.pureclock.ACTION_TIMER_ALARM"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TIMER_LABEL = "extra_timer_label"
        const val EXTRA_TIMER_DURATION = "extra_timer_duration"

        fun scheduleTimerAlarm(
            context: Context,
            id: String,
            label: String,
            durationText: String,
            triggerAtMillis: Long
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "ОШИБКА: Нет разрешения SCHEDULE_EXACT_ALARM!")
            }

            val intent = Intent(context, TimerReceiver::class.java).apply {
                action = ACTION_TIMER_ALARM
                putExtra(EXTRA_TIMER_ID, id)
                putExtra(EXTRA_TIMER_LABEL, label)
                putExtra(EXTRA_TIMER_DURATION, durationText)
            }

            val requestCode = (id.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d(TAG, "Планирование таймера '$label' ($durationText) на $triggerAtMillis")

            val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        }

        fun cancelTimerAlarm(context: Context, id: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerReceiver::class.java).apply {
                action = ACTION_TIMER_ALARM
            }
            val requestCode = (id.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "Отмена таймера ID: $id")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive вызван! Action: ${intent.action}")

        if (intent.action == ACTION_TIMER_ALARM) {
            val label = intent.getStringExtra(EXTRA_TIMER_LABEL) ?: "Таймер"
            val durationText = intent.getStringExtra(EXTRA_TIMER_DURATION) ?: ""

            val pendingResult = goAsync()

            try {
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_TRIGGER_ALARM
                    putExtra(TimerService.EXTRA_LABEL, label)
                    putExtra("EXTRA_DURATION", durationText)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска службы из ресивера", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}