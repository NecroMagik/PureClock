package com.necromagik.pureclock.alarm

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.net.toUri
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.util.OriginIslandHelper.attachOriginIslandFlags
import com.necromagik.pureclock.util.ScenaryExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var volumeRampJob: Job? = null
    private var autoDismissJob: Job? = null

    private val activeAlarmIds = ConcurrentHashMap.newKeySet<Long>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra("EXTRA_ALARM_ID", -1L) ?: -1L
        val settingsManager = SettingsManager.getInstance(this)

        if (action == NoticeCenter.ACTION_DISMISS_ALARM) {
            val idsToDismiss = if (activeAlarmIds.isNotEmpty()) activeAlarmIds.toList() else if (alarmId != -1L) listOf(alarmId) else emptyList()
            idsToDismiss.forEach { id ->
                ScenaryExecutor.executeForAlarm(this, id)
            }
            stopAlarmPlaybackAndExit()
            return START_NOT_STICKY
        }

        if (action == NoticeCenter.ACTION_SNOOZE_ALARM) {
            val snoozeMinutes = settingsManager.defaultSnoozeTimeMinutes
            val idsToSnooze = if (activeAlarmIds.isNotEmpty()) activeAlarmIds.toList() else if (alarmId != -1L) listOf(alarmId) else emptyList()

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val scheduler = AlarmScheduler(applicationContext)
                idsToSnooze.forEach { id ->
                    val alarm = db.alarmDao().getAlarmById(id)
                    if (alarm != null) {
                        scheduler.snooze(alarm, snoozeMinutes)
                    }
                }
            }
            stopAlarmPlaybackAndExit()
            return START_NOT_STICKY
        }

        if (alarmId != -1L) {
            activeAlarmIds.add(alarmId)
        }

        // Прекращаем предыдущий звук и сбрасываем корутины во избежание наложения звуков
        stopMediaAndVibratorOnly()
        volumeRampJob?.cancel()
        autoDismissJob?.cancel()

        val ringtoneUriStr = intent?.getStringExtra("RINGTONE_URI")
        val label = intent?.getStringExtra("EXTRA_ALARM_LABEL").orEmpty()
        val isVibrate = intent?.getBooleanExtra("EXTRA_IS_VIBRATE", true) ?: true
        val now = LocalTime.now()
        val timeText = String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)

        val displayLabel = if (activeAlarmIds.size > 1) {
            if (label.isNotEmpty()) "$label (+${activeAlarmIds.size - 1})" else "Будильники (${activeAlarmIds.size})"
        } else {
            label
        }

        val noticeCenter = NoticeCenter(this)
        val activeNotification = noticeCenter.buildActiveAlarmNotification(
            if (alarmId != -1L) alarmId else (activeAlarmIds.firstOrNull() ?: 1L),
            displayLabel,
            timeText
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                activeNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, activeNotification)
        }

        // Запрос эксклюзивного аудиофокуса
        requestAudioFocusSafe()

        // Воспроизведение звука с поддержкой настройки плавного нарастания
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

                    volumeRampJob = serviceScope.launch {
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

        // Вибрация с учетом флага будильника
        if (isVibrate) {
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        }

        // Автоотключение по таймеру autoDismissMinutes без паразитного снуза
        val autoDismissMins = settingsManager.autoDismissMinutes
        if (autoDismissMins > 0) {
            autoDismissJob = serviceScope.launch {
                delay(autoDismissMins * 60 * 1000L)
                // Завершаем звон: повторяющиеся будильники уже запланированы на следующий день в AlarmReceiver
                stopAlarmPlaybackAndExit()
            }
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
                putExtra("EXTRA_ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", displayLabel)
            }
            startActivity(alertIntent)
        }

        return START_STICKY
    }

    private fun requestAudioFocusSafe() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
    }

    private fun abandonAudioFocusSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun stopMediaAndVibratorOnly() {
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

    private fun stopAlarmPlaybackAndExit() {
        activeAlarmIds.clear()
        volumeRampJob?.cancel()
        autoDismissJob?.cancel()
        stopMediaAndVibratorOnly()
        abandonAudioFocusSafe()
        sendBroadcast(Intent(AlarmAlertActivity.ACTION_CLOSE_ALERT).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopAlarmPlaybackAndExit()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}