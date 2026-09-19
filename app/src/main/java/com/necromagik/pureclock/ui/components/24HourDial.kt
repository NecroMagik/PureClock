package com.necromagik.pureclock.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.AlarmPickerStyle
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
    pickerStyle: AlarmPickerStyle = AlarmPickerStyle.DIAL,
    dialSize: Dp = 270.dp
) {
    var isKeyboardMode by remember { mutableStateOf(false) }
    var dialMode by remember { mutableStateOf(DialMode.HOURS) }
    val accentColor = MaterialTheme.colorScheme.primary

    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var hourInput by remember(selectedHour) {
        mutableStateOf(TextFieldValue(String.format(Locale.ROOT, "%02d", selectedHour)))
    }
    var minuteInput by remember(selectedMinute) {
        mutableStateOf(TextFieldValue(String.format(Locale.ROOT, "%02d", selectedMinute)))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // ШАПКА ВЫБОРА: Интерактивное табло времени с прямым клавиатурным вводом
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Поле часов
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isKeyboardMode || dialMode == DialMode.HOURS) accentColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .border(
                        width = if (isKeyboardMode || dialMode == DialMode.HOURS) 2.dp else 1.dp,
                        color = if (isKeyboardMode || dialMode == DialMode.HOURS) accentColor.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        dialMode = DialMode.HOURS
                        isKeyboardMode = true
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isKeyboardMode) {
                    BasicTextField(
                        value = hourInput,
                        onValueChange = { newVal ->
                            val digits = newVal.text.filter { it.isDigit() }.take(2)
                            hourInput = newVal.copy(text = digits, selection = TextRange(digits.length))
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed in 0..23) {
                                onHourSelected(parsed)
                                if (digits.length == 2) {
                                    minuteFocusRequester.requestFocus()
                                    dialMode = DialMode.MINUTES
                                }
                            }
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(hourFocusRequester),
                        textStyle = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                minuteFocusRequester.requestFocus()
                                dialMode = DialMode.MINUTES
                            }
                        )
                    )
                } else {
                    Text(
                        text = String.format(Locale.ROOT, "%02d", selectedHour),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (dialMode == DialMode.HOURS) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = ":",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Поле минут
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isKeyboardMode || dialMode == DialMode.MINUTES) accentColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .border(
                        width = if (isKeyboardMode || dialMode == DialMode.MINUTES) 2.dp else 1.dp,
                        color = if (isKeyboardMode || dialMode == DialMode.MINUTES) accentColor.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        dialMode = DialMode.MINUTES
                        isKeyboardMode = true
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isKeyboardMode) {
                    BasicTextField(
                        value = minuteInput,
                        onValueChange = { newVal ->
                            val digits = newVal.text.filter { it.isDigit() }.take(2)
                            minuteInput = newVal.copy(text = digits, selection = TextRange(digits.length))
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed in 0..59) {
                                onMinuteSelected(parsed)
                            }
                        },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(minuteFocusRequester),
                        textStyle = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                isKeyboardMode = false
                            }
                        )
                    )
                } else {
                    Text(
                        text = String.format(Locale.ROOT, "%02d", selectedMinute),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (dialMode == DialMode.MINUTES) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Кнопка переключения клавиатуры / графического селектора
        Button(
            onClick = {
                isKeyboardMode = !isKeyboardMode
                if (isKeyboardMode) {
                    dialMode = DialMode.HOURS
                } else {
                    focusManager.clearFocus()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isKeyboardMode) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isKeyboardMode) Color.Black else MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.bounceClick()
        ) {
            Icon(
                imageVector = if (isKeyboardMode) Icons.Default.Dialpad else Icons.Default.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isKeyboardMode) "Графический выбор" else "Ввести с клавиатуры",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Графический селектор
        AnimatedVisibility(
            visible = !isKeyboardMode,
            enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically()
        ) {
            when (pickerStyle) {
                AlarmPickerStyle.DIAL -> {
                    DialVisualPicker(
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        mode = dialMode,
                        onModeChanged = { dialMode = it },
                        onHourSelected = onHourSelected,
                        onMinuteSelected = onMinuteSelected,
                        dialSize = dialSize,
                        accentColor = accentColor
                    )
                }
                AlarmPickerStyle.WHEEL -> {
                    AlarmWheelPicker(
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        accentColor = accentColor,
                        onHourSelected = onHourSelected,
                        onMinuteSelected = onMinuteSelected
                    )
                }
                AlarmPickerStyle.TIMELINE -> {
                    AlarmTimelinePicker(
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        accentColor = accentColor,
                        onHourSelected = onHourSelected,
                        onMinuteSelected = onMinuteSelected
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// РЕЖИМ 1: ЦИФЕРБЛАТ
// ----------------------------------------------------------------------------
@Composable
private fun DialVisualPicker(
    selectedHour: Int,
    selectedMinute: Int,
    mode: DialMode,
    onModeChanged: (DialMode) -> Unit,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    dialSize: Dp,
    accentColor: Color
) {
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    var isInnerRingLocked by remember { mutableStateOf(false) }

    val currentHandAngle = remember(mode, selectedHour, selectedMinute) {
        if (mode == DialMode.HOURS) {
            val hour12 = selectedHour % 12
            (hour12 * 30f) - 90f
        } else {
            (selectedMinute * 6f) - 90f
        }
    }

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
                                    onModeChanged(DialMode.MINUTES)
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

        drawCircle(color = Color(0xFF141414), radius = radius, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

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

            val p1 = Offset((center.x + (radius - 4.dp.toPx()) * cos(angle)).toFloat(), (center.y + (radius - 4.dp.toPx()) * sin(angle)).toFloat())
            val p2 = Offset((center.x + (radius - 4.dp.toPx() - tickLen) * cos(angle)).toFloat(), (center.y + (radius - 4.dp.toPx() - tickLen) * sin(angle)).toFloat())
            drawLine(color = tickColor, start = p1, end = p2, strokeWidth = strokeW, cap = StrokeCap.Round)
        }

        val isInner = mode == DialMode.HOURS && (selectedHour == 0 || selectedHour > 12)
        val handRadius = if (isInner) innerRadius else outerRadius
        val rad = Math.toRadians(currentHandAngle.toDouble())
        val endPos = Offset((center.x + handRadius * cos(rad)).toFloat(), (center.y + handRadius * sin(rad)).toFloat())

        drawLine(color = accentColor, start = center, end = endPos, strokeWidth = 2.dp.toPx())
        drawCircle(color = accentColor, radius = 4.dp.toPx(), center = center)

        if (mode == DialMode.HOURS) {
            drawCircle(color = accentColor, radius = 17.dp.toPx(), center = endPos)

            for (h in 1..12) {
                val angle = (h * 30 - 90) * (PI / 180)
                val p = Offset((center.x + outerRadius * cos(angle)).toFloat(), (center.y + outerRadius * sin(angle)).toFloat())
                val isSel = selectedHour == h
                val res = textMeasurer.measure("$h", TextStyle(fontSize = 15.sp, fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium, color = if (isSel) Color.Black else Color.White))
                drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
            }

            for (h in 1..12) {
                val val24 = if (h == 12) 0 else h + 12
                val angle = (h * 30 - 90) * (PI / 180)
                val p = Offset((center.x + innerRadius * cos(angle)).toFloat(), (center.y + innerRadius * sin(angle)).toFloat())
                val isSel = selectedHour == val24
                val res = textMeasurer.measure("%02d".format(val24), TextStyle(fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) Color.Black else Color.Gray))
                drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
            }
        } else {
            for (i in 0 until 12) {
                val m = i * 5
                val angle = (i * 30 - 90) * (PI / 180)
                val p = Offset((center.x + outerRadius * cos(angle)).toFloat(), (center.y + outerRadius * sin(angle)).toFloat())
                if (selectedMinute != m) {
                    val res = textMeasurer.measure("%02d".format(m), TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f)))
                    drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                }
            }

            drawCircle(color = accentColor, radius = 16.dp.toPx(), center = endPos)
            val minRes = textMeasurer.measure("%02d".format(selectedMinute), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black))
            drawText(minRes, topLeft = Offset(endPos.x - minRes.size.width / 2f, endPos.y - minRes.size.height / 2f))
        }
    }
}

// ----------------------------------------------------------------------------
// РЕЖИМ 2: БАРАБАННЫЙ СЕЛЕКТОР (БЕСКОНЕЧНЫЕ ЗАЦИКЛЕННЫЕ КОЛЕСА)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmWheelPicker(
    selectedHour: Int,
    selectedMinute: Int,
    accentColor: Color,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val totalHeight = itemHeight * 3

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .border(1.2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CyclicWheelColumn(
                range = 0..23,
                selectedValue = selectedHour,
                itemHeight = itemHeight,
                unitTitle = "ч",
                accentColor = accentColor,
                onSelected = onHourSelected,
                modifier = Modifier.weight(1f)
            )

            Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

            CyclicWheelColumn(
                range = 0..59,
                selectedValue = selectedMinute,
                itemHeight = itemHeight,
                unitTitle = "мин",
                accentColor = accentColor,
                onSelected = onMinuteSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CyclicWheelColumn(
    range: IntRange,
    selectedValue: Int,
    itemHeight: Dp,
    unitTitle: String,
    accentColor: Color,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = range.count()
    // Зацикливание за счёт большого количества повторений
    val cycles = 200
    val totalItems = count * cycles
    val middleOffset = (cycles / 2) * count

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = middleOffset + selectedValue)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current

    val currentCenterIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val centerOffset = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - centerOffset)
            }?.index ?: (middleOffset + selectedValue)
        }
    }

    LaunchedEffect(currentCenterIndex) {
        val actualValue = (currentCenterIndex % count + count) % count
        if (actualValue != selectedValue) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onSelected(actualValue)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        items(totalItems) { index ->
            val value = index % count
            val isSelected = index == currentCenterIndex
            Row(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.ROOT, "%02d", value),
                    fontSize = if (isSelected) 22.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isSelected) accentColor else Color.Gray.copy(alpha = 0.45f)
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unitTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// РЕЖИМ 3: ВОЛНОВАЯ ИНТЕРАКТИВНАЯ ЛИНИЯ В СТРОКУ (ПО РЕФЕРЕНСУ)
// ----------------------------------------------------------------------------
@Composable
fun AlarmTimelinePicker(
    selectedHour: Int,
    selectedMinute: Int,
    accentColor: Color,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Шкала Часов (0..23)
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ЧАСЫ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = String.format(Locale.ROOT, "%02d:00", selectedHour),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            WaveTimelineRuler(
                totalCount = 24,
                selectedValue = selectedHour,
                accentColor = accentColor,
                labelStep = 3,
                onValueSelected = onHourSelected
            )
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Шкала Минут (0..59)
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "МИНУТЫ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = String.format(Locale.ROOT, "%02d мин", selectedMinute),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            WaveTimelineRuler(
                totalCount = 60,
                selectedValue = selectedMinute,
                accentColor = accentColor,
                labelStep = 5,
                onValueSelected = onMinuteSelected
            )
        }
    }
}

