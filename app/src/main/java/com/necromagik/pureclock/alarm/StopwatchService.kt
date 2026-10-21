package com.necromagik.pureclock.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.necromagik.pureclock.util.ClockNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class StopwatchExternalCommand {
    START_OR_RESUME,
    PAUSE,
    LAP,
    RESET
}

class StopwatchService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var baseStartEpochMillis: Long = 0L
    private var pausedElapsedMillis: Long = 0L
    private var currentLaps: Int = 0
    private var isCurrentlyRunning: Boolean = false

    companion object {
        const val NOTIFICATION_ID = ClockNotificationManager.NOTIFICATION_ID_STOPWATCH

        const val ACTION_START = "ACTION_START_STOPWATCH_SERVICE"
        const val ACTION_PAUSE = "ACTION_PAUSE_STOPWATCH_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_STOPWATCH_SERVICE"
        const val ACTION_UPDATE_LAPS = "ACTION_UPDATE_LAPS_STOPWATCH_SERVICE"
        const val ACTION_LAP_FROM_NOTIFICATION = "ACTION_LAP_FROM_NOTIFICATION"

        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_LAP_COUNT = "extra_lap_count"
        const val EXTRA_FROM_NOTIFICATION = "extra_from_notification"

        private val _externalCommands = MutableSharedFlow<StopwatchExternalCommand>(extraBufferCapacity = 8)
        val externalCommands: SharedFlow<StopwatchExternalCommand> = _externalCommands.asSharedFlow()

        fun start(context: Context, startTimeMillis: Long, lapCount: Int) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_TIME, startTimeMillis)
                putExtra(EXTRA_LAP_COUNT, lapCount)
                putExtra(EXTRA_FROM_NOTIFICATION, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context, startTimeMillis: Long, lapCount: Int) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_START_TIME, startTimeMillis)
                putExtra(EXTRA_LAP_COUNT, lapCount)
                putExtra(EXTRA_FROM_NOTIFICATION, false)
            }
            context.startService(intent)
        }

        fun updateLaps(context: Context, startTimeMillis: Long, lapCount: Int) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_UPDATE_LAPS
                putExtra(EXTRA_START_TIME, startTimeMillis)
                putExtra(EXTRA_LAP_COUNT, lapCount)
                putExtra(EXTRA_FROM_NOTIFICATION, false)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_FROM_NOTIFICATION, false)
            }
            context.startService(intent)
        }
    }

    private fun startStopwatchForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLiveTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive && isCurrentlyRunning) {
                val elapsed = (System.currentTimeMillis() - baseStartEpochMillis).coerceAtLeast(0L)
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this@StopwatchService,
                    startTimeMillis = elapsed,
                    currentLapCount = currentLaps,
                    isRunning = true
                )
                manager.notify(NOTIFICATION_ID, notification)
                delay(1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ClockNotificationManager.initChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val passedElapsed = intent?.getLongExtra(EXTRA_START_TIME, -1L) ?: -1L
        val lapCount = intent?.getIntExtra(EXTRA_LAP_COUNT, -1) ?: -1
        val fromNotification = intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true

        when (intent?.action) {
            ACTION_START -> {
                if (fromNotification) {
                    _externalCommands.tryEmit(StopwatchExternalCommand.START_OR_RESUME)
                }
                if (lapCount >= 0) currentLaps = lapCount
                val effectiveElapsed = if (passedElapsed >= 0L) passedElapsed else pausedElapsedMillis
                isCurrentlyRunning = true
                baseStartEpochMillis = System.currentTimeMillis() - effectiveElapsed
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = effectiveElapsed,
                    currentLapCount = currentLaps,
                    isRunning = true
                )
                startStopwatchForeground(notification)
                startLiveTicker()
            }
            ACTION_LAP_FROM_NOTIFICATION -> {
                _externalCommands.tryEmit(StopwatchExternalCommand.LAP)
                currentLaps += 1
                val currentElapsed = if (isCurrentlyRunning) {
                    (System.currentTimeMillis() - baseStartEpochMillis).coerceAtLeast(0L)
                } else {
                    pausedElapsedMillis
                }
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = currentElapsed,
                    currentLapCount = currentLaps,
                    isRunning = isCurrentlyRunning
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_LAPS -> {
                if (lapCount >= 0) currentLaps = lapCount
                val currentElapsed = if (isCurrentlyRunning) {
                    (System.currentTimeMillis() - baseStartEpochMillis).coerceAtLeast(0L)
                } else if (passedElapsed >= 0L) {
                    passedElapsed
                } else {
                    pausedElapsedMillis
                }
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = currentElapsed,
                    currentLapCount = currentLaps,
                    isRunning = isCurrentlyRunning
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_PAUSE -> {
                if (fromNotification) {
                    _externalCommands.tryEmit(StopwatchExternalCommand.PAUSE)
                }
                if (isCurrentlyRunning) {
                    pausedElapsedMillis = (System.currentTimeMillis() - baseStartEpochMillis).coerceAtLeast(0L)
                } else if (passedElapsed >= 0L) {
                    pausedElapsedMillis = passedElapsed
                }
                isCurrentlyRunning = false
                tickerJob?.cancel()
                if (lapCount >= 0) currentLaps = lapCount
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = pausedElapsedMillis,
                    currentLapCount = currentLaps,
                    isRunning = false
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                if (fromNotification) {
                    _externalCommands.tryEmit(StopwatchExternalCommand.RESET)
                }
                isCurrentlyRunning = false
                pausedElapsedMillis = 0L
                currentLaps = 0
                tickerJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                ClockNotificationManager.cancel(this, NOTIFICATION_ID)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        ClockNotificationManager.cancel(this, NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}