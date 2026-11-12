package com.necromagik.pureclock.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.AnalogStyle
import com.necromagik.pureclock.data.DigitalStyle
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.PureClockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val RU_LOCALE = Locale.forLanguageTag("ru")

private fun getShortestAngleDelta(currentAngle: Float, targetAngle: Float): Float {
    val currentRad = Math.toRadians(currentAngle.toDouble())
    val targetRad = Math.toRadians(targetAngle.toDouble())
    val diffRad = atan2(sin(targetRad - currentRad), cos(targetRad - currentRad))
    return Math.toDegrees(diffRad).toFloat()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SmoothAnalogClock(
    timeZoneId: String = ZoneId.systemDefault().id,
    analogStyle: AnalogStyle = AnalogStyle.OXYGEN,
    digitalStyle: DigitalStyle = DigitalStyle.OXYGEN_LARGE,
    clockSize: Dp = 240.dp,
    // =========================================================================
    // ПАРАМЕТРЫ ДИНАМИКИ СТРЕЛКИ ДЛЯ ТОНКОЙ НАСТРОЙКИ:
    // =========================================================================
    /** Общая длительность хода стрелки (мс). Рекомендуется: 180..320 мс */
    tickDurationMillis: Int = 200,
    /** Угол перелёта вперёд за деление (в градусах, 1 сек = 6.0°). Рекомендуется: 0.8°..2.2° */
    overshootDegrees: Float = 1.6f,
    /** Доля времени на бросок вперёд (0.65 = 65% времени вперёд, 35% на откат назад) */
    forwardTimeRatio: Float = 0.65f,
    /** Длительность приветственного разворота стрелок от 0° к текущему времени (мс) */
    entranceDurationMillis: Int = 500,
    // =========================================================================
    onShiftHoursChanged: ((Int) -> Unit)? = null,
    onSecretCodeTriggered: (() -> Unit)? = null,
    onScenaryCodeTriggered: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var isDigitalMode by remember { mutableStateOf(settingsManager.isDigitalClockMode) }
    var nowDateTime by remember { mutableStateOf(LocalDateTime.now(ZoneId.of(timeZoneId))) }

    var shiftHoursCount by rememberSaveable { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    // Для превью в Android Studio выставляем красивое витринное время 10:10:35,
    // на реальном устройстве — стартуем строго из 0° для приветственного разворота
    val initialH = if (isInspection) 305f else 0f
    val initialM = if (isInspection) 60f else 0f
    val initialS = if (isInspection) 210f else 0f

    val animHourAngle = remember { Animatable(initialH) }
    val animMinuteAngle = remember { Animatable(initialM) }
    val animSecondAngle = remember { Animatable(initialS) }

    var isInitialized by remember { mutableStateOf(isInspection) }

    LaunchedEffect(shiftHoursCount) {
        onShiftHoursChanged?.invoke(shiftHoursCount)
    }

// ============================================================================
// СЕКЦИЯ 1: ПЛАВНЫЙ СТАРТ ИЗ 0° И КВАРЦЕВЫЙ ТИК С ПЕРЕЛЁТОМ
// ============================================================================
    var continuousSecondAngle by remember { mutableDoubleStateOf(0.0) }
    var lastRawSecond by remember { mutableIntStateOf(-1) }

    LaunchedEffect(timeZoneId) {
        if (isInspection) return@LaunchedEffect

        val initialDateTime = LocalDateTime.now(ZoneId.of(timeZoneId))
        nowDateTime = initialDateTime

        val initialSec = initialDateTime.second
        val initialMin = initialDateTime.minute
        val initialHour = initialDateTime.hour

        val targetSecAngle = initialSec * 6f
        val targetMinAngle = (initialMin + initialSec / 60f) * 6f
        val targetHourAngle = ((initialHour % 12) + initialMin / 60f + initialSec / 3600f) * 30f

        animHourAngle.snapTo(0f)
        animMinuteAngle.snapTo(0f)
        animSecondAngle.snapTo(0f)

        continuousSecondAngle = targetSecAngle.toDouble()

        // Плавная приветственная инициализация стрелок из 0°
        val entranceSpec = tween<Float>(
            durationMillis = entranceDurationMillis,
            easing = FastOutSlowInEasing
        )

        launch { animHourAngle.animateTo(targetHourAngle, entranceSpec) }
        launch { animMinuteAngle.animateTo(targetMinAngle, entranceSpec) }
        val secondEntranceJob = launch { animSecondAngle.animateTo(targetSecAngle, entranceSpec) }

        secondEntranceJob.join()
        isInitialized = true
        lastRawSecond = LocalDateTime.now(ZoneId.of(timeZoneId)).second
        continuousSecondAngle = (lastRawSecond * 6f).toDouble()

        // Рабочий цикл тика с инерционным отскоком
        while (true) {
            val now = LocalDateTime.now(ZoneId.of(timeZoneId))
            nowDateTime = now

            val currentSec = now.second
            if (currentSec != lastRawSecond) {
                val step = if (currentSec < lastRawSecond) {
                    (60 - lastRawSecond + currentSec)
                } else {
                    currentSec - lastRawSecond
                }
                continuousSecondAngle += step * 6.0
                lastRawSecond = currentSec

                val target = continuousSecondAngle.toFloat()
                val forwardDuration = (tickDurationMillis * forwardTimeRatio.coerceIn(0.1f, 0.9f)).toInt()

                // Ключевой эффект: резкий рывок вперед с перелётом + возврат назад на деление
                launch {
                    animSecondAngle.animateTo(
                        targetValue = target,
                        animationSpec = keyframes {
                            durationMillis = tickDurationMillis
                            // 1. Быстрый импульс вперёд: пролетаем метку на overshootDegrees
                            (target + overshootDegrees) at forwardDuration using FastOutLinearInEasing
                            // 2. Упругий возврат назад точно на метку шкалы
                            target at tickDurationMillis using LinearOutSlowInEasing
                        }
                    )
                }

                // Плавное следование минутной стрелки
                val currentMinAngle = (now.minute + now.second / 60f) * 6f
                launch {
                    animMinuteAngle.animateTo(
                        targetValue = currentMinAngle,
                        animationSpec = tween(
                            durationMillis = tickDurationMillis,
                            easing = LinearOutSlowInEasing
                        )
                    )
                }
            }

            delay(16)
        }
    }

    val effectiveDateTime = remember(nowDateTime, shiftHoursCount) {
        nowDateTime.plusHours(shiftHoursCount.toLong())
    }
    val effectiveTime = effectiveDateTime.toLocalTime()
    val effectiveDate = effectiveDateTime.toLocalDate()

    val rawSystemHourAngle = ((nowDateTime.hour % 12) + nowDateTime.minute / 60f + nowDateTime.second / 3600f) * 30f

    LaunchedEffect(rawSystemHourAngle, isDragging, shiftHoursCount, isInitialized) {
        if (!isDragging && isInitialized) {
            val targetAngle = rawSystemHourAngle + (shiftHoursCount * 30f)
            val delta = getShortestAngleDelta(animHourAngle.value, targetAngle)

            if (shiftHoursCount == 0) {
                animHourAngle.animateTo(
                    targetValue = animHourAngle.value + delta,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    )
                )
            } else {
                animHourAngle.snapTo(animHourAngle.value + delta)
            }
        }
    }

    val finalHourAngle = animHourAngle.value

// ============================================================================
// СЕКЦИЯ 2: АНИМАЦИЯ МЕРЦАНИЯ И ЦВЕТОВЫЕ НАСТРОЙКИ
// ============================================================================
    var flickerText by remember { mutableStateOf("") }
    var isFlickering by remember { mutableStateOf(!isInspection) }
    val recentShifts = remember { mutableStateListOf<Int>() }

    val flickerAlpha by animateFloatAsState(
        targetValue = if (isFlickering) 0.3f else 1.0f,
        animationSpec = tween(150, easing = LinearEasing),
        label = "FlickerAlpha"
    )

    LaunchedEffect(timeZoneId) {
        if (isInspection) return@LaunchedEffect
        isFlickering = true
        repeat(4) {
            val randH = Random.nextInt(0, 24)
            val randM = Random.nextInt(0, 60)
            flickerText = String.format(Locale.ROOT, "%02d:%02d", randH, randM)
            delay(40)
        }
        isFlickering = false
    }

    val clockFaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val clockOutline = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val hourHandColor = MaterialTheme.colorScheme.onBackground
    val minuteHandColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = isDigitalMode,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(targetScale = 0.85f))
            },
            label = "AnalogDigitalSwitch"
        ) { digitalActive ->
            if (digitalActive) {
                Surface(
                    onClick = {
                        isDigitalMode = false
                        settingsManager.isDigitalClockMode = false
                        triggerHaptic(context)
                    },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(clockSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val hoursStr = if (settingsManager.is24HourFormat) effectiveTime.format(DateTimeFormatter.ofPattern("HH")) else effectiveTime.format(DateTimeFormatter.ofPattern("hh"))
                        val minutesStr = effectiveTime.format(DateTimeFormatter.ofPattern("mm"))
                        val timeString = if (isFlickering) flickerText else "$hoursStr:$minutesStr"

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer { alpha = flickerAlpha }
                        ) {
                            when (digitalStyle) {
                                DigitalStyle.SECTIONAL -> {
                                    SevenSegmentTimeDisplay(
                                        timeString = timeString.take(5),
                                        accentColor = accentColor,
                                        digitWidth = clockSize * 0.13f,
                                        digitHeight = clockSize * 0.23f
                                    )
                                }
                                DigitalStyle.VERTICAL -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = hoursStr,
                                            fontSize = (clockSize.value * 0.26f).sp,
                                            fontWeight = FontWeight.Black,
                                            color = accentColor,
                                            lineHeight = (clockSize.value * 0.24f).sp
                                        )
                                        Text(
                                            text = minutesStr,
                                            fontSize = (clockSize.value * 0.26f).sp,
                                            fontWeight = FontWeight.Light,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            lineHeight = (clockSize.value * 0.24f).sp
                                        )
                                    }
                                }
                                DigitalStyle.CYBER_MONO -> {
                                    Box(
                                        modifier = Modifier
                                            .border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "> $timeString",
                                            fontSize = (clockSize.value * 0.16f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                else -> { // OXYGEN_LARGE
                                    Text(
                                        text = timeString,
                                        fontSize = (clockSize.value * 0.20f).sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accentColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Нажмите для переключения",
                                fontSize = 11.sp,
                                color = Color.Gray.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
// ============================================================================
// СЕКЦИЯ 3: ОПРЕДЕЛЕНИЕ ШКАЛ И ЦИФР АНАЛОГОВЫХ СТИЛЕЙ
// ============================================================================
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(clockSize)
                        .pointerInput(Unit) {
                            var accumulatedSweep = 0f
                            var lastTouchAngle = 0f
                            var isTouchValid = false

                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                                    val dx = startOffset.x - center.x
                                    val dy = startOffset.y - center.y
                                    val distFromCenter = sqrt(dx * dx + dy * dy)

                                    if (distFromCenter <= this.size.width / 2f) {
                                        isTouchValid = true
                                        isDragging = true
                                        accumulatedSweep = 0f
                                        lastTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    } else {
                                        isTouchValid = false
                                    }
                                },
                                onDragEnd = {
                                    if (isTouchValid) {
                                        isDragging = false
                                        isTouchValid = false

                                        if (shiftHoursCount != 0) {
                                            recentShifts.add(shiftHoursCount)
                                            if (recentShifts.size > 3) {
                                                recentShifts.removeAt(0)
                                            }

                                            when (recentShifts.toList()) {
                                                listOf(1, -5, 9) -> {
                                                    recentShifts.clear()
                                                    onSecretCodeTriggered?.invoke()
                                                }
                                                listOf(10, -5, 3) -> {
                                                    recentShifts.clear()
                                                    onScenaryCodeTriggered?.invoke()
                                                }
                                            }
                                        }

                                        val shortestDelta = getShortestAngleDelta(animHourAngle.value, rawSystemHourAngle)

                                        shiftHoursCount = 0
                                        coroutineScope.launch {
                                            animHourAngle.animateTo(
                                                targetValue = animHourAngle.value + shortestDelta,
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessLow,
                                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                                )
                                            )
                                        }

                                        triggerHaptic(context)
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    isTouchValid = false
                                    shiftHoursCount = 0
                                },
                                onDrag = { change, _ ->
                                    if (!isTouchValid) return@detectDragGestures
                                    change.consume()

                                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                                    val dx = change.position.x - center.x
                                    val dy = change.position.y - center.y
                                    val currentTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                                    var delta = currentTouchAngle - lastTouchAngle
                                    if (delta > 180f) delta -= 360f
                                    if (delta < -180f) delta += 360f

                                    accumulatedSweep += delta
                                    lastTouchAngle = currentTouchAngle

                                    coroutineScope.launch {
                                        animHourAngle.snapTo(animHourAngle.value + delta)
                                    }

                                    if (accumulatedSweep >= 25f) {
                                        shiftHoursCount += 1
                                        accumulatedSweep -= 30f
                                        triggerHaptic(context)
                                    } else if (accumulatedSweep <= -25f) {
                                        shiftHoursCount -= 1
                                        accumulatedSweep += 30f
                                        triggerHaptic(context)
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = size
                        val diameter = canvasSize.minDimension
                        val radius = diameter / 2f
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                        drawCircle(color = clockFaceBg, radius = radius, center = center)
                        drawCircle(color = clockOutline, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))

                        // --- 1. ШКАЛА, РИСКИ И ЦИФРЫ ---
                        when (analogStyle) {
                            AnalogStyle.OXYGEN -> {
                                val redColor = Color(0xFFFF5252)

                                for (i in 0 until 60) {
                                    val isHour = i % 5 == 0
                                    val isOneHourTick = i == 5
                                    val angle = i * 6f

                                    val tickLength = if (isHour) 8.dp.toPx() else 4.dp.toPx()
                                    val strokeWidth = if (isHour) 2.dp.toPx() else 1.dp.toPx()
                                    val tickColor = if (isOneHourTick) redColor else if (isHour) hourHandColor else Color.Gray.copy(alpha = 0.35f)

                                    rotate(angle, center) {
                                        drawLine(
                                            color = tickColor,
                                            start = Offset(center.x, center.y - radius + 6.dp.toPx()),
                                            end = Offset(center.x, center.y - radius + 6.dp.toPx() + tickLength),
                                            strokeWidth = strokeWidth,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                val textPaint = Paint().apply {
                                    isAntiAlias = true
                                    textSize = radius * 0.16f
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                }

                                val numRadius = radius - 28.dp.toPx()
                                for (hour in 1..12) {
                                    val angleDeg = hour * 30f - 90f
                                    val angleRad = Math.toRadians(angleDeg.toDouble())
                                    val x = center.x + (numRadius * cos(angleRad)).toFloat()
                                    val y = center.y + (numRadius * sin(angleRad)).toFloat() + (textPaint.textSize / 3f)

                                    if (hour == 1) {
                                        textPaint.color = redColor.toArgb()
                                    } else {
                                        textPaint.color = hourHandColor.toArgb()
                                    }

                                    drawContext.canvas.nativeCanvas.drawText(hour.toString(), x, y, textPaint)
                                }
                            }

                            AnalogStyle.CLASSIC_ARABIC -> {
                                for (i in 0 until 60) {
                                    val isHour = i % 5 == 0
                                    val angle = i * 6f
                                    rotate(angle, center) {
                                        drawLine(
                                            color = if (isHour) hourHandColor else Color.Gray.copy(alpha = 0.35f),
                                            start = Offset(center.x, center.y - radius + 6.dp.toPx()),
                                            end = Offset(center.x, center.y - radius + (if (isHour) 12.dp.toPx() else 8.dp.toPx())),
                                            strokeWidth = if (isHour) 2.dp.toPx() else 1.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                val textPaint = Paint().apply {
                                    isAntiAlias = true
                                    textSize = radius * 0.17f
                                    color = hourHandColor.toArgb()
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                }

                                val numRadius = radius - 30.dp.toPx()
                                for (hour in 1..12) {
                                    val angleDeg = hour * 30f - 90f
                                    val angleRad = Math.toRadians(angleDeg.toDouble())
                                    val x = center.x + (numRadius * cos(angleRad)).toFloat()
                                    val y = center.y + (numRadius * sin(angleRad)).toFloat() + (textPaint.textSize / 3f)

                                    drawContext.canvas.nativeCanvas.drawText(hour.toString(), x, y, textPaint)
                                }
                            }

                            AnalogStyle.CLASSIC_ROMAN -> {
                                val romanList = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
                                val textPaint = Paint().apply {
                                    isAntiAlias = true
                                    textSize = radius * 0.15f
                                    color = hourHandColor.toArgb()
                                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                }

                                val numRadius = radius - 26.dp.toPx()
                                romanList.forEachIndexed { index, roman ->
                                    val hour = if (index == 0) 12 else index
                                    val angleDeg = hour * 30f - 90f
                                    val angleRad = Math.toRadians(angleDeg.toDouble())
                                    val x = center.x + (numRadius * cos(angleRad)).toFloat()
                                    val y = center.y + (numRadius * sin(angleRad)).toFloat() + (textPaint.textSize / 3f)

                                    drawContext.canvas.nativeCanvas.drawText(roman, x, y, textPaint)
                                }
                            }

                            AnalogStyle.CHRONO -> {
                                drawCircle(color = accentColor.copy(alpha = 0.2f), radius = radius - 14.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))

                                for (i in 0 until 60) {
                                    val isHour = i % 5 == 0
                                    val angle = i * 6f
                                    val tickLength = if (isHour) 12.dp.toPx() else 5.dp.toPx()
                                    val color = if (isHour) accentColor else Color.Gray.copy(alpha = 0.5f)

                                    rotate(angle, center) {
                                        drawLine(
                                            color = color,
                                            start = Offset(center.x, center.y - radius + 6.dp.toPx()),
                                            end = Offset(center.x, center.y - radius + 6.dp.toPx() + tickLength),
                                            strokeWidth = if (isHour) 2.dp.toPx() else 1.dp.toPx()
                                        )
                                    }
                                }
                            }

                            AnalogStyle.MINIMAL -> {
                                for (i in 0 until 12) {
                                    val angle = i * 30f
                                    val isMainAxis = i % 3 == 0
                                    rotate(angle, center) {
                                        drawCircle(
                                            color = if (isMainAxis) accentColor else hourHandColor.copy(alpha = 0.3f),
                                            radius = if (isMainAxis) 4.dp.toPx() else 2.dp.toPx(),
                                            center = Offset(center.x, center.y - radius + 14.dp.toPx())
                                        )
                                    }
                                }
                            }

                            AnalogStyle.ULTRA_MINIMAL -> {}
                        }

                        // --- 2. ЧАСОВАЯ СТРЕЛКА ---
                        rotate(finalHourAngle, center) {
                            val handWidth = if (analogStyle == AnalogStyle.CHRONO) 5.dp.toPx() else 3.5.dp.toPx()
                            drawLine(
                                color = hourHandColor,
                                start = center,
                                end = Offset(center.x, center.y - radius * 0.44f),
                                strokeWidth = handWidth,
                                cap = StrokeCap.Round
                            )
                        }

                        // --- 3. МИНУТНАЯ СТРЕЛКА ---
                        rotate(animMinuteAngle.value, center) {
                            drawLine(
                                color = minuteHandColor,
                                start = center,
                                end = Offset(center.x, center.y - radius * 0.70f),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // --- 4. СЕКУНДНАЯ СТРЕЛКА (МЕХАНИЧЕСКИЙ РЕЗКИЙ ТИК С ОТСКОКОМ) ---
                        rotate(animSecondAngle.value, center) {
                            when (analogStyle) {
                                AnalogStyle.CHRONO -> {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(center.x, center.y + radius * 0.22f),
                                        end = Offset(center.x, center.y - radius * 0.85f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = accentColor,
                                        radius = 5.dp.toPx(),
                                        center = Offset(center.x, center.y + radius * 0.16f)
                                    )
                                    drawCircle(
                                        color = clockFaceBg,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(center.x, center.y + radius * 0.16f)
                                    )
                                }
                                AnalogStyle.ULTRA_MINIMAL -> {
                                    drawCircle(
                                        color = accentColor,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(center.x, center.y - radius * 0.82f)
                                    )
                                }
                                else -> {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(center.x, center.y + radius * 0.14f),
                                        end = Offset(center.x, center.y - radius * 0.82f),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            drawCircle(color = accentColor, radius = 4.dp.toPx(), center = center)
                            drawCircle(color = clockFaceBg, radius = 1.8.dp.toPx(), center = center)
                        }

                        // --- 5. ТОЧКА-ПОЛЗУНОК ЖЕСТА ---
                        val handleAngleRad = Math.toRadians((finalHourAngle - 90f).toDouble())
                        val handleRadius = radius - 4.dp.toPx()
                        val handleCenter = Offset(
                            x = center.x + (handleRadius * cos(handleAngleRad)).toFloat(),
                            y = center.y + (handleRadius * sin(handleAngleRad)).toFloat()
                        )

                        drawCircle(
                            color = accentColor.copy(alpha = if (isDragging) 0.45f else 0.2f),
                            radius = if (isDragging) 12.dp.toPx() else 8.dp.toPx(),
                            center = handleCenter
                        )
                        drawCircle(
                            color = accentColor,
                            radius = if (isDragging) 5.5.dp.toPx() else 4.5.dp.toPx(),
                            center = handleCenter
                        )
                    }

                    Surface(
                        onClick = {
                            isDigitalMode = true
                            settingsManager.isDigitalClockMode = true
                            triggerHaptic(context)
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(clockSize)
                    ) {}
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

// ============================================================================
// СЕКЦИЯ 4: ТЕКСТОВЫЙ ИНФОРМЕР ДАТЫ И ВРЕМЕНИ
// ============================================================================
        val dateFormatted = effectiveDate.format(DateTimeFormatter.ofPattern("d MMMM", RU_LOCALE))
        val realTimeFormatted = if (settingsManager.is24HourFormat) {
            effectiveTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            effectiveTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
        }

        val displayTimeText = if (isFlickering) flickerText else realTimeFormatted

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = flickerAlpha }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$dateFormatted, $displayTimeText",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (shiftHoursCount != 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val shiftText = if (shiftHoursCount > 0) "+$shiftHoursCount ч" else "$shiftHoursCount ч"
                    Text(
                        text = "($shiftText)",
                        color = accentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun triggerHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    } catch (_: Exception) {}
}

// ============================================================================
// COMPOSE PREVIEWS (АНАЛОГОВЫЕ ЧАСЫ)
// ============================================================================

@Preview(name = "Analog Clock - Oxygen (Тёмная тема)", showBackground = true, backgroundColor = 0xFF0B0D13)
@Composable
private fun SmoothAnalogClockPreview_Oxygen() {
    PureClockTheme(
        themeMode = "DARK",
        isPureMonocolor = true,
        accentColor = Color(0xFFFF5252),
        cardCornerRadiusDp = 20,
        is3dEnabled = true,
        isGlowEnabled = true,
        depthIntensityDp = 8
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            SmoothAnalogClock(
                analogStyle = AnalogStyle.OXYGEN,
                clockSize = 250.dp,
                // Здесь можно сразу тестировать разные значения скорости и отскока:
                tickDurationMillis = 250,
                overshootDegrees = 1.5f,
                forwardTimeRatio = 0.65f
            )
        }
    }
}

@Preview(name = "Analog Clock - Chrono Sport (Светлая тема)", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun SmoothAnalogClockPreview_Chrono() {
    PureClockTheme(
        themeMode = "LIGHT",
        isPureMonocolor = false,
        accentColor = Color(0xFF00C853),
        cardCornerRadiusDp = 18,
        is3dEnabled = true,
        isGlowEnabled = false,
        depthIntensityDp = 6
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            SmoothAnalogClock(
                analogStyle = AnalogStyle.CHRONO,
                clockSize = 250.dp,
                tickDurationMillis = 220,
                overshootDegrees = 1.8f,
                forwardTimeRatio = 0.60f
            )
        }
    }
}