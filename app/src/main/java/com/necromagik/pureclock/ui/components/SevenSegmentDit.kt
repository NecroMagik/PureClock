package com.necromagik.pureclock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig

private val SEGMENT_MAP = mapOf(
    '0' to booleanArrayOf(true, true, true, true, true, true, false),
    '1' to booleanArrayOf(false, true, true, false, false, false, false),
    '2' to booleanArrayOf(true, true, false, true, true, false, true),
    '3' to booleanArrayOf(true, true, true, true, false, false, true),
    '4' to booleanArrayOf(false, true, true, false, false, true, true),
    '5' to booleanArrayOf(true, false, true, true, false, true, true),
    '6' to booleanArrayOf(true, false, true, true, true, true, true),
    '7' to booleanArrayOf(true, true, true, false, false, false, false),
    '8' to booleanArrayOf(true, true, true, true, true, true, true),
    '9' to booleanArrayOf(true, true, true, true, false, true, true)
)

// ============================================================================
// СЕКЦИЯ 1: 3D СЕГМЕНТНАЯ ЦИФРА С НАСТРАИВАЕМЫМ СВЕЧЕНИЕМ И ГЛУБИНОЙ
// ============================================================================
@Composable
fun SevenSegmentDigit(
    digit: Char,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
) {
    val themeConfig = LocalPureClockConfig.current
    val isGlow = themeConfig.isGlowEnabled
    val is3D = themeConfig.is3dEnabled

    val activeStates = SEGMENT_MAP[digit] ?: booleanArrayOf(false, false, false, false, false, false, false)
    val spec = tween<Color>(durationMillis = 180, easing = FastOutSlowInEasing)

    val colorA by animateColorAsState(if (activeStates[0]) activeColor else inactiveColor, animationSpec = spec, label = "SegA")
    val colorB by animateColorAsState(if (activeStates[1]) activeColor else inactiveColor, animationSpec = spec, label = "SegB")
    val colorC by animateColorAsState(if (activeStates[2]) activeColor else inactiveColor, animationSpec = spec, label = "SegC")
    val colorD by animateColorAsState(if (activeStates[3]) activeColor else inactiveColor, animationSpec = spec, label = "SegD")
    val colorE by animateColorAsState(if (activeStates[4]) activeColor else inactiveColor, animationSpec = spec, label = "SegE")
    val colorF by animateColorAsState(if (activeStates[5]) activeColor else inactiveColor, animationSpec = spec, label = "SegF")
    val colorG by animateColorAsState(if (activeStates[6]) activeColor else inactiveColor, animationSpec = spec, label = "SegG")

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val t = w * 0.18f
        val gap = if (is3D) t * 0.22f else t * 0.1f

        val xL = 0f
        val xR = w
        val yT = 0f
        val yM = h / 2f
        val yB = h

        fun drawSegmentPath(points: List<Offset>, color: Color, isActive: Boolean) {
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                    close()
                }
            }

            if (isActive) {
                // Внешнее неоновое свечение рисуется только при включенном isGlowEnabled
                if (isGlow && is3D) {
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.35f),
                        style = Fill
                    )
                }
                // Активное ядро
                drawPath(
                    path = path,
                    color = color,
                    style = Fill
                )
            } else {
                // Матовый неактивный корпус
                drawPath(
                    path = path,
                    color = color,
                    style = Fill
                )
            }
        }

        // Seg A (Верхний)
        drawSegmentPath(
            listOf(
                Offset(xL + t + gap, yT),
                Offset(xR - t - gap, yT),
                Offset(xR - t * 1.6f - gap, yT + t),
                Offset(xL + t * 1.6f + gap, yT + t)
            ),
            colorA, activeStates[0]
        )

        // Seg B (Верхний правый)
        drawSegmentPath(
            listOf(
                Offset(xR, yT + t + gap),
                Offset(xR, yM - gap / 2f),
                Offset(xR - t, yM - t * 0.6f - gap / 2f),
                Offset(xR - t, yT + t * 1.6f + gap)
            ),
            colorB, activeStates[1]
        )

        // Seg C (Нижний правый)
        drawSegmentPath(
            listOf(
                Offset(xR, yM + gap / 2f),
                Offset(xR, yB - t - gap),
                Offset(xR - t, yB - t * 1.6f - gap),
                Offset(xR - t, yM + t * 0.6f + gap / 2f)
            ),
            colorC, activeStates[2]
        )

        // Seg D (Нижний)
        drawSegmentPath(
            listOf(
                Offset(xL + t + gap, yB),
                Offset(xR - t - gap, yB),
                Offset(xR - t * 1.6f - gap, yB - t),
                Offset(xL + t * 1.6f + gap, yB - t)
            ),
            colorD, activeStates[3]
        )

        // Seg E (Нижний левый)
        drawSegmentPath(
            listOf(
                Offset(xL, yM + gap / 2f),
                Offset(xL, yB - t - gap),
                Offset(xL + t, yB - t * 1.6f - gap),
                Offset(xL + t, yM + t * 0.6f + gap / 2f)
            ),
            colorE, activeStates[4]
        )

        // Seg F (Верхний левый)
        drawSegmentPath(
            listOf(
                Offset(xL, yT + t + gap),
                Offset(xL, yM - gap / 2f),
                Offset(xL + t, yM - t * 0.6f - gap / 2f),
                Offset(xL + t, yT + t * 1.6f + gap)
            ),
            colorF, activeStates[5]
        )

        // Seg G (Центральный)
        drawSegmentPath(
            listOf(
                Offset(xL + t + gap * 1.2f, yM),
                Offset(xL + t * 1.5f + gap * 1.2f, yM - t * 0.5f),
                Offset(xR - t * 1.5f - gap * 1.2f, yM - t * 0.5f),
                Offset(xR - t - gap * 1.2f, yM),
                Offset(xR - t * 1.5f - gap * 1.2f, yM + t * 0.5f),
                Offset(xL + t * 1.5f + gap * 1.2f, yM + t * 0.5f)
            ),
            colorG, activeStates[6]
        )
    }
}

// ============================================================================
// СЕКЦИЯ 2: МАСШТАБИРУЕМОЕ СВЕТОДИОДНОЕ ТАБЛО ВРЕМЕНИ
// ============================================================================
@Composable
fun SevenSegmentTimeDisplay(
    timeString: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    digitWidth: Dp = 36.dp,
    digitHeight: Dp = 64.dp
) {
    val themeConfig = LocalPureClockConfig.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        timeString.forEach { char ->
            if (char.isDigit()) {
                SevenSegmentDigit(
                    digit = char,
                    activeColor = accentColor,
                    modifier = Modifier
                        .width(digitWidth)
                        .height(digitHeight)
                        .padding(horizontal = 3.dp)
                )
            } else if (char == ':') {
                Canvas(
                    modifier = Modifier
                        .width(digitWidth * 0.4f)
                        .height(digitHeight)
                ) {
                    val cx = size.width / 2f
                    val cy1 = size.height * 0.33f
                    val cy2 = size.height * 0.67f
                    val dotR = size.width * 0.22f

                    if (themeConfig.isGlowEnabled && themeConfig.is3dEnabled) {
                        drawCircle(color = accentColor.copy(alpha = 0.35f), radius = dotR * 2.0f, center = Offset(cx, cy1))
                        drawCircle(color = accentColor.copy(alpha = 0.35f), radius = dotR * 2.0f, center = Offset(cx, cy2))
                    }

                    drawCircle(color = accentColor, radius = dotR, center = Offset(cx, cy1))
                    drawCircle(color = accentColor, radius = dotR, center = Offset(cx, cy2))
                }
            }
        }
    }
}