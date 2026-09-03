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
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.PureClockGreen
import com.necromagik.pureclock.ui.theme.PureClockTheme
import com.necromagik.pureclock.ui.theme.pure3DEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

class AlarmAlertActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeListener: ((direction: Float) -> Unit)? = null
    private var lastShakeTime: Long = 0
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentTimerId: String? = null
    private val durationTextState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeUpDeviceAndShowOverLockscreen()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Предотвращаем случайное закрытие кнопкой/жестом "Назад" без явного действия
            }
        })

        parseIntentData(intent)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val isTimer = intent.getBooleanExtra("IS_TIMER", false)
        val label = intent.getStringExtra("ALARM_LABEL") ?: if (isTimer) "Таймер" else "Будильник"

        setContent {
            PureClockTheme {
                AlarmAlertContent(
                    isTimer = isTimer,
                    displayLabel = label,
                    timerDurationDisplay = durationTextState.value,
                    onDismiss = { stopAlarmAndExit() },
                onSnooze = { if (!isTimer) snoozeAlarm() else stopAlarmAndExit() },
                onAddTimerTime = { extraMinutes -> addTimerMinutesAndRestart(label, extraMinutes) },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        wakeUpDeviceAndShowOverLockscreen()
        parseIntentData(intent)
    }

    private fun parseIntentData(intent: Intent) {
        val isTimer = intent.getBooleanExtra("IS_TIMER", false)
        currentTimerId = intent.getStringExtra(TimerService.EXTRA_TIMER_ID)
            ?: intent.getStringExtra("EXTRA_TIMER_ID")
        val durationSec = intent.getLongExtra("EXTRA_TIMER_DURATION_SECONDS", 0L)
        val passedDurationText = intent.getStringExtra("EXTRA_TIMER_DURATION_TEXT")

        durationTextState.value = when {
            !passedDurationText.isNullOrBlank() -> passedDurationText
                durationSec > 0L -> formatTimerDuration(durationSec)
            else -> "Время вышло"
        }

        if (isTimer) {
            playTimerSoundAndVibration()
        }
    }

    private fun addTimerMinutesAndRestart(label: String, minutesToAdd: Int) {
        stopMediaAndVibration()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(2003)

        // Обновляем карточку таймера в хранилище и перепланируем тревогу через TimerService
        TimerService.extendTimer(this, currentTimerId, label, minutesToAdd)
        dismissAndExit()
    }

    private fun formatTimerDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60

        return when {
            h > 0 && m > 0 && s > 0 -> String.format(Locale.ROOT, "%dч %02dм %02dс", h, m, s)
                h > 0 && m > 0 -> String.format(Locale.ROOT, "%dч %02dм", h, m)
                h > 0 && s > 0 -> String.format(Locale.ROOT, "%dч %02dс", h, s)
                h > 0 -> "${h}ч"
                m > 0 && s > 0 -> String.format(Locale.ROOT, "%dм %02dс", m, s)
                m > 0 -> "${m}м"
            else -> "${s}с"
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

        @Suppress("DEPRECATION")
        val wakeLockFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE

        wakeLock = powerManager.newWakeLock(wakeLockFlags, "PureClock:AlarmAlertWakeLock").apply {
            acquire(10 * 60 * 1000L)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(
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
            if (gForce > 1.8) {
                val now = System.currentTimeMillis()
                shakeListener?.invoke(x)
                if (gForce > 2.2 && lastShakeTime + 650 < now) {
                    lastShakeTime = now
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

// ============================================================================
// ГЛАВНЫЙ ЭКРАН СИГНАЛА
// ============================================================================
@Composable
fun AlarmAlertContent(
    isTimer: Boolean,
    displayLabel: String,
    timerDurationDisplay: String = "5м",
    onDismiss: () -> Unit = {},
    onSnooze: () -> Unit = {},
    onAddTimerTime: (Int) -> Unit = {},
    registerShakeListener: ((onShakeDirection: (Float) -> Unit) -> Unit) = {}
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    val context = LocalContext.current
    val settingsManager = remember {
        try { SettingsManager.getInstance(context) } catch (_: Exception) { null }
    }
    val dismissMethod = settingsManager?.dismissMethod ?: "SWIPE"
    val snoozeMinutes = settingsManager?.defaultSnoozeTimeMinutes ?: 10

    var currentTimeText by remember { mutableStateOf("") }

    LaunchedEffect(isTimer) {
        if (!isTimer) {
            while (true) {
                currentTimeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "NeonPulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val iconTilt by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IconTilt"
    )

    var shakeCount by remember { mutableIntStateOf(5) }
    val leftEdgeAlpha = remember { Animatable(0f) }
    val rightEdgeAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(dismissMethod) {
        if (dismissMethod == "SHAKE") {
            registerShakeListener { xDirection ->
                coroutineScope.launch {
                    if (xDirection > 0f) {
                        rightEdgeAlpha.snapTo(0.65f)
                        rightEdgeAlpha.animateTo(0f, tween(300))
                    } else {
                        leftEdgeAlpha.snapTo(0.65f)
                        leftEdgeAlpha.animateTo(0f, tween(300))
                    }
                }
                if (shakeCount > 1) {
                    shakeCount--
                } else {
                    onDismiss()
                }
            }
        }
        onDispose {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sm?.unregisterListener(context as? SensorEventListener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    .background(Color(0xFF07080A))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.32f)
            val baseRadius = size.width * 0.46f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = pulseAlpha),
            accentColor.copy(alpha = pulseAlpha * 0.3f),
            Color.Transparent
            ),
            center = center,
            radius = baseRadius * pulseScale
            ),
            center = center,
            radius = baseRadius * pulseScale
            )

            drawCircle(
                color = accentColor.copy(alpha = pulseAlpha * 0.5f),
            center = center,
            radius = baseRadius * 0.65f * pulseScale,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
        .width(48.dp)
        .align(Alignment.CenterStart)
        .background(
        Brush.horizontalGradient(
            colors = listOf(accentColor.copy(alpha = leftEdgeAlpha.value), Color.Transparent)
        )
        )
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
        .width(48.dp)
        .align(Alignment.CenterEnd)
        .background(
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, accentColor.copy(alpha = rightEdgeAlpha.value))
        )
        )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
        .padding(24.dp)
        .statusBarsPadding()
        .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Box(
            modifier = Modifier
                .pure3DEffect(
                    shape = RoundedCornerShape(24.dp),
        accentColor = accentColor,
        depthDp = themeConfig.depthIntensityDp,
        is3dEnabled = themeConfig.is3dEnabled,
        isGlowEnabled = true,
        surfaceColor = Color(0xFF14171E)
        )
        .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Icon(
            imageVector = if (isTimer) Icons.Default.HourglassTop else Icons.Default.Alarm,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier
            .size(24.dp)
        .graphicsLayer { rotationZ = iconTilt }
        )
        Text(
            text = displayLabel.uppercase(),
        color = accentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.4.sp
        )
    }
    }

        Spacer(modifier = Modifier.height(8.dp))

        val timeToDisplay = if (isTimer) timerDurationDisplay else currentTimeText.ifEmpty { "00:00" }

        RollingAlertTimeText(
            formattedTime = timeToDisplay,
        accentColor = accentColor,
        isLongText = isTimer && timerDurationDisplay.length > 5
        )

        Text(
            text = if (isTimer) "Таймер завершил отсчёт" else "Пора просыпаться!",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
        )
    }

        if (isTimer) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
            ) {
                TimerOneWaySwipeBar(
                    accentColor = accentColor,
                onDismiss = onDismiss
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                listOf(1, 3, 5).forEach { mins ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1F28))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .bounceClick { onAddTimerTime(mins) },
                    contentAlignment = Alignment.Center
                    ) {
                    Text(
                        text = "+$mins мин",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                    )
                }
                }
            }
            }
        } else {
            when (dismissMethod) {
                "MATH" -> {
                    MathDismissCard(
                        accentColor = accentColor,
                    snoozeMinutes = snoozeMinutes,
                    onDismiss = onDismiss,
                    onSnooze = onSnooze
                    )
                }

                "SHAKE" -> {
                    ShakeDismissSection(
                        shakeCount = shakeCount,
                    accentColor = accentColor,
                    snoozeMinutes = snoozeMinutes,
                    onSnooze = onSnooze
                    )
                }

                else -> {
                    AlarmDragAndReleaseSwipeBar(
                        accentColor = accentColor,
                    snoozeMinutes = snoozeMinutes,
                    onDismiss = onDismiss,
                    onSnooze = onSnooze
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
    }
}

// ============================================================================
// УЛУЧШЕННЫЙ РЕЖИМ ВСТРЯХИВАНИЯ (SHAKE С АНИМИРОВАННЫМИ СТРЕЛКАМИ)
// ============================================================================
@Composable
private fun ShakeDismissSection(
    shakeCount: Int,
    accentColor: Color,
    snoozeMinutes: Int,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShakeArrowsTransition")

    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ArrowOffset"
    )

    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ArrowAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp),
    modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                translationX = -arrowOffset
                alpha = arrowAlpha
            }
        ) {
        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
    }

        Box(
            modifier = Modifier
                .size(96.dp)
        .clip(CircleShape)
        .background(Color(0xFF141720))
        .border(2.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
        ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$shakeCount",
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "ВСТРЯХНИ",
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
            )
        }
    }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer {
                translationX = arrowOffset
                alpha = arrowAlpha
            }
        ) {
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
    }
    }

        Text(
            text = "Трясите телефон влево и вправо для сброса",
        color = Color.Gray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .height(48.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF181B24))
        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
        .clickable { onSnooze() }
        .bounceClick()
        .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Icon(Icons.Default.Snooze, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        Text(
            text = "Отложить на $snoozeMinutes мин",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
        )
    }
    }
    }
}

