package com.necromagik.pureclock.alarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.R

class TimerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "pure_clock_timer_channel"
        const val ALARM_CHANNEL_ID = "pure_clock_timer_alarm_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.necromagik.pureclock.TIMER_START"
        const val ACTION_STOP = "com.necromagik.pureclock.TIMER_STOP"
        const val ACTION_TRIGGER_ALARM = "com.necromagik.pureclock.TIMER_TRIGGER_ALARM"
        const val EXTRA_LABEL = "extra_timer_label"

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Таймер"
        val durationText = intent?.getStringExtra("EXTRA_DURATION").orEmpty()
        val displayTitle = if (durationText.isNotEmpty()) "$label ($durationText)" else label

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildRunningNotification("Таймер запущен"))
            }
            ACTION_TRIGGER_ALARM -> {
                val activeAlarmNotification = buildActiveTimerNotification(displayTitle)
                startForeground(NOTIFICATION_ID, activeAlarmNotification)

                // КЛЮЧЕВОЙ ФИКС ДЛЯ VIVO/ORIGINOS: зажигаем дисплей аппаратно через WakeLock
                wakeUpScreenNow()

                val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                    putExtra("ALARM_LABEL", displayTitle)
                    putExtra("IS_TIMER", true)
                }
                startActivity(alertIntent)
            }
            ACTION_STOP -> {
                stopForegroundAndNotification()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun wakeUpScreenNow() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val screenLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "PureClock::TimerScreenWakeUp"
        )
        screenLock.acquire(3000L) // Включаем экран на 3 секунды для гарантированной отрисовки Activity
    }

    private fun buildActiveTimerNotification(displayTitle: String): Notification {
        val fullScreenIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("ALARM_LABEL", displayTitle)
            putExtra("IS_TIMER", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2002,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2003,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Время вышло!")
            .setContentText(displayTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Сбросить",
                dismissPendingIntent
            )
            .build()
    }

    private fun buildRunningNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PureClock Таймер")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun stopForegroundAndNotification() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PureClock::TimerWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Фоновый таймер",
                NotificationManager.IMPORTANCE_LOW
            )

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Сигнал таймера",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Полноэкранный сигнал завершения таймера"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundAndNotification()
        super.onDestroy()
    }
}