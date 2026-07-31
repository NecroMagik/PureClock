package com.necromagik.pureclock.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Locale
import kotlin.math.*

enum class DialMode { HOURS, MINUTES }

// ============================================================================
// СЕКЦИЯ 1: ИНТЕРАКТИВНЫЙ 24-ЧАСОВОЙ ЦИФЕРБЛАТ ВЫБОРА ВРЕМЕНИ
// ============================================================================
@Composable
fun TwentyFourHourDial(
    selectedHour: Int,
    selectedMinute: Int,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 290.dp
) {
    var mode by remember { mutableStateOf(DialMode.HOURS) }
    var isKeyboardInputMode by remember { mutableStateOf(false) }

    val view = LocalView.current
    val accentColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()

    var dragAngle by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isInnerRingLocked by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val currentHandAngle = if (isDragging) {
        dragAngle
    } else {
        if (mode == DialMode.HOURS) {
            val hour12 = selectedHour % 12
            (hour12 * 30f) - 90f
        } else {
            (selectedMinute * 6f) - 90f
        }
    }

    LaunchedEffect(isKeyboardInputMode) {
        if (isKeyboardInputMode) {
            focusRequester.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
// ============================================================================
// СЕКЦИЯ 2: ШАПКА ВЫБОРА (ЦИФРОВОЕ ТАБЛО И КНОПКА КЛАВИАТУРЫ)
// ============================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isKeyboardInputMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    var hourText by remember(selectedHour) {
                        mutableStateOf(String.format(Locale.ROOT, "%02d", selectedHour))
                    }
                    var minuteText by remember(selectedMinute) {
                        mutableStateOf(String.format(Locale.ROOT, "%02d", selectedMinute))
                    }

                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                hourText = newValue
                                val h = newValue.toIntOrNull()
                                if (h != null && h in 0..23) onHourSelected(h)
                            }
                        },
                        modifier = Modifier
                            .width(80.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Text(
                        text = ":",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                minuteText = newValue
                                val m = newValue.toIntOrNull()
                                if (m != null && m in 0..59) onMinuteSelected(m)
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        textStyle = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format(Locale.ROOT, "%02d", selectedHour),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == DialMode.HOURS) accentColor else Color.Gray,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .pointerInput(Unit) { detectTapGestures { mode = DialMode.HOURS } }
                    )
                    Text(
                        text = ":",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%02d", selectedMinute),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == DialMode.MINUTES) accentColor else Color.Gray,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .pointerInput(Unit) { detectTapGestures { mode = DialMode.MINUTES } }
                    )
                }
            }

            IconButton(
                onClick = { isKeyboardInputMode = !isKeyboardInputMode },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Клавиатура",
                    tint = if (isKeyboardInputMode) accentColor else Color.Gray
                )
            }
        }