// ============================================================================
// УЛУЧШЕННЫЙ РЕЖИМ МАТЕМАТИКИ (MATH 3D-КАРТОЧКА)
// ============================================================================
@Composable
private fun MathDismissCard(
    accentColor: Color,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val num1 = remember { Random.nextInt(15, 65) }
    val num2 = remember { Random.nextInt(7, 28) }
    val correctAnswer = remember { num1 + num2 }
    var mathInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
    .clip(RoundedCornerShape(28.dp))
    .background(Color(0xFF13151D))
    .border(1.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
    .padding(20.dp),
    contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
        ) {
        Text(
            text = "РЕШИТЕ ПРИМЕР ДЛЯ СБРОСА",
        color = Color.Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp
        )

        Text(
            text = "$num1 + $num2 = ?",
        color = accentColor,
        fontSize = 38.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp
        )

        OutlinedTextField(
            value = mathInput,
        onValueChange = { input ->
            if (input.length <= 4 && input.all { it.isDigit() }) {
                mathInput = input
                if (input.toIntOrNull() == correctAnswer) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            }
        },
        singleLine = true,
        placeholder = { Text("Ответ", color = Color.Gray.copy(alpha = 0.5f), fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 28.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedContainerColor = Color(0xFF1B1E28),
            unfocusedContainerColor = Color(0xFF181A22)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(160.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
        .height(46.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF1E212B))
        .clickable { onSnooze() }
        .bounceClick(),
        contentAlignment = Alignment.Center
        ) {
        Text(
            text = "Отложить сигнал ($snoozeMinutes мин)",
        color = Color.Gray,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
        )
    }
    }
    }
}

// ============================================================================
// КАПСУЛА БУДИЛЬНИКА: ЦЕНТРАЛЬНАЯ ТОЧКА (СБРОС ВПРАВО / ОТЛОЖИТЬ ВЛЕВО)
// ============================================================================
@Composable
private fun AlarmDragAndReleaseSwipeBar(
    accentColor: Color,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbWidth = 64.dp
    val thumbWidthPx = with(density) { thumbWidth.toPx() }

    val maxDragPx = remember(containerWidthPx, thumbWidthPx) {
        ((containerWidthPx - thumbWidthPx) / 2f).coerceAtLeast(0f)
    }

    val dragOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val dismissColor = Color(0xFFFF3B30)
    val snoozeColor = Color(0xFF8E8E93)

    val progress = remember(dragOffsetX.value, maxDragPx) {
        if (maxDragPx > 0f) (dragOffsetX.value / maxDragPx).coerceIn(-1f, 1f) else 0f
    }

    val currentThumbColor = when {
        progress > 0f -> Color(
            red = accentColor.red + (dismissColor.red - accentColor.red) * progress,
            green = accentColor.green + (dismissColor.green - accentColor.green) * progress,
            blue = accentColor.blue + (dismissColor.blue - accentColor.blue) * progress,
            alpha = 1f
        )
            progress < 0f -> {
            val p = -progress
            Color(
                red = accentColor.red + (snoozeColor.red - accentColor.red) * p,
                green = accentColor.green + (snoozeColor.green - accentColor.green) * p,
                blue = accentColor.blue + (snoozeColor.blue - accentColor.blue) * p,
                alpha = 1f
            )
        }
        else -> accentColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        .height(68.dp)
        .onSizeChanged { containerWidthPx = it.width.toFloat() }
        .clip(RoundedCornerShape(34.dp))
        .background(
        brush = Brush.horizontalGradient(
            colors = listOf(
                if (progress < 0f) snoozeColor.copy(alpha = (-progress * 0.3f).coerceIn(0f, 0.3f)) else Color(0xFF16181F),
        Color(0xFF16181F),
        if (progress > 0f) dismissColor.copy(alpha = (progress * 0.35f).coerceIn(0f, 0.35f)) else Color(0xFF16181F)
        )
        )
        )
        .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(34.dp)
    )
        .pointerInput(maxDragPx) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (maxDragPx > 0f && dragOffsetX.value >= maxDragPx * 0.82f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        dragOffsetX.animateTo(maxDragPx, tween(120))
                        onDismiss()
                    }
                } else if (maxDragPx > 0f && dragOffsetX.value <= -maxDragPx * 0.82f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        dragOffsetX.animateTo(-maxDragPx, tween(120))
                        onSnooze()
                    }
                } else {
                    scope.launch {
                        dragOffsetX.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    dragOffsetX.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                val newTarget = (dragOffsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                scope.launch { dragOffsetX.snapTo(newTarget) }
            }
        )
    },
        contentAlignment = Alignment.Center
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
        Text(
            text = "◂ Отложить",
        color = if (progress < -0.3f) snoozeColor else Color.Gray.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
        )
        Text(
            text = "Сброс ▸",
        color = if (progress > 0.3f) dismissColor else Color.Gray.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
        )
    }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        .size(width = thumbWidth, height = 68.dp)
        .clip(RoundedCornerShape(34.dp))
        .background(currentThumbColor),
        contentAlignment = Alignment.Center
        ) {
        val isAtRightEnd = maxDragPx > 0f && dragOffsetX.value >= maxDragPx * 0.8f
        val isAtLeftEnd = maxDragPx > 0f && dragOffsetX.value <= -maxDragPx * 0.8f

        when {
            isAtRightEnd -> {
                Icon(
                    imageVector = Icons.Default.Check,
                contentDescription = "Сброс",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
                )
            }
            isAtLeftEnd -> {
                Icon(
                    imageVector = Icons.Default.Alarm,
                contentDescription = "Отложить",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape))
                    Box(modifier = Modifier.size(4.dp, 20.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape))
                    Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape))
                }
            }
        }
    }
    }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Отложить на $snoozeMinutes мин (влево) • Сбросить (вправо)",
        color = Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// КАПСУЛА ТАЙМЕРА: СВАЙП ВПРАВО ДЛЯ СБРОСА
