package com.necromagik.pureclock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.theme.PureClockGreen
import com.necromagik.pureclock.ui.theme.PureClockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random

class AlarmAlertActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeListener: (() -> Unit)? = null
    private var lastShakeTime: Long = 0
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeUpDeviceAndShowOverLockscreen()

        val isTimer = intent.getBooleanExtra("IS_TIMER", false)
        val label = intent.getStringExtra("ALARM_LABEL") ?: if (isTimer) "Таймер" else "Будильник"

        if (isTimer) {
            playTimerSoundAndVibration()
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            PureClockTheme {
                AlarmAlertContent(
                    isTimer = isTimer,
                    displayLabel = label,
                    onDismiss = { stopAlarmAndExit() },
                    onSnooze = { if (!isTimer) snoozeAlarm() else stopAlarmAndExit() },
                    registerShakeListener = { listener ->
                        shakeListener = listener
                        accelerometer?.let {
                            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                        }
                    }
                )
            }
        }
    }

    private fun playTimerSoundAndVibration() {
        val settings = SettingsManager.getInstance(this)

        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (settings.isTimerVibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
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

    private fun snoozeAlarm() {
        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
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
        stopAlarmAndExit()
    }

    private fun wakeUpDeviceAndShowOverLockscreen() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "PureClock:AlarmAlertWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "PureClock:AlarmAlertWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 минут
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            if (gForce > 2.2) {
                val now = System.currentTimeMillis()
                if (lastShakeTime + 800 < now) {
                    lastShakeTime = now
                    shakeListener?.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        stopMediaAndVibration()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    private fun stopMediaAndVibration() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAlarmAndExit() {
        stopMediaAndVibration()

        // Снимаем полноэкранное уведомление таймера
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(2003)

        val intent = Intent(this, AlarmService::class.java)
        stopService(intent)
        TimerService.stopService(this)
        dismissAndExit()
    }

    private fun dismissAndExit() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}

@Composable
fun AlarmAlertContent(
    isTimer: Boolean,
    displayLabel: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    registerShakeListener: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val dismissMethod = remember { settingsManager.dismissMethod }

    var currentTimeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            kotlinx.coroutines.delay(1000)
        }
    }

    val num1 = remember { Random.nextInt(12, 49) }
    val num2 = remember { Random.nextInt(4, 19) }
    val correctAnswer = remember { num1 + num2 }
    var mathInput by remember { mutableStateOf("") }

    var shakeCount by remember { mutableIntStateOf(5) }

    DisposableEffect(dismissMethod) {
        if (dismissMethod == "SHAKE") {
            registerShakeListener {
                if (shakeCount > 1) {
                    shakeCount--
                } else {
                    onDismiss()
                }
            }
        }
        onDispose {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager?.unregisterListener(context as? SensorEventListener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayLabel,
                color = PureClockGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentTimeText.ifEmpty { "00:00" },
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when (dismissMethod) {
            "MATH" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Решите пример для сброса:", color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$num1 + $num2 = ?", color = PureClockGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = mathInput,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                mathInput = input
                                if (input.toIntOrNull() == correctAnswer) {
                                    onDismiss()
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PureClockGreen, unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.width(140.dp)
                    )

                    if (!isTimer) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onSnooze,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Отложить (${settingsManager.defaultSnoozeTimeMinutes} мин)", color = Color.White)
                        }
                    }
                }
            }

            "SHAKE" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Потрясите телефон!", color = Color.Gray, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Осталось: $shakeCount", color = PureClockGreen, fontSize = 36.sp, fontWeight = FontWeight.Bold)

                    if (!isTimer) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onSnooze,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Отложить (${settingsManager.defaultSnoozeTimeMinutes} мин)", color = Color.White)
                        }
                    }
                }
            }

            else -> {
                SwipeToDismissBar(
                    isTimer = isTimer,
                    onDismiss = onDismiss,
                    onSnooze = onSnooze,
                    snoozeMinutes = settingsManager.defaultSnoozeTimeMinutes
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissBar(
    isTimer: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    snoozeMinutes: Int
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onDismiss()
            SwipeToDismissBoxValue.EndToStart -> if (!isTimer) onSnooze() else onDismiss()
            SwipeToDismissBoxValue.Settled -> {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = {
                val color = when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> PureClockGreen
                    SwipeToDismissBoxValue.EndToStart -> if (!isTimer) Color(0xFF2C2C2E) else PureClockGreen
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, RoundedCornerShape(32.dp))
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                        Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Text(
                        text = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd || isTimer) "Сброс" else "Отложить",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            content = {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Свайп вправо: Сброс", color = PureClockGreen, fontSize = 13.sp)
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray)
                        Text(if (!isTimer) "Свайп влево: Отложить" else "Свайп влево: Сброс", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        )

        if (!isTimer) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Отсрочка сигналов на $snoozeMinutes мин",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }
    }
}