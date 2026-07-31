package com.necromagik.pureclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId == -1L) return

        val db = AppDatabase.getDatabase(context)
        val pendingResult = goAsync()

        // 1. Предварительное уведомление
        if (intent.action == "ACTION_SHOW_UPCOMING_NOTICE") {
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
        if (intent.action == NoticeCenter.ACTION_SKIP_UPCOMING) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        val scheduler = AlarmScheduler(context)
                        val triggerTime = scheduler.calculateTriggerTime(alarm)

                        // Записываем пропуск 1 витка
                        val updated = alarm.copy(skippedDateMillis = triggerTime)
                        db.alarmDao().updateAlarm(updated)

                        // Отменяем баннер и пересчитываем расписание
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = db.alarmDao().getAlarmById(alarmId)

                if (alarm != null) {
                    // Гасим предварительное уведомление, если оно висело
                    NoticeCenter(context).dismissUpcomingNotice(alarmId)

                    // Отключаем однократный будильник
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
    }
}