// ============================================================================
@Composable
private fun TimerOneWaySwipeBar(
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbWidth = 64.dp
    val thumbWidthPx = with(density) { thumbWidth.toPx() }

    val maxDragPx = remember(containerWidthPx, thumbWidthPx) {
        (containerWidthPx - thumbWidthPx).coerceAtLeast(0f)
    }

    val dragOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
    .height(68.dp)
    .onSizeChanged { containerWidthPx = it.width.toFloat() }
    .clip(RoundedCornerShape(34.dp))
    .background(Color(0xFF181B22))
    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(34.dp))
    .pointerInput(maxDragPx) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (maxDragPx > 0f && dragOffsetX.value >= maxDragPx * 0.75f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        dragOffsetX.animateTo(maxDragPx, tween(150))
                        onDismiss()
                    }
                } else {
                    scope.launch {
                        dragOffsetX.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                }
            },
            onDragCancel = {
                scope.launch { dragOffsetX.animateTo(0f) }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                val newTarget = (dragOffsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                scope.launch { dragOffsetX.snapTo(newTarget) }
            }
        )
    },
    contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        .padding(start = 80.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
        ) {
        Text("Свайп вправо для сброса", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
    }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        .size(width = thumbWidth, height = 68.dp)
        .clip(RoundedCornerShape(34.dp))
        .background(accentColor),
        contentAlignment = Alignment.Center
        ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = Color.Black,
        modifier = Modifier.size(24.dp)
        )
    }
    }
}

