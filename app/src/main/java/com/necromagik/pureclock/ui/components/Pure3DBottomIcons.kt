package com.necromagik.pureclock.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import kotlin.math.sin
import kotlin.random.Random

enum class BottomBarTab {
    ALARM, WORLD_CLOCK, TIMER, STOPWATCH, SETTINGS
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

    // 1. Единый сглаженный контроллер состояния выбранной вкладки (0f -> 1f)
    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "SelectionProgress"
    )

    // 2. Однократный импульс для анимаций при нажатии (Запускается строго 1 раз на тап!)
    var tapTrigger by remember { mutableIntStateOf(0) }
    val tapAnim = remember { Animatable(0f) }

    // Углы для рандомного времени в часах
    var randomHourAngle by remember { mutableFloatStateOf(40f) }
    var randomMinAngle by remember { mutableFloatStateOf(160f) }
    var randomSecAngle by remember { mutableFloatStateOf(280f) }

    LaunchedEffect(isSelected, tapTrigger) {
        tapAnim.snapTo(0f)
        if (tab == BottomBarTab.WORLD_CLOCK && tapTrigger > 0) {
            randomHourAngle += Random.nextFloat() * 360f + 120f
            randomMinAngle += Random.nextFloat() * 720f + 360f
            randomSecAngle += Random.nextFloat() * 1080f + 540f
        }
        tapAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val currentColor = if (isSelected) activeColor else inactiveColor

    Box(
        modifier = modifier
            .size(size + 14.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tapTrigger++
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)

            when (tab) {
                // ============================================================
                // 1. БУДИЛЬНИК: ВЫРАЖЕННАЯ ТРЯСКА + МОЩНЫЕ WI-FI ВОЛНЫ СЛЕВА/СПРАВА
                // ============================================================
                BottomBarTab.ALARM -> {
                    // Размашистая вибрация корпуса
                    val shakePhase = (1f - tapAnim.value) * sin(tapAnim.value * Math.PI * 8).toFloat()
                    val totalAngle = shakePhase * 24f

                    rotate(totalAngle, pivot = center) {
                        val bodyRadius = w * 0.38f

                        // --- ЯРКИЕ WI-FI ВОЛНЫ СИГНАЛА СЛЕВА И СПРАВА ---
                        if (isSelected) {
                            val waveProgress = tapAnim.value
                            val waveAlpha = (1f - waveProgress).coerceIn(0f, 1f)

                            // Волны слева
                            val leftRadius1 = bodyRadius + (waveProgress * 10.dp.toPx())
                            val leftRadius2 = bodyRadius + (waveProgress * 16.dp.toPx())
                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha * 0.8f),
                                startAngle = 135f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(center.x - leftRadius1, center.y - leftRadius1),
                                size = Size(leftRadius1 * 2, leftRadius1 * 2),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha * 0.4f),
                                startAngle = 135f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(center.x - leftRadius2, center.y - leftRadius2),
                                size = Size(leftRadius2 * 2, leftRadius2 * 2),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Волны справа
                            val rightRadius1 = bodyRadius + (waveProgress * 10.dp.toPx())
                            val rightRadius2 = bodyRadius + (waveProgress * 16.dp.toPx())
                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha * 0.8f),
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(center.x - rightRadius1, center.y - rightRadius1),
                                size = Size(rightRadius1 * 2, rightRadius1 * 2),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = currentColor.copy(alpha = waveAlpha * 0.4f),
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(center.x - rightRadius2, center.y - rightRadius2),
                                size = Size(rightRadius2 * 2, rightRadius2 * 2),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Ножки
                        val legLen = h * 0.14f
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x - bodyRadius * 0.65f, center.y + bodyRadius * 0.7f),
                            end = Offset(center.x - bodyRadius * 0.65f - legLen * 0.45f, center.y + bodyRadius * 0.7f + legLen),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = currentColor,
                            start = Offset(center.x + bodyRadius * 0.65f, center.y + bodyRadius * 0.7f),
                            end = Offset(center.x + bodyRadius * 0.65f + legLen * 0.45f, center.y + bodyRadius * 0.7f + legLen),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Звонки (полусферы)
                        val bellW = w * 0.30f
                        val bellH = h * 0.16f

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

                        // Молоточек
                        drawLine(
                            currentColor,
                            Offset(center.x, center.y - bodyRadius),
                            Offset(center.x, center.y - bodyRadius - h * 0.09f),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Корпус
                        if (is3D) {
                            drawCircle(Color.Black.copy(alpha = 0.35f), bodyRadius, Offset(center.x, center.y + 2.dp.toPx()))
                        }
                        drawCircle(currentColor, bodyRadius, center, style = Stroke(width = 2.6.dp.toPx()))

                        // Стрелки (10:10)
                        drawLine(currentColor, center, Offset(center.x - bodyRadius * 0.45f, center.y - bodyRadius * 0.35f), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(currentColor, center, Offset(center.x + bodyRadius * 0.52f, center.y - bodyRadius * 0.42f), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(currentColor, 3.dp.toPx(), center)
                    }
                }

                // ============================================================
                // 2. ЧАСЫ: ЧИСТЫЙ ЦИФЕРБЛАТ БЕЗ МЕСИВА ЗАСЕЧЕК + КУРСОРНЫЕ СТРЕЛКИ
                // ============================================================
                BottomBarTab.WORLD_CLOCK -> {
                    val dialRadius = w * 0.44f

                    if (is3D) {
                        drawCircle(Color.Black.copy(alpha = 0.35f), dialRadius, Offset(center.x, center.y + 2.dp.toPx()))
                    }
                    drawCircle(currentColor, dialRadius, center, style = Stroke(width = 2.6.dp.toPx()))

                    if (isGlow && isSelected) {
                        drawCircle(currentColor.copy(alpha = 0.35f), dialRadius + 2.5.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
                    }

                    val baseHourAngle = selectionProgress * 360f
                    val baseMinAngle = selectionProgress * 720f
                    val baseSecAngle = selectionProgress * 1440f

                    val currentHour = baseHourAngle + (randomHourAngle * tapAnim.value)
                    val currentMin = baseMinAngle + (randomMinAngle * tapAnim.value)
                    val currentSec = baseSecAngle + (randomSecAngle * tapAnim.value)

                    // Часовая стрелка
                    rotate(currentHour, pivot = center) {
                        drawLine(currentColor, center, Offset(center.x, center.y - dialRadius * 0.52f), strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    }

                    // Минутная стрелка
                    rotate(currentMin, pivot = center) {
                        drawLine(currentColor, center, Offset(center.x, center.y - dialRadius * 0.78f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    }

                    // Секундная стрелка
                    rotate(currentSec, pivot = center) {
                        drawLine(currentColor.copy(alpha = 0.9f), Offset(center.x, center.y + dialRadius * 0.18f), Offset(center.x, center.y - dialRadius * 0.88f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }

                    drawCircle(currentColor, 3.5.dp.toPx(), center)
                }

                // ============================================================
                // 3. ТАЙМЕР: КИНЕМАТИЧЕСКАЯ ОДНОКРАТНАЯ АНИМАЦИЯ ПЕСКА (БЕЗ БЕСКОНЕЧНОГО ЦИКЛА)
                // ============================================================
                BottomBarTab.TIMER -> {
                    // 3D-Переворот часов строго на 180 градусов
                    val flipRotation = selectionProgress * 180f + (tapAnim.value * 180f)

                    rotate(flipRotation, pivot = center) {
                        val glassH = h * 0.44f
                        val glassW = w * 0.36f

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
                            drawPath(hourglassPath, color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = 2.4.dp.toPx()))
                        }
                        drawPath(hourglassPath, color = currentColor, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

                        // Опорные планки
                        drawLine(currentColor, Offset(center.x - glassW - 1.5.dp.toPx(), center.y - glassH), Offset(center.x + glassW + 1.5.dp.toPx(), center.y - glassH), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(currentColor, Offset(center.x - glassW - 1.5.dp.toPx(), center.y + glassH), Offset(center.x + glassW + 1.5.dp.toPx(), center.y + glassH), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)

                        // --- ФИЗИКА ПЕСКА (Анимируется строго при tapAnim/selectionProgress, затем замирает!) ---
                        val flowProgress = if (tapAnim.value > 0f && tapAnim.value < 1f) tapAnim.value else selectionProgress

                        // Upper Sand Mass
                        val topSandFactor = (1f - flowProgress * 0.8f).coerceIn(0.2f, 1f)
                        val topSandPath = Path().apply {
                            moveTo(center.x - glassW * topSandFactor * 0.7f, center.y - glassH * topSandFactor * 0.7f)
                            lineTo(center.x + glassW * topSandFactor * 0.7f, center.y - glassH * topSandFactor * 0.7f)
                            lineTo(center.x + glassW * 0.08f, center.y)
                            lineTo(center.x - glassW * 0.08f, center.y)
                            close()
                        }
                        drawPath(topSandPath, color = currentColor.copy(alpha = 0.75f), style = Fill)

                        // Lower Sand Mass
                        val bottomSandFactor = (0.2f + (flowProgress * 0.75f)).coerceIn(0.2f, 0.95f)
                        val bottomSandPath = Path().apply {
                            moveTo(center.x - glassW * 0.85f, center.y + glassH * 0.92f)
                            lineTo(center.x + glassW * 0.85f, center.y + glassH * 0.92f)
                            lineTo(center.x + glassW * 0.4f * bottomSandFactor, center.y + glassH * (0.92f - 0.65f * bottomSandFactor))
                            lineTo(center.x - glassW * 0.4f * bottomSandFactor, center.y + glassH * (0.92f - 0.65f * bottomSandFactor))
                            close()
                        }
                        drawPath(bottomSandPath, color = currentColor, style = Fill)

                        // Струйка видна ТОЛЬКО во время анимации пересыпания
                        if (tapAnim.value in 0.01f..0.98f) {
                            drawLine(
                                color = currentColor,
                                start = Offset(center.x, center.y),
                                end = Offset(center.x, center.y + glassH * 0.75f),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // ============================================================
                // 4. СЕКУНДОМЕР И 5. НАСТРОЙКИ
                // ============================================================
                BottomBarTab.STOPWATCH -> {
                    val handAngle = selectionProgress * 360f
                    val buttonPress = if (isSelected) 2.dp.toPx() * (1f - tapAnim.value) else 0f

                    drawCircle(currentColor, w * 0.40f, Offset(center.x, center.y + h * 0.05f), style = Stroke(2.4.dp.toPx()))
                    drawLine(currentColor, Offset(center.x, center.y - h * 0.33f + buttonPress), Offset(center.x, center.y - h * 0.44f + buttonPress), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

                    rotate(handAngle, pivot = Offset(center.x, center.y + h * 0.05f)) {
                        drawLine(currentColor, Offset(center.x, center.y + h * 0.05f), Offset(center.x, center.y - h * 0.26f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    }
                    drawCircle(currentColor, 2.8.dp.toPx(), Offset(center.x, center.y + h * 0.05f))
                }

                BottomBarTab.SETTINGS -> {
                    val gearRotation = selectionProgress * 90f + (tapAnim.value * 180f)

                    rotate(gearRotation, pivot = center) {
                        val teethCount = 6
                        val outerR = w * 0.44f
                        val innerR = w * 0.32f

                        for (i in 0 until teethCount) {
                            val angle = i * (360f / teethCount)
                            rotate(angle, pivot = center) {
                                drawLine(currentColor, Offset(center.x, center.y - innerR), Offset(center.x, center.y - outerR), strokeWidth = 3.8.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        drawCircle(currentColor, innerR, center, style = Stroke(2.2.dp.toPx()))
                        drawCircle(currentColor, innerR * 0.4f, center, style = Stroke(1.6.dp.toPx()))
                    }
                }
            }
        }
    }
}