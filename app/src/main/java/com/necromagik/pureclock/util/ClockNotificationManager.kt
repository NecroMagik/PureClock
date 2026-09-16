package com.necromagik.pureclock.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.R
import com.necromagik.pureclock.alarm.StopwatchService
import com.necromagik.pureclock.alarm.TimerService

object ClockNotificationManager {

    private const val CHANNEL_STOPWATCH = "pureclock_stopwatch_channel"
    private const val CHANNEL_TIMER = "pureclock_timer_channel_v2"

    const val NOTIFICATION_ID_STOPWATCH = 10001
    const val NOTIFICATION_ID_TIMER = 10002

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val stopwatchChannel = NotificationChannel(
                CHANNEL_STOPWATCH,
                "Секундомер",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val timerChannel = NotificationChannel(
                CHANNEL_TIMER,
                "Таймер",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            manager.createNotificationChannel(stopwatchChannel)
            manager.createNotificationChannel(timerChannel)
        }
    }

    fun buildStopwatchNotification(
        context: Context,
        startTimeMillis: Long,
        currentLapCount: Int,
        isRunning: Boolean
    ): Notification {
        initChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleActionIntent = Intent(context, StopwatchService::class.java).apply {
            action = if (isRunning) StopwatchService.ACTION_PAUSE else StopwatchService.ACTION_START
            putExtra(StopwatchService.EXTRA_START_TIME, startTimeMillis)
            putExtra(StopwatchService.EXTRA_LAP_COUNT, currentLapCount)
        }
        val togglePendingIntent = PendingIntent.getService(
            context,
            10,
            toggleActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bodyText = if (isRunning) {
            if (currentLapCount > 0) "Кругов: $currentLapCount" else "Идёт отсчёт"
        } else {
            val sec = startTimeMillis / 1000
            "Пауза • ${String.format("%02d:%02d", sec / 60, sec % 60)}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_STOPWATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Секундомер")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isRunning)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(false)
            builder.setWhen(System.currentTimeMillis() - startTimeMillis)
            builder.addAction(android.R.drawable.ic_media_pause, "Пауза", togglePendingIntent)
        } else {
            builder.setUsesChronometer(false)
            builder.addAction(android.R.drawable.ic_media_play, "Продолжить", togglePendingIntent)
        }

        return builder.build()
    }

    fun showStopwatchNotification(
        context: Context,
        startTimeMillis: Long,
        currentLapCount: Int,
        isRunning: Boolean
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID_STOPWATCH,
            buildStopwatchNotification(context, startTimeMillis, currentLapCount, isRunning)
        )
    }

    fun buildTimerNotification(
        context: Context,
        timerId: String,
        remainingMillis: Long,
        label: String,
        isRunning: Boolean
    ): Notification {
        initChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(context, TimerService::class.java).apply {
            action = if (isRunning) TimerService.ACTION_PAUSE_TIMER else TimerService.ACTION_RESUME_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        val togglePendingIntent = PendingIntent.getService(
            context,
            21,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val addMinuteIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_EXTEND_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
            putExtra(TimerService.EXTRA_LABEL, label)
            putExtra(TimerService.EXTRA_ADD_MINUTES, 1)
        }
        val addMinutePendingIntent = PendingIntent.getService(
            context,
            20,
            addMinuteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val totalSec = (remainingMillis + 999L) / 1000L
        val formattedTime = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label.ifBlank { "Таймер" })
            .setContentText(if (isRunning) "Осталось времени" else "Пауза • $formattedTime")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isRunning)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setWhen(System.currentTimeMillis() + remainingMillis)
            builder.addAction(android.R.drawable.ic_media_pause, "Пауза", togglePendingIntent)
            builder.addAction(R.drawable.ic_notification, "+1 мин", addMinutePendingIntent)
        } else {
            builder.setUsesChronometer(false)
            builder.addAction(android.R.drawable.ic_media_play, "Старт", togglePendingIntent)
            builder.addAction(R.drawable.ic_notification, "+1 мин", addMinutePendingIntent)
        }

        return builder.build()
    }

    fun showTimerNotification(
        context: Context,
        timerId: String,
        remainingMillis: Long,
        label: String,
        isRunning: Boolean
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID_TIMER,
            buildTimerNotification(context, timerId, remainingMillis, label, isRunning)
        )
    }

    fun cancel(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }
}