@Composable
private fun WaveTimelineRuler(
    totalCount: Int,
    selectedValue: Int,
    accentColor: Color,
    labelStep: Int,
    onValueSelected: (Int) -> Unit
) {
    val view = LocalView.current
    var isTouching by remember { mutableStateOf(false) }
    var currentTouchX by remember { mutableFloatStateOf(-1f) }

    val accentArgb = remember(accentColor) {
        android.graphics.Color.argb(
            (accentColor.alpha * 255).toInt(),
            (accentColor.red * 255).toInt(),
            (accentColor.green * 255).toInt(),
            (accentColor.blue * 255).toInt()
        )
    }

    // Текстовые кисти с поддержкой моноширинных цифр ("tnum")
    val selectedTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
    }

    val regularTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            fontFeatureSettings = "tnum"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .pointerInput(totalCount) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val posX = change.position.x

                        when (event.type) {
                            PointerEventType.Press -> {
                                isTouching = true
                                currentTouchX = posX
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                if (isTouching) {
                                    currentTouchX = posX
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> {
                                isTouching = false
                                currentTouchX = -1f
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val horizontalPadding = 20.dp.toPx()
            val usableWidth = width - (horizontalPadding * 2)

            val stepX = usableWidth / (totalCount - 1).toFloat()
            val baselineY = height * 0.60f

            // Горизонтальная базовая линия
            drawLine(
                color = Color.Gray.copy(alpha = 0.22f),
                start = Offset(horizontalPadding - 6.dp.toPx(), baselineY),
                end = Offset(width - horizontalPadding + 6.dp.toPx(), baselineY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            val activeIndex = if (isTouching && currentTouchX in horizontalPadding..(width - horizontalPadding)) {
                val raw = ((currentTouchX - horizontalPadding) / stepX).roundToInt()
                raw.coerceIn(0, totalCount - 1)
            } else {
                selectedValue
            }

            if (activeIndex != selectedValue && isTouching) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onValueSelected(activeIndex)
            }

            val waveRadiusPx = 72.dp.toPx()
            val baseActiveX = horizontalPadding + (activeIndex * stepX)

            // Фиксированные параметры типографики: стабильный размер и высота подъема
            val selectedFontSizePx = 20.sp.toPx()
            val regularFontSizePx = 11.sp.toPx()

            selectedTextPaint.textSize = selectedFontSizePx
            selectedTextPaint.color = accentArgb

            regularTextPaint.textSize = regularFontSizePx
            regularTextPaint.color = android.graphics.Color.GRAY

            val maxPeakTickUp = 26.dp.toPx()
            val fixedTextY = baselineY - maxPeakTickUp - 6.dp.toPx()

            for (i in 0 until totalCount) {
                val origX = horizontalPadding + (i * stepX)
                val dist = origX - baseActiveX
                val absDist = abs(dist)

                // Волновое распределение делений
                val waveFactor = if (absDist < waveRadiusPx) {
                    cos((absDist / waveRadiusPx) * (PI / 2)).pow(2.0).toFloat()
                } else {
                    0f
                }

                // Линзовое расталкивание соседних засечек
                val spreadMaxPx = 12.dp.toPx()
                val spreadOffset = if (absDist in 1f..waveRadiusPx) {
                    sin((absDist / waveRadiusPx) * PI).toFloat() * spreadMaxPx * sign(dist)
                } else {
                    0f
                }

                val posX = origX + spreadOffset
                val isExactSelected = (i == activeIndex)

                // Расчет высоты засечки: для выбранного — четкий максимум
                val defaultTickUp = if (i % labelStep == 0) 9.dp.toPx() else 4.5.dp.toPx()
                val tickUp = if (isExactSelected) {
                    maxPeakTickUp
                } else {
                    defaultTickUp + (waveFactor * 14.dp.toPx())
                }

                val tickDown = if (isExactSelected) 16.dp.toPx() else 4.dp.toPx()

                val tickColor = when {
                    isExactSelected -> accentColor
                    waveFactor > 0.15f -> accentColor.copy(alpha = 0.35f + waveFactor * 0.5f)
                    i % labelStep == 0 -> Color.Gray.copy(alpha = 0.65f)
                    else -> Color.Gray.copy(alpha = 0.22f)
                }

                val tickWidth = if (isExactSelected) 3.dp.toPx() else (1.2.dp.toPx() + waveFactor * 1.3.dp.toPx())

                drawLine(
                    color = tickColor,
                    start = Offset(posX, baselineY - tickUp),
                    end = Offset(posX, baselineY + tickDown),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )

                // Отрисовка чисел:
                // Выбранное число ВСЕГДА рисуется одного строгого размера в точке fixedTextY
                if (isExactSelected) {
                    drawContext.canvas.nativeCanvas.drawText(
                        i.toString(),
                        posX,
                        fixedTextY,
                        selectedTextPaint
                    )
                } else if (i % labelStep == 0 && waveFactor < 0.08f) {
                    // Спокойные числа вне волны
                    val calmTextY = baselineY - defaultTickUp - 5.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(
                        i.toString(),
                        posX,
                        calmTextY,
                        regularTextPaint
                    )
                }

                // Петля-визир снизу под пальцем
                if (isExactSelected) {
                    val circleCenterY = baselineY + tickDown + 6.dp.toPx()
                    val circleRadius = 4.5.dp.toPx()

                    drawCircle(
                        color = accentColor,
                        radius = circleRadius,
                        center = Offset(posX, circleCenterY)
                    )
                    drawCircle(
                        color = Color(0xFF141414),
                        radius = circleRadius * 0.45f,
                        center = Offset(posX, circleCenterY)
                    )
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