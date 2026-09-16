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
import com.necromagik.pureclock.util.ClockNotificationManager

class TimerService : Service() {

    companion object {
        const val NOTIFICATION_ID = ClockNotificationManager.NOTIFICATION_ID_TIMER
        private const val ALARM_NOTIFICATION_ID = 2003
        private const val ALARM_CHANNEL_ID = "pureclock_timer_alarm_channel"

        const val ACTION_TRIGGER_ALARM = "com.necromagik.pureclock.ACTION_TRIGGER_TIMER_ALARM"
        const val ACTION_EXTEND_TIMER = "com.necromagik.pureclock.ACTION_EXTEND_TIMER"
        const val ACTION_PAUSE_TIMER = "com.necromagik.pureclock.ACTION_PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.necromagik.pureclock.ACTION_RESUME_TIMER"
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
        createAlarmNotificationChannel()
        ClockNotificationManager.initChannels(this)
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
            ACTION_PAUSE_TIMER -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                handlePauseTimer(timerId)
            }
            ACTION_RESUME_TIMER -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                handleResumeTimer(timerId)
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildLiveTimerNotification())
            }
        }
        return START_STICKY
    }

    private fun handlePauseTimer(timerId: String?) {
        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("saved_timers_list", null) ?: return
        val type = object : TypeToken<MutableList<TimerItem>>() {}.type
        val list: MutableList<TimerItem> = gson.fromJson(json, type) ?: return
        val index = list.indexOfFirst { it.id == timerId || (timerId == null && it.state == TimerState.RUNNING) }
        if (index != -1) {
            val item = list[index]
            val exactRemaining = (item.endTimestampMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            list[index] = item.copy(
                state = TimerState.PAUSED,
                remainingMillis = exactRemaining,
                remainingSeconds = (exactRemaining + 999L) / 1000L
            )
            prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()
            TimerReceiver.cancelTimerAlarm(this, item.id)
            startForeground(NOTIFICATION_ID, buildLiveTimerNotification())
        }
    }

    private fun handleResumeTimer(timerId: String?) {
        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("saved_timers_list", null) ?: return
        val type = object : TypeToken<MutableList<TimerItem>>() {}.type
        val list: MutableList<TimerItem> = gson.fromJson(json, type) ?: return
        val index = list.indexOfFirst { it.id == timerId || (timerId == null && it.state == TimerState.PAUSED) }
        if (index != -1) {
            val item = list[index]
            val triggerTime = System.currentTimeMillis() + item.remainingMillis
            list[index] = item.copy(
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
            prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()
            TimerReceiver.scheduleTimerAlarm(this, item.id, item.label, "${(item.remainingMillis / 60000)} мин", triggerTime)
            startForeground(NOTIFICATION_ID, buildLiveTimerNotification())
        }
    }

    private fun handleExtendTimer(timerId: String?, label: String, minutesToAdd: Int) {
        val extraSec = minutesToAdd * 60L
        val extraMillis = extraSec * 1000L

        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("saved_timers_list", null)
        val type = object : TypeToken<MutableList<TimerItem>>() {}.type
        val list: MutableList<TimerItem> = if (!json.isNullOrEmpty()) gson.fromJson(json, type) ?: mutableListOf() else mutableListOf()

        val index = if (!timerId.isNullOrEmpty()) list.indexOfFirst { it.id == timerId } else list.indexOfFirst { it.state == TimerState.RUNNING }
        val finalId: String
        val triggerTime: Long

        if (index != -1) {
            val item = list[index]
            val currentBase = if (item.state == TimerState.RUNNING) item.endTimestampMillis else System.currentTimeMillis() + item.remainingMillis
            triggerTime = currentBase + extraMillis
            val newRemainingMillis = (triggerTime - System.currentTimeMillis()).coerceAtLeast(0L)
            list[index] = item.copy(
                remainingSeconds = (newRemainingMillis + 999L) / 1000L,
                remainingMillis = newRemainingMillis,
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
            finalId = item.id
        } else {
            finalId = timerId ?: java.util.UUID.randomUUID().toString()
            triggerTime = System.currentTimeMillis() + extraMillis
            list.add(
                TimerItem(
                    id = finalId,
                    label = label,
                    initialTimeSeconds = extraSec,
                    remainingSeconds = extraSec,
                    remainingMillis = extraMillis,
                    state = TimerState.RUNNING,
                    endTimestampMillis = triggerTime
                )
            )
        }

        prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()
        TimerReceiver.scheduleTimerAlarm(this, finalId, label, "$minutesToAdd мин", triggerTime)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALARM_NOTIFICATION_ID)
        startForeground(NOTIFICATION_ID, buildLiveTimerNotification())
    }

    private fun buildLiveTimerNotification(): Notification {
        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("saved_timers_list", null)
        val list = if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<TimerItem>>() {}.type
                Gson().fromJson<List<TimerItem>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        val activeTimer = list.firstOrNull { it.state == TimerState.RUNNING }
            ?: list.firstOrNull { it.state == TimerState.PAUSED }

        val remaining = activeTimer?.remainingMillis ?: 0L
        val label = activeTimer?.label ?: "Таймер"
        val activeId = activeTimer?.id ?: ""
        val isRunning = activeTimer?.state == TimerState.RUNNING

        return ClockNotificationManager.buildTimerNotification(
            context = this,
            timerId = activeId,
            remainingMillis = remaining,
            label = label,
            isRunning = isRunning
        )
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
            .setSmallIcon(R.drawable.ic_notification)
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

    private fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Сигнал таймера",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        isRinging = false
        ClockNotificationManager.cancel(this, NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}