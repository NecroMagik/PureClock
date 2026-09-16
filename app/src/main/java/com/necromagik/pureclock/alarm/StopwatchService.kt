package com.necromagik.pureclock.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.necromagik.pureclock.util.ClockNotificationManager

class StopwatchService : Service() {

    companion object {
        const val NOTIFICATION_ID = ClockNotificationManager.NOTIFICATION_ID_STOPWATCH

        const val ACTION_START = "ACTION_START_STOPWATCH_SERVICE"
        const val ACTION_PAUSE = "ACTION_PAUSE_STOPWATCH_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_STOPWATCH_SERVICE"
        const val ACTION_UPDATE_LAPS = "ACTION_UPDATE_LAPS_STOPWATCH_SERVICE"

        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_LAP_COUNT = "extra_lap_count"

        fun start(context: Context, startTimeMillis: Long, lapCount: Int) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_TIME, startTimeMillis)
                putExtra(EXTRA_LAP_COUNT, lapCount)
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
            }
            context.startService(intent)
        }

        fun updateLaps(context: Context, startTimeMillis: Long, lapCount: Int) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_UPDATE_LAPS
                putExtra(EXTRA_START_TIME, startTimeMillis)
                putExtra(EXTRA_LAP_COUNT, lapCount)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ClockNotificationManager.initChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startTime = intent?.getLongExtra(EXTRA_START_TIME, 0L) ?: 0L
        val lapCount = intent?.getIntExtra(EXTRA_LAP_COUNT, 0) ?: 0

        when (intent?.action) {
            ACTION_START -> {
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = startTime,
                    currentLapCount = lapCount,
                    isRunning = true
                )
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_LAPS -> {
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = startTime,
                    currentLapCount = lapCount,
                    isRunning = true
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_PAUSE -> {
                val notification = ClockNotificationManager.buildStopwatchNotification(
                    context = this,
                    startTimeMillis = startTime,
                    currentLapCount = lapCount,
                    isRunning = false
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                ClockNotificationManager.cancel(this, NOTIFICATION_ID)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ClockNotificationManager.cancel(this, NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}