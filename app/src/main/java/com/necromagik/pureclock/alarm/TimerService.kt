package com.necromagik.pureclock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.viewmodel.TimerItem
import com.necromagik.pureclock.ui.viewmodel.TimerState
import com.necromagik.pureclock.util.ClockNotificationManager
import com.necromagik.pureclock.util.OriginIslandHelper.attachOriginIslandFlags
import com.necromagik.pureclock.util.OriginIslandHelper.promoteToOriginIsland
import com.necromagik.pureclock.util.ScenaryExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var liveTickerJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = ClockNotificationManager.NOTIFICATION_ID_TIMER
        const val ALARM_NOTIFICATION_ID = 2003
        private const val ALARM_CHANNEL_ID = "pureclock_timer_alarm_silent_channel"

        const val ACTION_TRIGGER_ALARM = "com.necromagik.pureclock.ACTION_TRIGGER_TIMER_ALARM"
        const val ACTION_DISMISS_TIMER_ALARM = "com.necromagik.pureclock.ACTION_DISMISS_TIMER_ALARM"
        const val ACTION_EXTEND_TIMER = "com.necromagik.pureclock.ACTION_EXTEND_TIMER"
        const val ACTION_PAUSE_TIMER = "com.necromagik.pureclock.ACTION_PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.necromagik.pureclock.ACTION_RESUME_TIMER"
        const val ACTION_CANCEL_TIMER = "com.necromagik.pureclock.ACTION_CANCEL_TIMER"

        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_LABEL = "extra_timer_label"
        const val EXTRA_DURATION_SECONDS = "extra_timer_duration_seconds"
        const val EXTRA_TIMER_DURATION_TEXT = "extra_timer_duration_text"
        const val EXTRA_ADD_MINUTES = "extra_add_minutes"

        @Volatile
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

        fun triggerAlarm(
            context: Context,
            timerId: String?,
            label: String,
            durationSeconds: Long = 0L,
            durationText: String = ""
        ) {
            if (isRinging) return
            isRinging = true

            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_TRIGGER_ALARM
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_TIMER_DURATION_TEXT, durationText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismissRingingTimer(context: Context, timerId: String? = null) {
            isRinging = false

            val prefs = context.getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("saved_timers_list", null)
            var hasRunningTimers = false
            val dismissedTimerIds = mutableListOf<String>()

            if (!json.isNullOrEmpty()) {
                try {
                    val gson = Gson()
                    val type = object : TypeToken<MutableList<TimerItem>>() {}.type
                    val list: MutableList<TimerItem> = gson.fromJson(json, type) ?: mutableListOf()
                    var changed = false
                    for (i in list.indices) {
                        val item = list[i]
                        if ((timerId != null && item.id == timerId) || item.state == TimerState.COMPLETED) {
                            TimerReceiver.cancelTimerAlarm(context, item.id)
                            dismissedTimerIds.add(item.id)
                            list[i] = item.copy(
                                state = TimerState.IDLE,
                                remainingSeconds = item.initialTimeSeconds,
                                remainingMillis = item.initialTimeSeconds * 1000L
                            )
                            changed = true
                        } else if (item.state == TimerState.RUNNING) {
                            hasRunningTimers = true
                        }
                    }
                    if (changed) {
                        prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()
                    }
                } catch (_: Exception) {}
            }

            if (timerId != null && !dismissedTimerIds.contains(timerId)) {
                dismissedTimerIds.add(timerId)
            }

            dismissedTimerIds.forEach { id ->
                ScenaryExecutor.executeForTimer(context, id)
            }

            context.sendBroadcast(Intent(AlarmAlertActivity.ACTION_CLOSE_ALERT).setPackage(context.packageName))

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ALARM_NOTIFICATION_ID)

            if (hasRunningTimers) {
                val intent = Intent(context, TimerService::class.java).apply {
                    action = ACTION_DISMISS_TIMER_ALARM
                    putExtra(EXTRA_TIMER_ID, timerId)
                }
                context.startService(intent)
            } else {
                stopService(context)
            }
        }

        fun extendTimer(context: Context, timerId: String?, label: String, minutesToAdd: Int) {
            isRinging = false
            context.sendBroadcast(Intent(AlarmAlertActivity.ACTION_CLOSE_ALERT).setPackage(context.packageName))

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
            context.sendBroadcast(Intent(AlarmAlertActivity.ACTION_CLOSE_ALERT).setPackage(context.packageName))
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ALARM_NOTIFICATION_ID)
            ClockNotificationManager.cancel(context, NOTIFICATION_ID)
            val intent = Intent(context, TimerService::class.java)
            context.stopService(intent)
        }
    }

    private fun startTimerForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun ensureLiveNotificationTicker() {
        if (liveTickerJob?.isActive == true) return
        liveTickerJob = serviceScope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive) {
                if (!isRinging) {
                    val active = findActiveOrPausedTimer()
                    if (active == null) {
                        stopSelf()
                        break
                    }
                    nm.notify(NOTIFICATION_ID, buildLiveTimerNotification())
                    if (active.state != TimerState.RUNNING) {
                        break
                    }
                }
                delay(1000L)
            }
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
                liveTickerJob?.cancel()
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
                val durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L)
                val durationText = intent.getStringExtra(EXTRA_TIMER_DURATION_TEXT).orEmpty()
                triggerTimerFullScreenAlarm(timerId, label, durationSeconds, durationText)
            }
            ACTION_DISMISS_TIMER_ALARM -> {
                stopMediaAndVibration()
                isRinging = false
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                dismissRingingTimer(this, timerId)
                return START_NOT_STICKY
            }
            ACTION_EXTEND_TIMER -> {
                stopMediaAndVibration()
                isRinging = false
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Таймер"
                val minutesToAdd = intent.getIntExtra(EXTRA_ADD_MINUTES, 1)
                handleExtendTimer(timerId, label, minutesToAdd)
                ensureLiveNotificationTicker()
            }
            ACTION_PAUSE_TIMER -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                handlePauseTimer(timerId)
                liveTickerJob?.cancel()
            }
            ACTION_RESUME_TIMER -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                handleResumeTimer(timerId)
                ensureLiveNotificationTicker()
            }
            ACTION_CANCEL_TIMER -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                handleCancelTimer(timerId)
                return START_NOT_STICKY
            }
            else -> {
                if (!isRinging) {
                    startTimerForeground(NOTIFICATION_ID, buildLiveTimerNotification())
                    ensureLiveNotificationTicker()
                }
            }
        }
        return START_STICKY
    }

    private fun handleCancelTimer(timerId: String?) {
        stopMediaAndVibration()
        isRinging = false
        val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("saved_timers_list", null)
        var hasOtherActive = false

        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<TimerItem>>() {}.type
                val list: MutableList<TimerItem> = gson.fromJson(json, type) ?: mutableListOf()
                for (i in list.indices) {
                    val item = list[i]
                    if ((timerId != null && item.id == timerId) || (timerId == null && item.state != TimerState.IDLE)) {
                        TimerReceiver.cancelTimerAlarm(this, item.id)
                        list[i] = item.copy(
                            state = TimerState.IDLE,
                            remainingSeconds = item.initialTimeSeconds,
                            remainingMillis = item.initialTimeSeconds * 1000L
                        )
                    } else if (item.state == TimerState.RUNNING || item.state == TimerState.PAUSED) {
                        hasOtherActive = true
                    }
                }
                prefs.edit().putString("saved_timers_list", gson.toJson(list)).apply()
            } catch (_: Exception) {}
        }

        if (hasOtherActive) {
            startTimerForeground(NOTIFICATION_ID, buildLiveTimerNotification())
            ensureLiveNotificationTicker()
        } else {
            liveTickerJob?.cancel()
            stopService(this)
        }
    }

    private fun playTimerSoundAndVibration() {
        stopMediaAndVibration()
        val settings = SettingsManager.getInstance(this)

        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (settings.isTimerVibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 300, 500, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopMediaAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
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
            startTimerForeground(NOTIFICATION_ID, buildLiveTimerNotification())
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
            startTimerForeground(NOTIFICATION_ID, buildLiveTimerNotification())
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

        val index = if (!timerId.isNullOrEmpty()) list.indexOfFirst { it.id == timerId } else list.indexOfFirst { it.state == TimerState.RUNNING || it.state == TimerState.COMPLETED }
        val finalId: String
        val triggerTime: Long

        if (index != -1) {
            val item = list[index]
            val currentBase = if (item.state == TimerState.RUNNING) item.endTimestampMillis else System.currentTimeMillis()
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
        startTimerForeground(NOTIFICATION_ID, buildLiveTimerNotification())
    }

    private fun findActiveOrPausedTimer(): TimerItem? {
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

        return list.firstOrNull { it.state == TimerState.RUNNING }
            ?: list.firstOrNull { it.state == TimerState.PAUSED }
    }

    private fun buildLiveTimerNotification(): Notification {
        val activeTimer = findActiveOrPausedTimer()
        val now = System.currentTimeMillis()
        val isRunning = activeTimer?.state == TimerState.RUNNING
        val remaining = if (isRunning && activeTimer != null && activeTimer.endTimestampMillis > now) {
            activeTimer.endTimestampMillis - now
        } else {
            activeTimer?.remainingMillis ?: 0L
        }

        val label = activeTimer?.label ?: "Таймер"
        val activeId = activeTimer?.id ?: ""
        val initialSeconds = activeTimer?.initialTimeSeconds ?: 0L

        return ClockNotificationManager.buildTimerNotification(
            context = this,
            timerId = activeId,
            remainingMillis = remaining,
            label = label,
            isRunning = isRunning,
            initialTimeSeconds = initialSeconds
        )
    }

    private fun triggerTimerFullScreenAlarm(
        timerId: String?,
        label: String,
        durationSeconds: Long,
        durationText: String
    ) {
        playTimerSoundAndVibration()

        var resolvedSeconds = durationSeconds
        if (resolvedSeconds <= 0L) {
            try {
                val prefs = getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
                val json = prefs.getString("saved_timers_list", null)
                if (!json.isNullOrEmpty()) {
                    val type = object : TypeToken<List<TimerItem>>() {}.type
                    val list: List<TimerItem> = Gson().fromJson(json, type) ?: emptyList()
                    val matched = list.firstOrNull { it.id == timerId } ?: list.firstOrNull { it.state == TimerState.COMPLETED }
                    if (matched != null) {
                        resolvedSeconds = matched.initialTimeSeconds
                    }
                }
            } catch (_: Exception) {}
        }

        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("IS_TIMER", true)
            putExtra("EXTRA_TIMER_ID", timerId)
            putExtra("ALARM_LABEL", label)
            putExtra("EXTRA_TIMER_DURATION_SECONDS", resolvedSeconds)
            putExtra("EXTRA_TIMER_DURATION_TEXT", durationText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2004,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_DISMISS_TIMER_ALARM
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2005,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("00:00")
            .setContentText("Таймер завершен • $label")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", dismissPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .promoteToOriginIsland(
                chipText = "00:00",
                //targetTimeMillis = null
            )
            .build()
            .attachOriginIslandFlags(chipText = "00:00")

        startTimerForeground(ALARM_NOTIFICATION_ID, notification)
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
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        liveTickerJob?.cancel()
        serviceScope.cancel()
        stopMediaAndVibration()
        isRinging = false
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALARM_NOTIFICATION_ID)
        ClockNotificationManager.cancel(this, NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}