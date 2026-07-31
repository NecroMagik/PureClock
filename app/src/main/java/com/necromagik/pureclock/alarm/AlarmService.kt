package com.necromagik.pureclock.alarm

import android.app.KeyguardManager
import android.app.Service
//import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.net.toUri
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra("EXTRA_ALARM_ID", -1L) ?: -1L

        // 1. Обработка кнопок из Heads-Up Уведомления
        if (action == NoticeCenter.ACTION_DISMISS_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == NoticeCenter.ACTION_SNOOZE_ALARM) {
            if (alarmId != -1L) {
                val settingsManager = SettingsManager.getInstance(this)
                val snoozeMinutes = settingsManager.defaultSnoozeTimeMinutes
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        AlarmScheduler(applicationContext).snooze(alarm, snoozeMinutes)
                    }
                }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val ringtoneUriStr = intent?.getStringExtra("RINGTONE_URI")
        val label = intent?.getStringExtra("EXTRA_ALARM_LABEL").orEmpty()
        val settingsManager = SettingsManager.getInstance(this)
        val now = LocalTime.now()
        val timeText = String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)

        // 2. Старт единого Foreground Service через NoticeCenter
        val noticeCenter = NoticeCenter(this)
        val activeNotification = noticeCenter.buildActiveAlarmNotification(alarmId, label, timeText)
        startForeground(NOTIFICATION_ID, activeNotification)

        // 3. Воспроизведение звука с использованием .toUri() и ifEmpty
        try {
            val audioUri = ringtoneUriStr?.ifEmpty { null }?.toUri()
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true

                if (settingsManager.isVolumeRampEnabled) {
                    setVolume(0.05f, 0.05f)
                    prepare()
                    start()

                    serviceScope.launch {
                        for (i in 1..20) {
                            delay(1500)
                            val vol = i / 20f
                            setVolume(vol, vol)
                        }
                    }
                } else {
                    setVolume(1.0f, 1.0f)
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Вибрация (без версионирования, API >= 29)
        vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))

        // 5. Разделение логики: Экран Включен vs Выключен
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        val isScreenInteractive = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked

        if (!isScreenInteractive || isLocked) {
            val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("EXTRA_ALARM_ID", alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(alertIntent)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}