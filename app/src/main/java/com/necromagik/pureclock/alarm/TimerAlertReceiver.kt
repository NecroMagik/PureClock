package com.necromagik.pureclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_ALARM) {
            val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
            val label = intent.getStringExtra(EXTRA_TIMER_LABEL) ?: "Таймер"
            TimerService.triggerAlarm(context, timerId, label)
        }
    }

    companion object {
        const val ACTION_TIMER_ALARM = "com.necromagik.pureclock.ACTION_TIMER_ALARM"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TIMER_LABEL = "extra_timer_label"

        fun scheduleTimerAlarm(
            context: Context,
            timerId: String,
            label: String,
            durationText: String,
            triggerTimeMillis: Long
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, TimerReceiver::class.java).apply {
                action = ACTION_TIMER_ALARM
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_TIMER_LABEL, label)
            }

            val requestCode = timerId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, AlarmAlertActivity::class.java).apply {
                putExtra("IS_TIMER", true)
                putExtra("ALARM_LABEL", label)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                requestCode + 1000,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val clockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        }

        fun cancelTimerAlarm(context: Context, timerId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TimerReceiver::class.java).apply {
                action = ACTION_TIMER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                timerId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}