// ============================================================================
// БАРАБАННЫЙ НАКАТ ЦИФР
// ============================================================================
@Composable
private fun RollingAlertTimeText(
    formattedTime: String,
    accentColor: Color,
    isLongText: Boolean
) {
    val fontSize = if (isLongText) 48.sp else 72.sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth()
    ) {
        formattedTime.forEachIndexed { index, char ->
            if (char == ':' || char == ' ' || char == 'ч' || char == 'м' || char == 'с') {
                Text(
                    text = char.toString(),
                fontSize = if (char == 'ч' || char == 'м' || char == 'с') (fontSize.value * 0.45f).sp else fontSize,
                fontWeight = FontWeight.Bold,
                color = if (char == 'ч' || char == 'м' || char == 'с') accentColor else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = if (char == ' ') 2.dp else 1.dp)
                )
            } else {
                AnimatedContent(
                    targetState = char,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { -it } + fadeIn()).togetherWith(
                        slideOutVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { it } + fadeOut()
                    )
                },
                label = "AlertRollDigit_$index"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                    )
                }
            }
        }
    }
}

// ============================================================================
// COMPOSE PREVIEWS
// ============================================================================
@Preview(name = "Alarm Alert (Будильник: Свайп)", showBackground = true, backgroundColor = 0xFF07080A)
@Composable
private fun AlarmAlertPreview_Alarm() {
    PureClockTheme {
        AlarmAlertContent(
            isTimer = false,
        displayLabel = "Подъем на работу",
        timerDurationDisplay = "07:30"
        )
    }
}

@Preview(name = "Alarm Alert (Таймер 5 мин)", showBackground = true, backgroundColor = 0xFF07080A)
@Composable
private fun AlarmAlertPreview_Timer() {
    PureClockTheme {
        AlarmAlertContent(
            isTimer = true,
        displayLabel = "Варка спагетти",
        timerDurationDisplay = "5м 00с"
        )
    }
}