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
import com.necromagik.pureclock.util.OriginIslandHelper.attachOriginIslandFlags
import java.util.Locale

object ClockNotificationManager {

    private const val CHANNEL_STOPWATCH = "pureclock_stopwatch_channel"
    private const val CHANNEL_TIMER = "pureclock_timer_channel_v2"

    const val NOTIFICATION_ID_STOPWATCH = 10001
    const val NOTIFICATION_ID_TIMER = 10002

    fun initChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stopwatchChannel = NotificationChannel(
            CHANNEL_STOPWATCH,
            "Секундомер",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            "Таймер",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(stopwatchChannel)
        manager.createNotificationChannel(timerChannel)
    }

    private fun formatClockTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatInitialDurationText(initialSeconds: Long): String {
        if (initialSeconds <= 0L) return ""
        val hours = initialSeconds / 3600
        val mins = (initialSeconds % 3600) / 60
        val secs = initialSeconds % 60
        return when {
            hours > 0 && mins > 0 -> "$hours ч $mins мин"
            hours > 0 -> "$hours ч"
            mins > 0 && secs > 0 -> "$mins мин $secs сек"
            mins > 0 -> "$mins мин"
            else -> "$secs сек"
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
            putExtra(StopwatchService.EXTRA_FROM_NOTIFICATION, true)
        }
        val togglePendingIntent = PendingIntent.getService(
            context,
            10,
            toggleActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_STOP
            putExtra(StopwatchService.EXTRA_FROM_NOTIFICATION, true)
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            11,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lapIntent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_LAP_FROM_NOTIFICATION
            putExtra(StopwatchService.EXTRA_FROM_NOTIFICATION, true)
        }
        val lapPendingIntent = PendingIntent.getService(
            context,
            12,
            lapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val totalSec = (startTimeMillis / 1000L).coerceAtLeast(0L)
        val formattedTime = formatClockTime(totalSec)

        // Точно как на референсе: сверху крупно "00:07", снизу "Секундомер"
        val subtitleText = when {
            !isRunning -> "Секундомер • Пауза"
            currentLapCount > 0 -> "Секундомер • Кругов: $currentLapCount"
            else -> "Секундомер"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_STOPWATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(formattedTime)
            .setContentText(subtitleText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            builder.addAction(android.R.drawable.ic_menu_mylocation, "Круг", lapPendingIntent)
            builder.addAction(android.R.drawable.ic_media_pause, "Пауза", togglePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Сброс", stopPendingIntent)
            builder.addAction(android.R.drawable.ic_media_play, "Старт", togglePendingIntent)
        }

        return builder.build().attachOriginIslandFlags(
            chipText = formattedTime
        )
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
        isRunning: Boolean,
        initialTimeSeconds: Long = 0L
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

        // 1. Кнопка Закрыть / Удалить
        val cancelIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_CANCEL_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            22,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Кнопка +1 мин
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

        // 3. Кнопка Пауза / Старт
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

        val totalSec = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
        val formattedCountdown = formatClockTime(totalSec)

        // Формируем нижнюю подпись как на референсе ("15 мин" или "Название • 15 мин")
        val durationLabel = formatInitialDurationText(initialTimeSeconds)
        val cleanLabel = label.trim()
        val isDefaultTimerName = cleanLabel.isEmpty() ||
                cleanLabel.equals("Таймер", ignoreCase = true) ||
                cleanLabel.matches(Regex("""^Таймер\s*\d*$""", RegexOption.IGNORE_CASE))

        val baseSubtitle = when {
            !isDefaultTimerName && durationLabel.isNotEmpty() -> "$cleanLabel • $durationLabel"
            durationLabel.isNotEmpty() -> durationLabel
            cleanLabel.isNotEmpty() -> cleanLabel
            else -> "Таймер"
        }
        val finalSubtitle = if (isRunning) baseSubtitle else "$baseSubtitle • Пауза"

        // Крупно сверху — сам отсчёт ("14:53"), снизу — подпись ("15 мин"), без дублирующего хронометра справа!
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(formattedCountdown)
            .setContentText(finalSubtitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)

        // Все 3 кнопки у таймера: 1) Закрыть, 2) +1 мин, 3) Пауза/Старт
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", cancelPendingIntent)
        builder.addAction(R.drawable.ic_notification, "+1 мин", addMinutePendingIntent)

        if (isRunning) {
            builder.addAction(android.R.drawable.ic_media_pause, "Пауза", togglePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Старт", togglePendingIntent)
        }

        return builder.build().attachOriginIslandFlags(
            chipText = formattedCountdown
        )
    }

    fun showTimerNotification(
        context: Context,
        timerId: String,
        remainingMillis: Long,
        label: String,
        isRunning: Boolean,
        initialTimeSeconds: Long = 0L
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID_TIMER,
            buildTimerNotification(context, timerId, remainingMillis, label, isRunning, initialTimeSeconds)
        )
    }

    fun cancel(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }
}