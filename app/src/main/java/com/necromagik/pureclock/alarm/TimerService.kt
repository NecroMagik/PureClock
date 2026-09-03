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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.necromagik.pureclock.R
import com.necromagik.pureclock.ui.viewmodel.TimerItem
import com.necromagik.pureclock.ui.viewmodel.TimerState

class TimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val ALARM_NOTIFICATION_ID = 2003
        private const val CHANNEL_ID = "pureclock_timer_channel"
        private const val ALARM_CHANNEL_ID = "pureclock_timer_alarm_channel"

        const val ACTION_TRIGGER_ALARM = "com.necromagik.pureclock.ACTION_TRIGGER_TIMER_ALARM"
        const val ACTION_EXTEND_TIMER = "com.necromagik.pureclock.ACTION_EXTEND_TIMER"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_LABEL = "extra_timer_label"
        const val EXTRA_DURATION_SECONDS = "extra_timer_duration_seconds"
        const val EXTRA_ADD_MINUTES = "extra_add_minutes"

        var isRinging: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun triggerAlarm(context: Context, timerId: String?, label: String, durationSeconds: Long = 0L) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_TRIGGER_ALARM
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun extendTimer(context: Context, timerId: String?, label: String, minutesToAdd: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_EXTEND_TIMER
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_ADD_MINUTES, minutesToAdd)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            isRinging = false
            val intent = Intent(context, TimerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_ALARM -> {
                isRinging = true
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
                val durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L)
                triggerTimerFullScreenAlarm(timerId, label, durationSeconds)
            }
            ACTION_EXTEND_TIMER -> {
                isRinging = false
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
                val minutesToAdd = intent.getIntExtra(EXTRA_ADD_MINUTES, 1)
                handleExtendTimer(timerId, label, minutesToAdd)
            }
            else -> {
                val notification = buildForegroundNotification()
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    private fun handleExtendTimer(timerId: String?, label: String, minutesToAdd: Int) {
        val extraSec = minutesToAdd * 60L
        val extraMillis = extraSec * 1000L
        val triggerTime = System.currentTimeMillis() + extraMillis

        // 1. Обновляем JSON в SharedPreferences, чтобы карточка ожила в UI и ViewModel подхватила ее
        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("saved_timers_list", null)

        val list = if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<TimerItem>>() {}.type
                gson.fromJson<MutableList<TimerItem>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
        } else mutableListOf()

        val index = if (!timerId.isNullOrEmpty()) list.indexOfFirst { it.id == timerId } else -1
        val finalId = if (index != -1) {
            val item = list[index]
            list[index] = item.copy(
                initialTimeSeconds = extraSec,
                remainingSeconds = extraSec,
                remainingMillis = extraMillis,
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
            item.id
        } else {
            val newId = timerId ?: java.util.UUID.randomUUID().toString()
            list.add(
                TimerItem(
                    id = newId,
                    label = label,
                    initialTimeSeconds = extraSec,
                    remainingSeconds = extraSec,
                    remainingMillis = extraMillis,
                    state = TimerState.RUNNING,
                    endTimestampMillis = triggerTime
                )
            )
            newId
        }

        prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()

        // 2. Планируем системный AlarmClock
        TimerReceiver.scheduleTimerAlarm(
            this,
            finalId,
            label,
            "$minutesToAdd мин",
            triggerTime
        )

        // 3. Переводим службу в фоновый статус без звона
        val notification = buildForegroundNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALARM_NOTIFICATION_ID)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun triggerTimerFullScreenAlarm(timerId: String?, label: String, durationSeconds: Long) {
        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("IS_TIMER", true)
            putExtra("EXTRA_TIMER_ID", timerId)
            putExtra("ALARM_LABEL", label)
            putExtra("EXTRA_TIMER_DURATION_SECONDS", durationSeconds)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2004,
        alertIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Таймер завершен!")
        .setContentText(label)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setOngoing(true)
        .setAutoCancel(false)
        .build()

        startForeground(ALARM_NOTIFICATION_ID, notification)

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

    override fun onDestroy() {
        isRinging = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}