// ============================================================================
// СЕКЦИЯ 3: ОТРИСОВКА И ДРАГ-ОБРАБОТКА АНАЛОГОВОЙ СТРЕЛКИ (CANVAS)
// ============================================================================
        AnimatedVisibility(
            visible = !isKeyboardInputMode,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(250)) + scaleIn(initialScale = 0.85f),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.88f)) togetherWith
                            (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.12f))
                },
                label = "HoursToMinutesAnimation"
            ) { activeMode ->
                Canvas(
                    modifier = Modifier
                        .size(dialSize)
                        .pointerInput(activeMode, selectedHour, selectedMinute) {
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
                                            isDragging = true
                                            dragAngle = angleDeg
                                            isInnerRingLocked = distance < (center.x * 0.62f)
                                            event.changes.forEach { it.consume() }

                                            updateValues(
                                                mode = activeMode,
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
                                            if (isDragging) {
                                                dragAngle = angleDeg
                                                event.changes.forEach { it.consume() }

                                                if (distance < center.x * 0.45f) isInnerRingLocked = true
                                                else if (distance > center.x * 0.75f) isInnerRingLocked = false

                                                updateValues(
                                                    mode = activeMode,
                                                    normAngle = normAngle,
                                                    isInner = isInnerRingLocked,
                                                    selectedHour = selectedHour,
                                                    selectedMinute = selectedMinute,
                                                    onHourSelected = onHourSelected,
                                                    onMinuteSelected = onMinuteSelected,
                                                    view = view
                                                )
                                            }
                                        }

                                        PointerEventType.Release -> {
                                            if (isDragging) {
                                                isDragging = false
                                                event.changes.forEach { it.consume() }

                                                if (activeMode == DialMode.HOURS) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    mode = DialMode.MINUTES
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val canvasSize = size.width
                    val center = Offset(canvasSize / 2f, canvasSize / 2f)
                    val dialRadius = canvasSize / 2f - 2.dp.toPx()

                    val outerRadius = dialRadius - 22.dp.toPx()
                    val innerRadius = outerRadius * 0.60f

                    drawCircle(color = Color(0xFF141414), radius = dialRadius)

                    drawCircle(
                        color = Color(0xFF2C2C2E),
                        radius = dialRadius,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    for (i in 0 until 60) {
                        val angleRad = Math.toRadians((i * 6 - 90).toDouble())
                        val isMajor = i % 5 == 0
                        val tickLength = if (isMajor) 6.dp.toPx() else 3.dp.toPx()
                        val strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                        val tickColor = if (isMajor) Color(0xFF48484A) else Color(0xFF2C2C2E)

                        val start = Offset(
                            x = center.x + ((dialRadius - 6.dp.toPx()) * cos(angleRad)).toFloat(),
                            y = center.y + ((dialRadius - 6.dp.toPx()) * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            x = center.x + ((dialRadius - 6.dp.toPx() - tickLength) * cos(angleRad)).toFloat(),
                            y = center.y + ((dialRadius - 6.dp.toPx() - tickLength) * sin(angleRad)).toFloat()
                        )

                        drawLine(
                            color = tickColor,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    val isInner = if (activeMode == DialMode.HOURS) {
                        if (isDragging) isInnerRingLocked else (selectedHour == 0 || selectedHour > 12)
                    } else false

                    val currentRadius = if (isInner) innerRadius else outerRadius

                    val rad = Math.toRadians(currentHandAngle.toDouble())
                    val handEnd = Offset(
                        x = center.x + (currentRadius * cos(rad)).toFloat(),
                        y = center.y + (currentRadius * sin(rad)).toFloat()
                    )

                    drawLine(
                        color = accentColor,
                        start = center,
                        end = handEnd,
                        strokeWidth = 2.dp.toPx()
                    )

                    drawCircle(
                        color = accentColor,
                        radius = 17.dp.toPx(),
                        center = handEnd
                    )

                    drawCircle(color = accentColor, radius = 4.dp.toPx(), center = center)

                    if (activeMode == DialMode.HOURS) {
                        for (i in 1..12) {
                            val angle = (i * 30 - 90) * (PI / 180)
                            val pos = Offset(
                                x = center.x + (outerRadius * cos(angle)).toFloat(),
                                y = center.y + (outerRadius * sin(angle)).toFloat()
                            )
                            val isSelected = selectedHour == i

                            drawDialText(
                                text = "$i",
                                centerPos = pos,
                                isSelected = isSelected,
                                textMeasurer = textMeasurer,
                                fontSize = 15.sp,
                                textColor = if (isSelected) Color.Black else Color.White
                            )
                        }

                        for (i in 1..12) {
                            val hourVal = if (i == 12) 0 else i + 12
                            val angle = (i * 30 - 90) * (PI / 180)
                            val pos = Offset(
                                x = center.x + (innerRadius * cos(angle)).toFloat(),
                                y = center.y + (innerRadius * sin(angle)).toFloat()
                            )
                            val isSelected = selectedHour == hourVal

                            drawDialText(
                                text = String.format(Locale.ROOT, "%02d", hourVal),
                                centerPos = pos,
                                isSelected = isSelected,
                                textMeasurer = textMeasurer,
                                fontSize = 12.sp,
                                textColor = if (isSelected) Color.Black else Color.Gray
                            )
                        }
                    } else {
                        for (i in 0 until 12) {
                            val minVal = i * 5
                            val angle = (i * 30 - 90) * (PI / 180)
                            val pos = Offset(
                                x = center.x + (outerRadius * cos(angle)).toFloat(),
                                y = center.y + (outerRadius * sin(angle)).toFloat()
                            )
                            val isSelected = (selectedMinute / 5) * 5 == minVal

                            drawDialText(
                                text = String.format(Locale.ROOT, "%02d", minVal),
                                centerPos = pos,
                                isSelected = isSelected,
                                textMeasurer = textMeasurer,
                                fontSize = 15.sp,
                                textColor = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// СЕКЦИЯ 4: ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ PACЧЕTA УГЛОВ И РЕНДЕРИНГА ТЕКСТА
// ============================================================================
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDialText(
    text: String,
    centerPos: Offset,
    isSelected: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color
) {
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    )

    val topLeft = Offset(
        x = centerPos.x - (textLayoutResult.size.width / 2f),
        y = centerPos.y - (textLayoutResult.size.height / 2f)
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = topLeft
    )
}