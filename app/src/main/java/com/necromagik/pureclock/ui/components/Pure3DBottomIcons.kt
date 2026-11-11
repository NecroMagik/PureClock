package com.necromagik.pureclock.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.PureClockTheme
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class BottomBarTab {
    ALARM, WORLD_CLOCK, TIMER, STOPWATCH, SCENARY, SETTINGS
}

@Composable
fun Pure3DIcon(
    tab: BottomBarTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    activeColor: Color = LocalPureClockConfig.current.accentColor,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.5f)
) {
    val themeConfig = LocalPureClockConfig.current
    val isGlow = themeConfig.isGlowEnabled
    val is3D = themeConfig.is3dEnabled
    val isInspection = LocalInspectionMode.current

    // Общий прогресс перехода между неактивным и активным состоянием
    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "SelectionProgress"
    )

    // Триггеры для разделения первого входа и повторного тапа
    var tapCounter by remember { mutableIntStateOf(0) }
    var wasSelectedBefore by remember { mutableStateOf(isSelected) }

    // Анимация первого входа на вкладку (Entrance Trigger)
    val entranceAnim = remember { Animatable(if (isSelected) 1f else 0f) }

    // Анимация повторного нажатия на уже открытую вкладку (Repeat Tap Trigger)
    val repeatTapAnim = remember { Animatable(0f) }

    // Состояния для часов (реальное время vs случайное)
    var isRandomTimeMode by remember { mutableStateOf(false) }
    val hourAngleAnim = remember { Animatable(0f) }
    val minuteAngleAnim = remember { Animatable(0f) }

    // Состояние для поочерёдного нажатия кнопок секундомера (0 = старт, 1 = отсечка)
    var stopwatchButtonToggle by remember { mutableIntStateOf(0) }

    // Состояние 2D поворота таймера при повторном тапе
    var timer2DFlipCount by remember { mutableIntStateOf(0) }
    val timer2DAngleAnim = remember { Animatable(0f) }

    // Расчёт реального времени для часов
    fun getRealTimeAngles(): Pair<Float, Float> {
        val now = LocalTime.now()
        val h = (now.hour % 12 + now.minute / 60f) * 30f
        val m = (now.minute + now.second / 60f) * 6f
        return h to m
    }

    LaunchedEffect(isSelected) {
        if (isSelected && !wasSelectedBefore) {
            // ПЕРЕХОД НА ВКЛАДКУ
            isRandomTimeMode = false
            if (tab == BottomBarTab.WORLD_CLOCK) {
                val (realH, realM) = getRealTimeAngles()
                hourAngleAnim.snapTo(realH)
                minuteAngleAnim.snapTo(realM)
            }
            entranceAnim.snapTo(0f)
            entranceAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(550, easing = FastOutSlowInEasing)
            )
        }
        wasSelectedBefore = isSelected
    }

    val currentColor = if (isSelected) activeColor else inactiveColor

    Box(
        modifier = modifier
            .size(size + 14.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isSelected) {
                    // ПОВТОРНОЕ НАЖАТИЕ НА АКТИВНУЮ ВКЛАДКУ
                    tapCounter++
                    when (tab) {
                        BottomBarTab.WORLD_CLOCK -> {
                            isRandomTimeMode = true
                        }
                        BottomBarTab.STOPWATCH -> {
                            stopwatchButtonToggle = (stopwatchButtonToggle + 1) % 2
                        }
                        BottomBarTab.TIMER -> {
                            timer2DFlipCount++
                        }
                        else -> {}
                    }
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Запуск анимаций повторного тапа в корутине LaunchedEffect
        LaunchedEffect(tapCounter) {
            if (tapCounter > 0) {
                repeatTapAnim.snapTo(0f)
                when (tab) {
                    BottomBarTab.ALARM -> {
                        repeatTapAnim.animateTo(
                            1f,
                            animationSpec = tween(400, easing = LinearEasing)
                        )
                    }
                    BottomBarTab.WORLD_CLOCK -> {
                        val randH = Random.nextInt(0, 12) * 30f + Random.nextInt(0, 4) * 7.5f
                        val randM = Random.nextInt(0, 60) * 6f
                        val spec = spring<Float>(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                        launch { hourAngleAnim.animateTo(randH, spec) }
                        launch { minuteAngleAnim.animateTo(randM, spec) }
                    }
                    BottomBarTab.TIMER -> {
                        timer2DAngleAnim.animateTo(
                            targetValue = timer2DFlipCount * 180f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        )
                    }
                    else -> {
                        repeatTapAnim.animateTo(
                            1f,
                            animationSpec = tween(450, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
        }

        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)

            when (tab) {
                // ============================================================
                // 1. БУДИЛЬНИК (ЗВОН ПРИ ВХОДЕ, ТРЯСКА ВЛЕВО-ВПРАВО НА ПОВТОР)
                // ============================================================
                BottomBarTab.ALARM -> {
                    val isRepeat = tapCounter > 0 && repeatTapAnim.value in 0.01f..0.99f
                    val entranceProgress = entranceAnim.value

                    val shakeAngle = if (isRepeat) {
                        val phase = sin(repeatTapAnim.value * Math.PI * 6).toFloat()
                        val decay = (1f - repeatTapAnim.value)
                        phase * decay * 9f
                    } else if (isSelected && entranceProgress < 0.95f) {
                        sin(entranceProgress * Math.PI * 8).toFloat() * (1f - entranceProgress) * 7f
                    } else 0f

                    rotate(shakeAngle, pivot = center) {
                        val bodyRadius = w * 0.38f

                        if (!isRepeat && isSelected && entranceProgress in 0.05f..0.95f) {
                            val waveAlpha = (1f - entranceProgress) * 0.7f
                            val waveDist = bodyRadius + (entranceProgress * 7.dp.toPx())

                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha),
                                startAngle = 145f,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = Offset(center.x - waveDist, center.y - waveDist),
                                size = Size(waveDist * 2, waveDist * 2),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha),
                                startAngle = -35f,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = Offset(center.x - waveDist, center.y - waveDist),
                                size = Size(waveDist * 2, waveDist * 2),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Ножки
                        val legLen = h * 0.14f
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x - bodyRadius * 0.65f, center.y + bodyRadius * 0.70f),
                            end = Offset(center.x - bodyRadius * 0.65f - legLen * 0.45f, center.y + bodyRadius * 0.70f + legLen),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x + bodyRadius * 0.65f, center.y + bodyRadius * 0.70f),
                            end = Offset(center.x + bodyRadius * 0.65f + legLen * 0.45f, center.y + bodyRadius * 0.70f + legLen),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Колокольчики
                        val bellW = w * 0.28f
                        val bellH = h * 0.15f
                        rotate(-35f, pivot = Offset(center.x - bodyRadius * 0.68f, center.y - bodyRadius * 0.68f)) {
                            drawArc(
                                color = currentColor,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true,
                                topLeft = Offset(center.x - bodyRadius * 0.68f - bellW / 2, center.y - bodyRadius * 0.68f - bellH / 2),
                                size = Size(bellW, bellH)
                            )
                        }
                        rotate(35f, pivot = Offset(center.x + bodyRadius * 0.68f, center.y - bodyRadius * 0.68f)) {
                            drawArc(
                                color = currentColor,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true,
                                topLeft = Offset(center.x + bodyRadius * 0.68f - bellW / 2, center.y - bodyRadius * 0.68f - bellH / 2),
                                size = Size(bellW, bellH)
                            )
                        }

                        // Кнопка сверху
                        drawLine(
                            currentColor,
                            Offset(center.x, center.y - bodyRadius),
                            Offset(center.x, center.y - bodyRadius - h * 0.08f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Корпус и 3D-тень
                        if (is3D) {
                            drawCircle(Color.Black.copy(alpha = 0.35f), bodyRadius, Offset(center.x, center.y + 1.8.dp.toPx()))
                        }
                        drawCircle(currentColor, bodyRadius, center, style = Stroke(width = 2.4.dp.toPx()))

                        // Стрелки будильника
                        drawLine(currentColor, center, Offset(center.x - bodyRadius * 0.45f, center.y - bodyRadius * 0.25f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(currentColor, center, Offset(center.x + bodyRadius * 0.48f, center.y - bodyRadius * 0.38f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(currentColor, 2.6.dp.toPx(), center)
                    }
                }

                // ============================================================
                // 2. ЧАСЫ (РЕАЛЬНОЕ ВРЕМЯ VS РАНДОМНОЕ ПРИ ПОВТОРЕ)
                // ============================================================
                BottomBarTab.WORLD_CLOCK -> {
                    val dialRadius = w * 0.44f

                    if (is3D) {
                        drawCircle(Color.Black.copy(alpha = 0.35f), dialRadius, Offset(center.x, center.y + 1.8.dp.toPx()))
                    }
                    drawCircle(currentColor, dialRadius, center, style = Stroke(width = 2.4.dp.toPx()))

                    if (isGlow && isSelected) {
                        drawCircle(currentColor.copy(alpha = 0.25f), dialRadius + 2.dp.toPx(), center, style = Stroke(width = 1.8.dp.toPx()))
                    }

                    val (currentHAngle, currentMAngle) = if (isRandomTimeMode) {
                        hourAngleAnim.value to minuteAngleAnim.value
                    } else if (isInspection) {
                        300f to 60f
                    } else {
                        getRealTimeAngles()
                    }

                    rotate(currentHAngle, pivot = center) {
                        drawLine(currentColor, center, Offset(center.x, center.y - dialRadius * 0.52f), strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    }

                    rotate(currentMAngle, pivot = center) {
                        drawLine(currentColor, center, Offset(center.x, center.y - dialRadius * 0.76f), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    }

                    drawCircle(currentColor, 2.8.dp.toPx(), center)
                }

                // ============================================================
                // 3. ТАЙМЕР (ПРОСТРАНСТВЕННЫЙ 3D FLIP VS 2D ПЕРЕВОРОТ 180°)
                // ============================================================
                BottomBarTab.TIMER -> {
                    val glassH = h * 0.44f
                    val glassW = w * 0.35f

                    val flipYProgress = if (tapCounter == 0) entranceAnim.value else 1f
                    val scaleXFactor = if (isSelected && flipYProgress < 1f) {
                        cos(flipYProgress * Math.PI).toFloat()
                    } else 1f

                    val zRotation = timer2DAngleAnim.value

                    scale(scaleX = scaleXFactor, scaleY = 1f, pivot = center) {
                        rotate(zRotation, pivot = center) {
                            val hourglassPath = Path().apply {
                                moveTo(center.x - glassW, center.y - glassH)
                                lineTo(center.x + glassW, center.y - glassH)
                                cubicTo(center.x + glassW * 0.45f, center.y - glassH * 0.25f, center.x + glassW * 0.12f, center.y - glassH * 0.05f, center.x + glassW * 0.08f, center.y)
                                cubicTo(center.x + glassW * 0.12f, center.y + glassH * 0.05f, center.x + glassW * 0.45f, center.y + glassH * 0.25f, center.x + glassW, center.y + glassH)
                                lineTo(center.x - glassW, center.y + glassH)
                                cubicTo(center.x - glassW * 0.45f, center.y + glassH * 0.25f, center.x - glassW * 0.12f, center.y + glassH * 0.05f, center.x - glassW * 0.08f, center.y)
                                cubicTo(center.x - glassW * 0.12f, center.y - glassH * 0.05f, center.x - glassW * 0.45f, center.y - glassH * 0.25f, center.x - glassW, center.y - glassH)
                                close()
                            }

                            if (is3D) {
                                drawPath(hourglassPath, color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = 2.2.dp.toPx()))
                            }
                            drawPath(hourglassPath, color = currentColor, style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round))

                            drawLine(currentColor, Offset(center.x - glassW - 1.dp.toPx(), center.y - glassH), Offset(center.x + glassW + 1.dp.toPx(), center.y - glassH), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(currentColor, Offset(center.x - glassW - 1.dp.toPx(), center.y + glassH), Offset(center.x + glassW + 1.dp.toPx(), center.y + glassH), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)

                            val sandProgress = if (tapCounter > 0) ((zRotation / 180f) % 1f) else selectionProgress
                            val topFactor = (1f - sandProgress * 0.5f).coerceIn(0.4f, 1f)

                            val topSandPath = Path().apply {
                                moveTo(center.x - glassW * topFactor * 0.65f, center.y - glassH * topFactor * 0.65f)
                                lineTo(center.x + glassW * topFactor * 0.65f, center.y - glassH * topFactor * 0.65f)
                                lineTo(center.x + glassW * 0.08f, center.y)
                                lineTo(center.x - glassW * 0.08f, center.y)
                                close()
                            }
                            drawPath(topSandPath, color = currentColor.copy(alpha = 0.75f), style = Fill)

                            val bottomFactor = (0.4f + sandProgress * 0.5f).coerceIn(0.4f, 0.95f)
                            val bottomSandPath = Path().apply {
                                moveTo(center.x - glassW * 0.82f, center.y + glassH * 0.90f)
                                lineTo(center.x + glassW * 0.82f, center.y + glassH * 0.90f)
                                lineTo(center.x + glassW * 0.35f * bottomFactor, center.y + glassH * (0.90f - 0.55f * bottomFactor))
                                lineTo(center.x - glassW * 0.35f * bottomFactor, center.y + glassH * (0.90f - 0.55f * bottomFactor))
                                close()
                            }
                            drawPath(bottomSandPath, color = currentColor, style = Fill)
                        }
                    }
                }

                // ============================================================
                // 4. СЕКУНДОМЕР (ПООЧЕРЕДНОЕ УТАПЛИВАНИЕ КНОПОК)
                // ============================================================
                BottomBarTab.STOPWATCH -> {
                    val isSecondButton = stopwatchButtonToggle == 1
                    val pressDepth = if (tapCounter > 0 && repeatTapAnim.value in 0.05f..0.85f) {
                        (1f - repeatTapAnim.value) * 1.8.dp.toPx()
                    } else 0f

                    val topBtnOffset = if (!isSecondButton) pressDepth else 0f
                    val sideBtnOffset = if (isSecondButton) pressDepth else 0f

                    val handAngle = (selectionProgress * 45f) + (tapCounter * 45f)

                    if (is3D) {
                        drawCircle(Color.Black.copy(alpha = 0.35f), w * 0.40f, Offset(center.x, center.y + h * 0.05f + 1.8.dp.toPx()))
                    }
                    drawCircle(currentColor, w * 0.40f, Offset(center.x, center.y + h * 0.05f), style = Stroke(2.3.dp.toPx()))

                    // 1. Верхняя кнопка Старт/Стоп
                    drawLine(
                        color = currentColor,
                        start = Offset(center.x, center.y - h * 0.33f + topBtnOffset),
                        end = Offset(center.x, center.y - h * 0.45f + topBtnOffset),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // 2. Боковая кнопка отсечки круга Lap
                    rotate(-38f, pivot = Offset(center.x, center.y + h * 0.05f)) {
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x, center.y - h * 0.33f + sideBtnOffset),
                            end = Offset(center.x, center.y - h * 0.42f + sideBtnOffset),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    rotate(handAngle, pivot = Offset(center.x, center.y + h * 0.05f)) {
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x, center.y + h * 0.05f),
                            end = Offset(center.x, center.y - h * 0.28f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(currentColor, 2.6.dp.toPx(), Offset(center.x, center.y + h * 0.05f))
                }

                // ============================================================
                // 5. СЦЕНАРИИ (ВЫЧЕРЧИВАНИЕ СТРОК + 3D ПИРУЭТ / 2D ПОВОРОТ)
                // ============================================================
                BottomBarTab.SCENARY -> {
                    val docLeft = w * 0.12f
                    val docTop = h * 0.12f
                    val docRight = w * 0.80f
                    val docBottom = h * 0.90f
                    val corner = w * 0.16f

                    val badgeCenter = Offset(w * 0.78f, h * 0.22f)
                    val cutoutRadius = w * 0.24f

                    val docPath = Path().apply {
                        addRoundRect(RoundRect(Rect(docLeft, docTop, docRight, docBottom), CornerRadius(corner, corner)))
                    }
                    val cutoutPath = Path().apply {
                        addOval(Rect(center = badgeCenter, radius = cutoutRadius))
                    }
                    val finalDocPath = Path.combine(PathOperation.Difference, docPath, cutoutPath)

                    if (is3D) {
                        val shadowPath = Path().apply { addPath(finalDocPath, Offset(0f, 1.8.dp.toPx())) }
                        drawPath(shadowPath, color = Color.Black.copy(alpha = 0.35f), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    if (isGlow && isSelected) {
                        drawPath(finalDocPath, color = currentColor.copy(alpha = 0.18f), style = Fill)
                    }
                    drawPath(finalDocPath, color = currentColor, style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val lineReveal = if (tapCounter == 0) entranceAnim.value else 1f
                    val lineStart = w * 0.26f
                    val lineShortEnd = w * 0.48f
                    val lineFullEnd = w * 0.65f
                    val lineStroke = 2.dp.toPx()

                    val p1 = (lineReveal / 0.5f).coerceIn(0f, 1f)
                    val p2 = ((lineReveal - 0.25f) / 0.5f).coerceIn(0f, 1f)
                    val p3 = ((lineReveal - 0.5f) / 0.5f).coerceIn(0f, 1f)

                    if (p1 > 0.05f) {
                        drawLine(currentColor, Offset(lineStart, h * 0.38f), Offset(lineStart + (lineShortEnd - lineStart) * p1, h * 0.38f), lineStroke, StrokeCap.Round)
                    }
                    if (p2 > 0.05f) {
                        drawLine(currentColor, Offset(lineStart, h * 0.56f), Offset(lineStart + (lineFullEnd - lineStart) * p2, h * 0.56f), lineStroke, StrokeCap.Round)
                    }
                    if (p3 > 0.05f) {
                        drawLine(currentColor, Offset(lineStart, h * 0.74f), Offset(lineStart + (lineFullEnd - lineStart) * p3, h * 0.74f), lineStroke, StrokeCap.Round)
                    }

                    val isFirstEntrance = tapCounter == 0 && entranceAnim.value < 0.99f
                    val pirouetteScaleX = if (isFirstEntrance) cos(entranceAnim.value * Math.PI * 2).toFloat() else 1f
                    val plusRotation2D = if (tapCounter > 0) (tapCounter * 90f) else (selectionProgress * 45f)

                    val arm = w * 0.13f
                    scale(scaleX = pirouetteScaleX, scaleY = 1f, pivot = badgeCenter) {
                        rotate(plusRotation2D, pivot = badgeCenter) {
                            drawLine(currentColor, Offset(badgeCenter.x - arm, badgeCenter.y), Offset(badgeCenter.x + arm, badgeCenter.y), 2.1.dp.toPx(), StrokeCap.Round)
                            drawLine(currentColor, Offset(badgeCenter.x, badgeCenter.y - arm), Offset(badgeCenter.x, badgeCenter.y + arm), 2.1.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }

                // ============================================================
                // 6. НАСТРОЙКИ (МЕХАНИЧЕСКИЙ ЩЕЛЧОК ШЕСТЕРНИ НА ШАГ ЗУБА 60°)
                // ============================================================
                BottomBarTab.SETTINGS -> {
                    val gearAngle = (selectionProgress * 60f) + (tapCounter * 60f)

                    rotate(gearAngle, pivot = center) {
                        val teethCount = 6
                        val outerR = w * 0.44f
                        val innerR = w * 0.32f

                        for (i in 0 until teethCount) {
                            val angle = i * (360f / teethCount)
                            rotate(angle, pivot = center) {
                                drawLine(
                                    currentColor,
                                    Offset(center.x, center.y - innerR),
                                    Offset(center.x, center.y - outerR),
                                    strokeWidth = 3.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        drawCircle(currentColor, innerR, center, style = Stroke(2.2.dp.toPx()))
                        drawCircle(currentColor, innerR * 0.38f, center, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

// ============================================================================
// COMPOSE PREVIEWS (ИНТЕРАКТИВНОЕ ТЕСТИРОВАНИЕ ИКОНОК)
// ============================================================================

@Preview(name = "Bottom Icons - Dark (Interactive)", showBackground = true, backgroundColor = 0xFF0B0D13)
@Composable
private fun Pure3DBottomIconsPreview_Dark() {
    PureClockTheme(themeMode = "DARK", accentColor = Color(0xFF00E676)) {
        var selectedTab by remember { mutableStateOf(BottomBarTab.ALARM) }

        Surface(
            color = Color(0xFF141720),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Нажмите на активную иконку повторно для проверки спец-эффектов",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomBarTab.entries.forEach { tab ->
                        Pure3DIcon(
                            tab = tab,
                            isSelected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            size = 30.dp
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Bottom Icons - Light (Chrono Accent)", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun Pure3DBottomIconsPreview_Light() {
    PureClockTheme(themeMode = "LIGHT", accentColor = Color(0xFFFF5252)) {
        var selectedTab by remember { mutableStateOf(BottomBarTab.TIMER) }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarTab.entries.forEach { tab ->
                    Pure3DIcon(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        size = 30.dp
                    )
                }
            }
        }
    }
}