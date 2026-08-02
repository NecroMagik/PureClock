package com.necromagik.pureclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.util.ClockIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UPDATE_LIVE_ICON = "com.necromagik.pureclock.ACTION_UPDATE_LIVE_ICON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // 0. Обновление живой иконки/ярлыка при каждой минуте или по сигналу
        if (action == Intent.ACTION_TIME_TICK || action == ACTION_UPDATE_LIVE_ICON) {
            ClockIconManager(context).updateDynamicShortcut()
            if (action == ACTION_UPDATE_LIVE_ICON) return
        }

        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId == -1L && action != Intent.ACTION_TIME_TICK) return

        val db = AppDatabase.getDatabase(context)
        val pendingResult = goAsync()

        // 1. Предварительное уведомление
        if (action == "ACTION_SHOW_UPCOMING_NOTICE") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null && alarm.isEnabled) {
                        val noticeCenter = NoticeCenter(context)
                        noticeCenter.showUpcomingAlarmNotice(alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 2. Обработка пропуска из предварительного уведомления
        if (action == NoticeCenter.ACTION_SKIP_UPCOMING) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        val scheduler = AlarmScheduler(context)
                        val triggerTime = scheduler.calculateTriggerTime(alarm)

                        val updated = alarm.copy(skippedDateMillis = triggerTime)
                        db.alarmDao().updateAlarm(updated)

                        NoticeCenter(context).dismissUpcomingNotice(alarmId)
                        scheduler.schedule(updated)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 3. Основной сигнал будильника
        if (alarmId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = db.alarmDao().getAlarmById(alarmId)

                    if (alarm != null) {
                        NoticeCenter(context).dismissUpcomingNotice(alarmId)

                        if (alarm.daysOfWeek == 0 && alarm.specificDateMillis == null) {
                            db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
                        }

                        val serviceIntent = Intent(context, AlarmService::class.java).apply {
                            putExtra("EXTRA_ALARM_ID", alarmId)
                            putExtra("RINGTONE_URI", alarm.ringtoneUri)
                            putExtra("EXTRA_ALARM_LABEL", alarm.label)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }
}