package com.necromagik.pureclock.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import java.util.Locale
import kotlin.math.*

enum class DialMode { HOURS, MINUTES }

@Composable
fun TwentyFourHourDial(
    selectedHour: Int,
    selectedMinute: Int,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 280.dp
) {
    var mode by remember { mutableStateOf(DialMode.HOURS) }
    var isKeyboardInputMode by remember { mutableStateOf(false) }

    val view = LocalView.current
    val accentColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    var isInnerRingLocked by remember { mutableStateOf(false) }

    // Расчёт текущего угла стрелки: всегда строго указывает на активное значение без задержек
    val currentHandAngle = remember(mode, selectedHour, selectedMinute) {
        if (mode == DialMode.HOURS) {
            val hour12 = selectedHour % 12
            (hour12 * 30f) - 90f
        } else {
            (selectedMinute * 6f) - 90f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Шапка с табло времени
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (isKeyboardInputMode) {
                var hourStr by remember(selectedHour) { mutableStateOf("%02d".format(selectedHour)) }
                var minStr by remember(selectedMinute) { mutableStateOf("%02d".format(selectedMinute)) }

                OutlinedTextField(
                    value = hourStr,
                    onValueChange = {
                        if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                            hourStr = it
                            it.toIntOrNull()?.takeIf { h -> h in 0..23 }?.let(onHourSelected)
                        }
                    },
                    modifier = Modifier
                        .width(86.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    ":",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                OutlinedTextField(
                    value = minStr,
                    onValueChange = {
                        if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                            minStr = it
                            it.toIntOrNull()?.takeIf { m -> m in 0..59 }?.let(onMinuteSelected)
                        }
                    },
                    modifier = Modifier.width(86.dp),
                    textStyle = TextStyle(
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (mode == DialMode.HOURS) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { mode = DialMode.HOURS }
                        .bounceClick()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "%02d".format(selectedHour),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == DialMode.HOURS) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Text(":", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (mode == DialMode.MINUTES) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { mode = DialMode.MINUTES }
                        .bounceClick()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "%02d".format(selectedMinute),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == DialMode.MINUTES) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = {
                    isKeyboardInputMode = !isKeyboardInputMode
                    if (isKeyboardInputMode) focusRequester.requestFocus()
                },
                modifier = Modifier.bounceClick()
            ) {
                Icon(
                    Icons.Default.Keyboard,
                    contentDescription = "Клавиатура",
                    tint = if (isKeyboardInputMode) accentColor else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedVisibility(
            visible = !isKeyboardInputMode,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            Canvas(
                modifier = Modifier
                    .size(dialSize)
                    .pointerInput(mode, selectedHour, selectedMinute) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.firstOrNull()?.position ?: continue
                                val center = Offset(size.width / 2f, size.height / 2f)

                                val dx = position.x - center.x
                                val dy = position.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)

                                val angleDeg = (atan2(dy, dx) * 180 / PI).toFloat()
                                var normAngle = angleDeg + 90f
                                if (normAngle < 0) normAngle += 360f

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        isInnerRingLocked = distance < (center.x * 0.62f)
                                        event.changes.forEach { it.consume() }

                                        updateValues(
                                            mode = mode,
                                            normAngle = normAngle,
                                            isInner = isInnerRingLocked,
                                            selectedHour = selectedHour,
                                            selectedMinute = selectedMinute,
                                            onHourSelected = onHourSelected,
                                            onMinuteSelected = onMinuteSelected,
                                            view = view
                                        )
                                    }

                                    PointerEventType.Move -> {
                                        event.changes.forEach { it.consume() }

                                        if (distance < center.x * 0.48f) isInnerRingLocked = true
                                        else if (distance > center.x * 0.72f) isInnerRingLocked = false

                                        updateValues(
                                            mode = mode,
                                            normAngle = normAngle,
                                            isInner = isInnerRingLocked,
                                            selectedHour = selectedHour,
                                            selectedMinute = selectedMinute,
                                            onHourSelected = onHourSelected,
                                            onMinuteSelected = onMinuteSelected,
                                            view = view
                                        )
                                    }

                                    PointerEventType.Release -> {
                                        event.changes.forEach { it.consume() }
                                        if (mode == DialMode.HOURS) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            mode = DialMode.MINUTES
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                val outerRadius = radius * 0.77f
                val innerRadius = radius * 0.47f

                // Фон циферблата
                drawCircle(color = Color(0xFF141414), radius = radius, center = center)
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Минутные риски по периметру
                for (i in 0 until 60) {
                    val angle = Math.toRadians((i * 6 - 90).toDouble())
                    val isMajor = i % 5 == 0
                    val isExactCurrentMinute = mode == DialMode.MINUTES && selectedMinute == i

                    val tickLen = when {
                        isExactCurrentMinute -> 8.dp.toPx()
                        isMajor -> 5.5.dp.toPx()
                        else -> 3.dp.toPx()
                    }
                    val strokeW = if (isExactCurrentMinute || isMajor) 1.8.dp.toPx() else 1.dp.toPx()
                    val tickColor = when {
                        isExactCurrentMinute -> accentColor
                        isMajor -> Color.Gray.copy(alpha = 0.65f)
                        else -> Color.DarkGray.copy(alpha = 0.35f)
                    }

                    val p1 = Offset(
                        (center.x + (radius - 4.dp.toPx()) * cos(angle)).toFloat(),
                        (center.y + (radius - 4.dp.toPx()) * sin(angle)).toFloat()
                    )
                    val p2 = Offset(
                        (center.x + (radius - 4.dp.toPx() - tickLen) * cos(angle)).toFloat(),
                        (center.y + (radius - 4.dp.toPx() - tickLen) * sin(angle)).toFloat()
                    )

                    drawLine(
                        color = tickColor,
                        start = p1,
                        end = p2,
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }

                val isInner = mode == DialMode.HOURS && (selectedHour == 0 || selectedHour > 12)
                val handRadius = if (isInner) innerRadius else outerRadius

                val rad = Math.toRadians(currentHandAngle.toDouble())
                val endPos = Offset(
                    (center.x + handRadius * cos(rad)).toFloat(),
                    (center.y + handRadius * sin(rad)).toFloat()
                )

                // Линия стрелки
                drawLine(
                    color = accentColor,
                    start = center,
                    end = endPos,
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(color = accentColor, radius = 4.dp.toPx(), center = center)

                if (mode == DialMode.HOURS) {
                    // Магнитная таблетка-селектор
                    drawCircle(color = accentColor, radius = 17.dp.toPx(), center = endPos)

                    for (h in 1..12) {
                        val angle = (h * 30 - 90) * (PI / 180)
                        val p = Offset(
                            (center.x + outerRadius * cos(angle)).toFloat(),
                            (center.y + outerRadius * sin(angle)).toFloat()
                        )
                        val isSel = selectedHour == h
                        val res = textMeasurer.measure(
                            "$h",
                            TextStyle(
                                fontSize = 15.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSel) Color.Black else Color.White
                            )
                        )
                        drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                    }

                    for (h in 1..12) {
                        val val24 = if (h == 12) 0 else h + 12
                        val angle = (h * 30 - 90) * (PI / 180)
                        val p = Offset(
                            (center.x + innerRadius * cos(angle)).toFloat(),
                            (center.y + innerRadius * sin(angle)).toFloat()
                        )
                        val isSel = selectedHour == val24
                        val res = textMeasurer.measure(
                            "%02d".format(val24),
                            TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.Black else Color.Gray
                            )
                        )
                        drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                    }
                } else {
                    // Статичная разметка 00, 05, 10...55
                    for (i in 0 until 12) {
                        val m = i * 5
                        val angle = (i * 30 - 90) * (PI / 180)
                        val p = Offset(
                            (center.x + outerRadius * cos(angle)).toFloat(),
                            (center.y + outerRadius * sin(angle)).toFloat()
                        )
                        val isExactFiveMinute = selectedMinute == m

                        // Скрываем статичную цифру, если прямо на неё указывает стрелка, исключая наложение
                        if (!isExactFiveMinute) {
                            val res = textMeasurer.measure(
                                "%02d".format(m),
                                TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                            drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                        }
                    }

                    // Таблетка селектора с точным числом текущей минуты
                    drawCircle(color = accentColor, radius = 16.dp.toPx(), center = endPos)

                    val currentMinText = "%02d".format(selectedMinute)
                    val minRes = textMeasurer.measure(
                        currentMinText,
                        TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    )
                    drawText(minRes, topLeft = Offset(endPos.x - minRes.size.width / 2f, endPos.y - minRes.size.height / 2f))
                }
            }
        }
    }
}

private fun updateValues(
    mode: DialMode,
    normAngle: Float,
    isInner: Boolean,
    selectedHour: Int,
    selectedMinute: Int,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    view: android.view.View
) {
    if (mode == DialMode.HOURS) {
        val rawSector = (normAngle / 30f).roundToInt()
        val sector = if (rawSector >= 12) 0 else rawSector

        val hour = if (isInner) {
            if (sector == 0) 0 else sector + 12
        } else {
            if (sector == 0) 12 else sector
        }

        if (hour != selectedHour) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onHourSelected(hour)
        }
    } else {
        val rawMinute = (normAngle / 6f).roundToInt()
        val minute = if (rawMinute >= 60) 0 else rawMinute

        if (minute != selectedMinute) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onMinuteSelected(minute)
        }
    }
}