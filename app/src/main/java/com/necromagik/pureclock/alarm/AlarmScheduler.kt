package com.necromagik.pureclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val nowMillis = System.currentTimeMillis()

        if (alarm.skippedDateMillis != null && alarm.skippedDateMillis <= nowMillis) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.alarmDao().updateAlarm(alarm.copy(skippedDateMillis = null))
                } catch (_: Exception) {}
            }
        }

        val settingsManager = SettingsManager.getInstance(context)
        val triggerTime = calculateTriggerTime(alarm)
        val upcomingMinutes = settingsManager.upcomingNotificationMinutes

        val upcomingIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "ACTION_SHOW_UPCOMING_NOTICE"
            putExtra("EXTRA_ALARM_ID", alarm.id)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val pendingUpcoming = PendingIntent.getBroadcast(
            context,
            (alarm.id + 40000).toInt(),
            upcomingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Синхронизация с настройкой предварительного уведомления
        if (upcomingMinutes > 0) {
            val upcomingTime = triggerTime - (upcomingMinutes * 60 * 1000L)
            if (upcomingTime > nowMillis) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    upcomingTime,
                    pendingUpcoming
                )
            } else {
                alarmManager.cancel(pendingUpcoming)
            }
        } else {
            alarmManager.cancel(pendingUpcoming)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.necromagik.pureclock.ACTION_FIRE_ALARM"
            putExtra("EXTRA_ALARM_ID", alarm.id)
            putExtra("EXTRA_IS_VIBRATE", alarm.isVibrate)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            return
        }

        val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)
    }

    fun cancel(alarm: AlarmEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.necromagik.pureclock.ACTION_FIRE_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val upcomingIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "ACTION_SHOW_UPCOMING_NOTICE"
        }
        val pendingUpcoming = PendingIntent.getBroadcast(
            context,
            (alarm.id + 40000).toInt(),
            upcomingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingUpcoming)
    }

    fun calculateTriggerTime(alarm: AlarmEntity): Long {
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val extraDates = alarm.parseExtraDates()
        val excludedDates = alarm.parseExcludedDates()
        val activeSkipDate = if (alarm.isSkipActive(nowMillis)) alarm.getSkippedLocalDate() else null

        val candidates = mutableListOf<Long>()

        for (date in extraDates) {
            if (activeSkipDate != null && date == activeSkipDate) continue

            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > nowMillis) {
                candidates.add(cal.timeInMillis)
            }
        }

        if (alarm.daysOfWeek != 0) {
            var checkDate = today
            for (i in 0..365) {
                val dayBit = 1 shl (checkDate.dayOfWeek.value - 1)
                val isDayActive = (alarm.daysOfWeek and dayBit) != 0

                if (isDayActive && !excludedDates.contains(checkDate)) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, checkDate.year)
                        set(Calendar.MONTH, checkDate.monthValue - 1)
                        set(Calendar.DAY_OF_MONTH, checkDate.dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, alarm.hour)
                        set(Calendar.MINUTE, alarm.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (cal.timeInMillis > nowMillis) {
                        if (activeSkipDate != null && checkDate == activeSkipDate) {
                            checkDate = checkDate.plusDays(1)
                            continue
                        } else {
                            candidates.add(cal.timeInMillis)
                            break
                        }
                    }
                }
                checkDate = checkDate.plusDays(1)
            }
        }

        if (candidates.isEmpty()) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        return candidates.minOrNull() ?: (nowMillis + 60_000L)
    }

    fun snooze(alarm: AlarmEntity, snoozeMinutes: Int) {
        val snoozeTriggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.necromagik.pureclock.ACTION_FIRE_ALARM"
            putExtra("EXTRA_ALARM_ID", alarm.id)
            putExtra("EXTRA_IS_VIBRATE", alarm.isVibrate)
            putExtra("IS_SNOOZE_EVENT", true)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id + 99999).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(snoozeTriggerTime, snoozePendingIntent)
        alarmManager.setAlarmClock(clockInfo, snoozePendingIntent)
    }

    companion object {
        fun rescheduleAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = AlarmScheduler(context)
                    val enabled = db.alarmDao().getEnabledAlarmsSync()
                    enabled.forEach { scheduler.schedule(it) }
                } catch (_: Exception) {}
            }
        }
    }
}