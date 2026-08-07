package com.necromagik.pureclock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.necromagik.pureclock.R

class TimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val ALARM_NOTIFICATION_ID = 2003
        private const val CHANNEL_ID = "pureclock_timer_channel"
        private const val ALARM_CHANNEL_ID = "pureclock_timer_alarm_channel"

        const val ACTION_TRIGGER_ALARM = "com.necromagik.pureclock.ACTION_TRIGGER_TIMER_ALARM"
        const val EXTRA_LABEL = "extra_timer_label"

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_ALARM) {
            val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
            triggerTimerFullScreenAlarm(label)
        } else {
            val notification = buildForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun triggerTimerFullScreenAlarm(label: String) {
        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("IS_TIMER", true)
            putExtra("ALARM_LABEL", label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2004,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Таймер завершен!")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALARM_NOTIFICATION_ID, builder.build())

        try {
            startActivity(alertIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PureClock")
            .setContentText("Таймер работает в фоне")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Фоновая служба таймера",
                NotificationManager.IMPORTANCE_LOW
            )

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Сигнал таймера",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}