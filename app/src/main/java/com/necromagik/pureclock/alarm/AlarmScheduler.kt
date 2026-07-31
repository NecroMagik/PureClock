package com.necromagik.pureclock.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.util.PermissionHelper
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    // ============================================================================
// СЕКЦИЯ 1: РЕГИСТРАЦИЯ И ОТМЕНА БУДИЛЬНИКОВ В СИСТЕМЕ (ALARM MANAGER)
// ============================================================================
    fun schedule(alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val settingsManager = SettingsManager.getInstance(context)
        val noticeCenter = NoticeCenter(context)

        val triggerTime = calculateTriggerTime(alarm)
        val upcomingMinutes = settingsManager.upcomingNotificationMinutes

        if (upcomingMinutes > 0) {
            val upcomingTime = triggerTime - (upcomingMinutes * 60 * 1000L)

            // Если время предварительного уведомления ещё не прошло — ставим AlarmManager на отправку Notice
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

        val triggerAtMillis = calculateTriggerTime(alarm)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Проверка прав на точные будильники (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Если нет разрешения точных будильников — используем точный резервный вызов без AlarmClockInfo
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            return
        }

        // Полноценный запуск через AlarmClockInfo (отображает иконку будильника в статус-баре)
        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
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

    // ============================================================================
// СЕКЦИЯ 2: РАСЧЕТ ТОЧНОГО ВРЕМЕНИ СРАБАТЫВАНИЯ (БИТОВЫЕ МАСКИ И ДАТЫ)
// ============================================================================
    fun calculateTriggerTime(alarm: AlarmEntity): Long {
        val nowMillis = System.currentTimeMillis()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 1. Если задана точечная дата из календаря OxygenOS
        if (alarm.specificDateMillis != null) {
            val dateCal = Calendar.getInstance().apply { timeInMillis = alarm.specificDateMillis }
            calendar.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
            calendar.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))

            // Фикс: Если выбранная дата со временем УЖЕ прошли, переносим на завтра
            if (calendar.timeInMillis <= nowMillis) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        // 2. Разовый будильник (без повторов по дням)
        if (alarm.daysOfWeek == 0) {
            if (calendar.timeInMillis <= nowMillis) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        // 3. Повторяющийся будильник по дням недели
        // Первичный сдвиг, если время сегодня уже прошло
        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        while (true) {
            val dayBit = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 8
                Calendar.FRIDAY -> 16
                Calendar.SATURDAY -> 32
                Calendar.SUNDAY -> 64
                else -> 0
            }

            if ((alarm.daysOfWeek and dayBit) != 0) {
                // Проверка умного пропуска 1 сигнала
                if (alarm.skippedDateMillis != null) {
                    val skipCal = Calendar.getInstance().apply { timeInMillis = alarm.skippedDateMillis }
                    val isSameDay = calendar.get(Calendar.YEAR) == skipCal.get(Calendar.YEAR) &&
                            calendar.get(Calendar.DAY_OF_YEAR) == skipCal.get(Calendar.DAY_OF_YEAR)
                    if (isSameDay) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        continue
                    }
                }
                break
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    // ============================================================================
// СЕКЦИЯ 3: ОБРАБОТКА ОТСРОЧКИ (SNOOZE ENGINE)
// ============================================================================
    fun snooze(alarm: AlarmEntity, snoozeMinutes: Int) {
        val snoozeTriggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarm.id)
            putExtra("IS_SNOOZE_EVENT", true)
        }

        // Уникальный PendingIntent ID для снуза, чтобы не затереть основной таймер
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id + 99999).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                // Запросить разрешение через Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        } else {
            val clockInfo = AlarmManager.AlarmClockInfo(snoozeTriggerTime, snoozePendingIntent)
            alarmManager.setAlarmClock(clockInfo, snoozePendingIntent)
        }
    }
}