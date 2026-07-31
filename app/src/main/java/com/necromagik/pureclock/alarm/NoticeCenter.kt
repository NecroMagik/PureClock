package com.necromagik.pureclock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.AlarmEntity
import java.util.Locale

class NoticeCenter(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val upcomingChannel = NotificationChannel(
            UPCOMING_CHANNEL_ID,
            "Предстоящие будильники",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления о скором срабатывании сигнала"
        }

        val activeChannel = NotificationChannel(
            ACTIVE_CHANNEL_ID,
            "Звонящий будильник",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setBypassDnd(true)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(upcomingChannel)
        notificationManager.createNotificationChannel(activeChannel)
    }

    // ============================================================================
    // РАСШИРЕННАЯ КАРТОЧКА ПРЕДВАРИТЕЛЬНОГО УВЕДОМЛЕНИЯ (UPCOMING NOTICE)
    // ============================================================================
    fun showUpcomingAlarmNotice(alarm: AlarmEntity) {
        val timeText = String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute)
        val title = alarm.label.ifEmpty { "Скоро сработает будильник" }

        val skipIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SKIP_UPCOMING
            putExtra("EXTRA_ALARM_ID", alarm.id)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id + 10000).toInt(),
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val expandedMessage = if (alarm.label.isNotEmpty()) {
            "Метка: «${alarm.label}»\nВремя сигнала: $timeText\nВы можете пропустить этот сигнал заранее."
        } else {
            "Запланированный сигнал на $timeText.\nНажмите «Пропустить», если встали раньше."
        }

        val notification = NotificationCompat.Builder(context, UPCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Предстоящий сигнал в $timeText")
            .setSubText("Будильник PureClock")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(mainPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("⏰ $title ($timeText)")
                    .bigText(expandedMessage)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Пропустить 1 раз",
                skipPendingIntent
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify((alarm.id + UPCOMING_OFFSET).toInt(), notification)
    }

    fun dismissUpcomingNotice(alarmId: Long) {
        notificationManager.cancel((alarmId + UPCOMING_OFFSET).toInt())
    }

    fun buildActiveAlarmNotification(alarmId: Long, label: String, timeText: String): Notification {
        val fullScreenIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmService::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra("EXTRA_ALARM_ID", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            (alarmId + 20000).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmService::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra("EXTRA_ALARM_ID", alarmId)
        }
        val snoozePendingIntent = PendingIntent.getService(
            context,
            (alarmId + 30000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifEmpty { "Будильник звонит" })
            .setContentText("Сигнал на $timeText")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Время срабатывания: $timeText\nВыберите действие ниже или нажмите для открытия управления.")
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Сбросить",
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Отложить",
                snoozePendingIntent
            )
            .build()
    }

    companion object {
        const val UPCOMING_CHANNEL_ID = "pureclock_upcoming_channel"
        const val ACTIVE_CHANNEL_ID = "pure_clock_alarm_channel"

        const val UPCOMING_OFFSET = 50000

        const val ACTION_SKIP_UPCOMING = "com.necromagik.pureclock.ACTION_SKIP_UPCOMING"
        const val ACTION_DISMISS_ALARM = "com.necromagik.pureclock.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.necromagik.pureclock.ACTION_SNOOZE_ALARM"
    }
}