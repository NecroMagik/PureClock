package com.necromagik.pureclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.SettingsManager
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

        val settingsManager = SettingsManager.getInstance(context)
        val triggerTime = calculateTriggerTime(alarm)
        val upcomingMinutes = settingsManager.upcomingNotificationMinutes

        if (upcomingMinutes > 0) {
            val upcomingTime = triggerTime - (upcomingMinutes * 60 * 1000L)
            if (upcomingTime > System.currentTimeMillis()) {
                val upcomingIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "ACTION_SHOW_UPCOMING_NOTICE"
                    putExtra("EXTRA_ALARM_ID", alarm.id)
                }
                val pendingUpcoming = PendingIntent.getBroadcast(
                    context,
                    (alarm.id + 40000).toInt(),
                    upcomingIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    upcomingTime,
                    pendingUpcoming
                )
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarm.id)
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
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun calculateTriggerTime(alarm: AlarmEntity): Long {
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val extraDates = alarm.parseExtraDates()
        val excludedDates = alarm.parseExcludedDates()

        val candidates = mutableListOf<Long>()

        // 1. Проверяем точечные даты календаря
        for (date in extraDates) {
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

        // 2. Проверяем регулярные дни недели с учетом исключений
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
                        // Проверка пропуска 1 раза
                        if (alarm.skippedDateMillis != null) {
                            val skipCal = Calendar.getInstance().apply { timeInMillis = alarm.skippedDateMillis }
                            val isSameDay = cal.get(Calendar.YEAR) == skipCal.get(Calendar.YEAR) &&
                                    cal.get(Calendar.DAY_OF_YEAR) == skipCal.get(Calendar.DAY_OF_YEAR)
                            if (!isSameDay) {
                                candidates.add(cal.timeInMillis)
                                break
                            }
                        } else {
                            candidates.add(cal.timeInMillis)
                            break
                        }
                    }
                }
                checkDate = checkDate.plusDays(1)
            }
        }

        // 3. Если нет ни повторов, ни extra-дат: однократный сигнал сегодня/завтра
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
            putExtra("EXTRA_ALARM_ID", alarm.id)
            putExtra("IS_SNOOZE_EVENT", true)
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
}