package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.breathingGlow
import com.necromagik.pureclock.ui.animation.staggeredEntrance
import com.necromagik.pureclock.ui.components.EmptyTimerState
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.TimerItem
import com.necromagik.pureclock.ui.viewmodel.TimerState
import com.necromagik.pureclock.ui.viewmodel.TimerViewModel
import com.necromagik.pureclock.ui.viewmodel.TimerViewMode
import kotlinx.coroutines.delay
import java.util.Locale

val QuickTimerPresets = listOf(
    1L to "1 мин",
    5L to "5 мин",
    10L to "10 мин",
    15L to "15 мин",
    30L to "30 мин",
    60L to "1 час"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    timerViewModel: TimerViewModel,
    onOpenSettings: () -> Unit = {},
    onTimerStateChanged: (isRunning: Boolean, action: () -> Unit) -> Unit = { _, _ -> }
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    val timersList by timerViewModel.timersList.collectAsState()
    val viewMode by timerViewModel.viewMode.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onTimerStateChanged(false) {
            showBottomSheet = true
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.35f))
            )
        }
        ) {
            NewTimerSetupSheet(
                accentColor = accentColor,
                themeConfig = themeConfig,
                onAdd = { hours, minutes, seconds, label ->
                    val totalSec = (hours * 3600L) + (minutes * 60L) + seconds
                    if (totalSec > 0) {
                        timerViewModel.addTimerToChain(label, totalSec)
                    }
                    showBottomSheet = false
                },
                onStart = { hours, minutes, seconds, label ->
                    val totalSec = (hours * 3600L) + (minutes * 60L) + seconds
                    if (totalSec > 0) {
                        val beforeIds = timersList.map { it.id }.toSet()
                        timerViewModel.addTimerToChain(label, totalSec)
                        val newTimer = timerViewModel.timersList.value.firstOrNull { !beforeIds.contains(it.id) }
                        if (newTimer != null) {
                            timerViewModel.toggleSingleTimer(newTimer.id)
                        }
                    }
                    showBottomSheet = false
                },
                onCancel = { showBottomSheet = false }
            )
        }
    }

    AnimatedContent(
        targetState = timersList.isEmpty(),
    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
    label = "TimerEmptyStateTransition"
    ) { isEmpty ->
        if (isEmpty) {
            EmptyTimerState(onAddTimerClick = { showBottomSheet = true })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                    text = "АКТИВНЫЕ ТАЙМЕРЫ (${timersList.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
                )

                IconButton(
                    onClick = { timerViewModel.toggleViewMode() },
                modifier = Modifier.bounceClick()
                ) {
                Icon(
                    imageVector = if (viewMode == TimerViewMode.CAROUSEL) Icons.Default.GridView else Icons.Default.ViewDay,
                contentDescription = "Режим отображения",
                tint = accentColor
                )
            }
            }

                AnimatedContent(
                    targetState = viewMode == TimerViewMode.CAROUSEL,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "TimerViewModeTransition"
                ) { isTileView ->
                if (isTileView) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                    ) {
                        itemsIndexed(timersList, key = { _, item -> item.id }) { index, item ->
                            var isVisible by remember { mutableStateOf(true) }

                            AnimatedVisibility(
                                visible = isVisible,
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f)
                            ) {
                                Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                    TimerTileCard(
                                        item = item,
                                    accentColor = accentColor,
                                    themeConfig = themeConfig,
                                    onToggle = { timerViewModel.toggleSingleTimer(item.id) },
                                    onReset = { timerViewModel.resetSingleTimer(item.id) },
                                    onDelete = {
                                        isVisible = false
                                        timerViewModel.deleteTimer(item.id)
                                    }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                    ) {
                        itemsIndexed(timersList, key = { _, item -> item.id }) { index, item ->
                            var isVisible by remember { mutableStateOf(true) }

                            AnimatedVisibility(
                                visible = isVisible,
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                            ) {
                                Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                    TimerGridItemCard(
                                        item = item,
                                    accentColor = accentColor,
                                    themeConfig = themeConfig,
                                    onToggle = { timerViewModel.toggleSingleTimer(item.id) },
                                    onReset = { timerViewModel.resetSingleTimer(item.id) },
                                    onDelete = {
                                        isVisible = false
                                        timerViewModel.deleteTimer(item.id)
                                    }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ДИНАМИЧЕСКИЙ БЛОК НАСТРОЙКИ
@Composable
private fun NewTimerSetupSheet(
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig,
    onAdd: (hours: Int, minutes: Int, seconds: Int, label: String) -> Unit,
    onStart: (hours: Int, minutes: Int, seconds: Int, label: String) -> Unit,
    onCancel: () -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var labelText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    var activeEditingField by remember { mutableIntStateOf(-1) }

    val totalSeconds = remember(hours, minutes, seconds) {
        (hours * 3600L) + (minutes * 60L) + seconds
    }

    val cancelColor = Color(0xFFFF5252)
    val addColor = remember(accentColor) {
        val gray = Color(0xFF888E96)
        Color(
            red = (accentColor.red * 0.45f + gray.red * 0.55f),
        green = (accentColor.green * 0.45f + gray.green * 0.55f),
        blue = (accentColor.blue * 0.45f + gray.blue * 0.55f),
        alpha = 1f
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    .padding(horizontal = 20.dp, vertical = 6.dp)
    .navigationBarsPadding(),
    horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Быстрые прибавки
        Row(
            modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        val increments = listOf(1 to "+1 мин", 5 to "+5 мин", 15 to "+15 мин")
        increments.forEach { (minsToAdd, title) ->
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val totalMins = (hours * 60) + minutes + minsToAdd
                    hours = (totalMins / 60).coerceAtMost(23)
                    minutes = (totalMins % 60)
                },
                shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
            .bounceClick(),
            contentPadding = PaddingValues(vertical = 6.dp)
            ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                hours = 0
                minutes = 0
                seconds = 0
            },
            modifier = Modifier
                .size(40.dp)
        .bounceClick()
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
        Icon(Icons.Default.RestartAlt, contentDescription = "Сбросить", tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
    }

        Spacer(modifier = Modifier.height(12.dp))

        // Пресеты времени
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
        ) {
        items(QuickTimerPresets) { (presetMin, title) ->
            val isSelected = (hours == (presetMin / 60).toInt()) && (minutes == (presetMin % 60).toInt()) && (seconds == 0)

            Box(
                modifier = Modifier
                    .bounceClick()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
            .border(
            1.dp,
            if (isSelected) accentColor else accentColor.copy(alpha = 0.15f),
            RoundedCornerShape(14.dp)
            )
            .clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hours = (presetMin / 60).toInt()
            minutes = (presetMin % 60).toInt()
            seconds = 0
        }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
            Text(
                text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
        }
    }

        Spacer(modifier = Modifier.height(20.dp))

        // Увеличенные карточки ввода
        Row(
            modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
        ) {
        InteractiveDigitPicker(
            unitTitle = "ЧАС",
        value = hours,
        range = 0..23,
        isFocused = activeEditingField == 0,
        accentColor = accentColor,
        onFocusGained = { activeEditingField = 0 },
        onFocusLost = { if (activeEditingField == 0) activeEditingField = -1 },
        onValueChange = { hours = it }
        )

        Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accentColor.copy(alpha = 0.5f))

        InteractiveDigitPicker(
            unitTitle = "МИН",
        value = minutes,
        range = 0..59,
        isFocused = activeEditingField == 1,
        accentColor = accentColor,
        onFocusGained = { activeEditingField = 1 },
        onFocusLost = { if (activeEditingField == 1) activeEditingField = -1 },
        onValueChange = { minutes = it }
        )

        Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accentColor.copy(alpha = 0.5f))

        InteractiveDigitPicker(
            unitTitle = "СЕК",
        value = seconds,
        range = 0..59,
        isFocused = activeEditingField == 2,
        accentColor = accentColor,
        onFocusGained = { activeEditingField = 2 },
        onFocusLost = { if (activeEditingField == 2) activeEditingField = -1 },
        onValueChange = { seconds = it }
        )
    }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = labelText,
        onValueChange = { labelText = it },
        placeholder = { Text("Название (например: Паста, Чай, Тренировка)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3 Кнопки действий
        Row(
            modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        OutlinedButton(
            onClick = onCancel,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cancelColor),
        border = BorderStroke(1.dp, cancelColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .weight(1f)
        .height(52.dp)
        .bounceClick(),
        shape = RoundedCornerShape(16.dp)
        ) {
        Text("Отмена", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }

        Button(
            onClick = { onAdd(hours, minutes, seconds, labelText) },
        enabled = totalSeconds > 0,
        colors = ButtonDefaults.buttonColors(
            containerColor = addColor,
        contentColor = Color.White,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .weight(1.1f)
        .height(52.dp)
        .bounceClick(),
        shape = RoundedCornerShape(16.dp)
        ) {
        Text("Добавить", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }

        Button(
            onClick = { onStart(hours, minutes, seconds, labelText) },
        enabled = totalSeconds > 0,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
        contentColor = Color.Black,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .weight(1.2f)
        .height(52.dp)
        .bounceClick(),
        shape = RoundedCornerShape(16.dp)
        ) {
        Text("Запустить", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
    }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// УВЕЛИЧЕННЫЙ ИНТЕРАКТИВНЫЙ БАРАБАН
@Composable
private fun InteractiveDigitPicker(
    unitTitle: String,
    value: Int,
    range: IntRange,
    isFocused: Boolean,
    accentColor: Color,
    onFocusGained: () -> Unit,
    onFocusLost: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isIncrementing by remember { mutableStateOf(true) }
    var textInput by remember { mutableStateOf(TextFieldValue(String.format(Locale.ROOT, "%02d", value))) }
    val focusRequester = remember { FocusRequester() }

    val animatedDigitColor by animateColorAsState(
        targetValue = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "DigitColorTransition"
    )

    val animatedDigitScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
    ),
    label = "DigitScaleTransition"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) accentColor else Color.White.copy(alpha = 0.08f),
    animationSpec = tween(250),
    label = "CardBorderColor"
    )

    LaunchedEffect(value) {
        if (!isFocused) {
            textInput = TextFieldValue(
                text = String.format(Locale.ROOT, "%02d", value),
            selection = TextRange(2)
            )
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(50)
            focusRequester.requestFocus()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = unitTitle,
            fontSize = 12.sp,
            color = if (isFocused) accentColor else Color.Gray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(96.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    )
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = animatedBorderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isIncrementing = true
                        onFocusLost()
                        if (value < range.last) onValueChange(value + 1) else onValueChange(range.first)
                    },
                    modifier = Modifier.size(34.dp).bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Увеличить",
                        tint = if (isFocused) accentColor else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .clickable {
                            onFocusGained()
                            val textStr = String.format(Locale.ROOT, "%02d", value)
                            textInput = TextFieldValue(
                                text = textStr,
                                selection = TextRange(0, textStr.length)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isFocused) {
                        BasicTextField(
                            value = textInput,
                            onValueChange = { newText ->
                                val digitsOnly = newText.text.filter { it.isDigit() }.take(2)
                                textInput = newText.copy(
                                    text = digitsOnly,
                                    selection = TextRange(digitsOnly.length)
                                )
                                val parsed = digitsOnly.toIntOrNull()
                                if (parsed != null && parsed in range) {
                                    onValueChange(parsed)
                                }
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(accentColor),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = animatedDigitColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val finalVal = textInput.text.toIntOrNull()?.coerceIn(range) ?: value
                                    onValueChange(finalVal)
                                    onFocusLost()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .graphicsLayer {
                                    scaleX = animatedDigitScale
                                    scaleY = animatedDigitScale
                                }
                        )
                    } else {
                        AnimatedContent(
                            targetState = value,
                            transitionSpec = {
                                if (isIncrementing) {
                                    (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                                } else {
                                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                                }
                            },
                            label = "DigitRollAnimation"
                        ) { targetNum ->
                            Text(
                                text = String.format(Locale.ROOT, "%02d", targetNum),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = animatedDigitColor,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = animatedDigitScale
                                    scaleY = animatedDigitScale
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isIncrementing = false
                        onFocusLost()
                        if (value > range.first) onValueChange(value - 1) else onValueChange(range.last)
                    },
                    modifier = Modifier.size(34.dp).bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Уменьшить",
                        tint = if (isFocused) accentColor else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// УЛУЧШЕННАЯ КРУПНАЯ 3D-КАРТОЧКА
@Composable
private fun TimerTileCard(
    item: TimerItem,
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = item.state == TimerState.RUNNING
    val isCompleted = item.state == TimerState.COMPLETED
    val activeColor = when {
        isCompleted -> Color(0xFFFFB74D)
        isRunning -> accentColor
        else -> Color.Gray.copy(alpha = 0.45f)
    }

    // 60-FPS непрерывная плавная интерполяция шкалы отсчёта
    val targetProgress = remember(item.remainingMillis, item.initialTimeSeconds) {
        val totalMs = item.initialTimeSeconds * 1000f
        if (totalMs <= 0f) 0f else (item.remainingMillis.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "TileProgressSmooth"
    )

    val formattedTime = remember(item.remainingSeconds) {
        val hours = item.remainingSeconds / 3600
        val minutes = (item.remainingSeconds % 3600) / 60
        val seconds = item.remainingSeconds % 60
        if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    .pure3DEffect(
        shape = RoundedCornerShape(30.dp),
        accentColor = if (isRunning) accentColor else if (isCompleted) Color(0xFFFFB74D) else Color.DarkGray,
        depthDp = themeConfig.depthIntensityDp + if (isRunning) 2.dp else 0.dp,
        is3dEnabled = themeConfig.is3dEnabled,
    isGlowEnabled = isRunning && themeConfig.isGlowEnabled,
    surfaceColor = MaterialTheme.colorScheme.surface
    )
    .padding(20.dp),
    contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(174.dp)
                    .then(if (isRunning) Modifier.breathingGlow(accentColor, minAlpha = 0.06f, maxAlpha = 0.22f) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Фоновая направляющая дуга
                    drawArc(
                        color = activeColor.copy(alpha = 0.12f),
                        startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Анимированная дуга оставшегося времени
                    drawArc(
                        color = activeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RollingTimeText(
                        formattedTime = formattedTime,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Панель управления с большими кнопками (хитбоксы 48-56 dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка: Сброс
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сброс",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Кнопка: Старт / Пауза (главная увеличенная кнопка 58 dp)
                FloatingActionButton(
                    onClick = onToggle,
                containerColor = if (isRunning) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isRunning) Color.Black else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .bounceClick()
                ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Старт/Пауза",
                modifier = Modifier.size(28.dp)
                )
            }

                // Кнопка: Удалить
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// УЛУЧШЕННАЯ СПИСОЧНАЯ КАРТОЧКА (ЭРГОНОМИЧНЫЕ КНОПКИ БЕЗ ПРОМАХОВ)
@Composable
private fun TimerGridItemCard(
    item: TimerItem,
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = item.state == TimerState.RUNNING
    val isCompleted = item.state == TimerState.COMPLETED
    val titleColor =
        if (isRunning) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)

    val formattedTime = remember(item.remainingSeconds) {
        val minutes = (item.remainingSeconds % 3600) / 60
        val seconds = item.remainingSeconds % 60
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = RoundedCornerShape(22.dp),
                accentColor = if (isRunning) accentColor else Color.Transparent,
                depthDp = if (isRunning) themeConfig.depthIntensityDp else 2.dp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = isRunning && themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Информация о таймере
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (item.state) {
                        TimerState.RUNNING -> "Идёт отсчёт"
                        TimerState.PAUSED -> "Пауза"
                        TimerState.COMPLETED -> "Готово"
                        else -> "Ожидание"
                    },
                    fontSize = 11.sp,
                    color = if (isRunning) accentColor.copy(alpha = 0.85f) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            // Цифровой счетчик
            Text(
                text = formattedTime,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Панель действий: кнопки разнесены и увеличены до 46 dp для уверенного тапа
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка Сброс (46 dp)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onReset() }
                        .bounceClick(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сброс",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка Старт/Пауза (46 dp, акцентная)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isRunning) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggle() }
                        .bounceClick(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Старт/Пауза",
                        tint = if (isRunning) Color.Black else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Кнопка Удалить (46 dp, красный фон с защитным отступом)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .clickable { onDelete() }
                        .bounceClick(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

    // ============================================================================
// КОМПОНЕНТ БАРАБАННОГО НАКАТА ЦИФР (СВЕРХУ-ВНИЗ)
// ============================================================================
    @Composable
    private fun RollingTimeText(
        formattedTime: String,
        fontSize: androidx.compose.ui.unit.TextUnit,
        fontWeight: FontWeight = FontWeight.Bold,
        color: Color = MaterialTheme.colorScheme.onSurface,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            formattedTime.forEachIndexed { index, char ->
                if (char == ':') {
                    Text(
                        text = ":",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color.copy(alpha = 0.6f)
                    )
                } else {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
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
                        label = "DigitRoll_$index"
                    ) { targetChar ->
                        Text(
                            text = targetChar.toString(),
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            color = color
                        )
                    }
                }
            }
        }
    }
