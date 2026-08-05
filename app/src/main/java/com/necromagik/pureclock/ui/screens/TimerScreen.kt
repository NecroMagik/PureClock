package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.breathingGlow
import com.necromagik.pureclock.ui.animation.staggeredEntrance
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.TimerItem
import com.necromagik.pureclock.ui.viewmodel.TimerState
import com.necromagik.pureclock.ui.viewmodel.TimerViewModel
import com.necromagik.pureclock.ui.viewmodel.TimerViewMode
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

    // Передаем действие открытия панели взвода в FAB на MainScreen
    LaunchedEffect(Unit) {
        onTimerStateChanged(false) {
            showBottomSheet = true
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.4f))
                )
            }
        ) {
            NewTimerSetupSheet(
                accentColor = accentColor,
                onConfirm = { hours, minutes, seconds, label ->
                    val totalSec = (hours * 3600L) + (minutes * 60L) + seconds
                    if (totalSec > 0) {
                        timerViewModel.addTimerToChain(label, totalSec)
                    }
                    showBottomSheet = false
                },
                onCancel = { showBottomSheet = false }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ШАПКА ПЕРЕКЛЮЧЕНИЯ
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

        // АНИМИРОВАННЫЙ ПЕРЕХОД СЕТКА - СПИСОК (Аналогично AlarmListScreen)
        AnimatedContent(
            targetState = viewMode == TimerViewMode.CAROUSEL,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "TimerViewModeTransition"
        ) { isTileView ->
            if (isTileView) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    itemsIndexed(timersList, key = { _, item -> item.id }) { index, item ->
                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                            TimerTileCard(
                                item = item,
                                canDelete = timersList.size > 1,
                                accentColor = accentColor,
                                themeConfig = themeConfig,
                                onToggle = { timerViewModel.toggleSingleTimer(item.id) },
                                onReset = { timerViewModel.resetSingleTimer(item.id) },
                                onDelete = { timerViewModel.deleteTimer(item.id) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                ) {
                    itemsIndexed(timersList, key = { _, item -> item.id }) { index, item ->
                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                            TimerGridItemCard(
                                item = item,
                                canDelete = timersList.size > 1,
                                accentColor = accentColor,
                                themeConfig = themeConfig,
                                onToggle = { timerViewModel.toggleSingleTimer(item.id) },
                                onReset = { timerViewModel.resetSingleTimer(item.id) },
                                onDelete = { timerViewModel.deleteTimer(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ИНТЕРАКТИВНЫЙ БЛОК ВЗВОДА (BOTTOM SHEET)
@Composable
private fun NewTimerSetupSheet(
    accentColor: Color,
    onConfirm: (hours: Int, minutes: Int, seconds: Int, label: String) -> Unit,
    onCancel: () -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var labelText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ВЗВОД ТАЙМЕРА",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it },
            placeholder = { Text("Название (например: Паста, Варка яйца)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

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
                            if (isSelected) accentColor else accentColor.copy(alpha = 0.2f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
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

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelDigitPicker("ЧАС", hours, 0..23) { hours = it }
            Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accentColor)
            WheelDigitPicker("МИН", minutes, 0..59) { minutes = it }
            Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accentColor)
            WheelDigitPicker("СЕК", seconds, 0..59) { seconds = it }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .bounceClick(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Отмена", color = Color.Gray)
            }

            Button(
                onClick = { onConfirm(hours, minutes, seconds, labelText) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier
                    .weight(1f)
                    .bounceClick(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Запустить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun WheelDigitPicker(
    unitTitle: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(unitTitle, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { if (value < range.last) onValueChange(value + 1) else onValueChange(range.first) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("▲", fontSize = 10.sp, color = Color.Gray)
                }

                Text(
                    text = String.format(Locale.ROOT, "%02d", value),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { if (value > range.first) onValueChange(value - 1) else onValueChange(range.last) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("▼", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

// КРУПНАЯ 3D-КАРТОЧКА
@Composable
private fun TimerTileCard(
    item: TimerItem,
    canDelete: Boolean,
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = item.state == TimerState.RUNNING
    val activeColor = if (isRunning) accentColor else Color.Gray.copy(alpha = 0.4f)

    val progress = remember(item.remainingSeconds, item.initialTimeSeconds) {
        if (item.initialTimeSeconds == 0L) 0f else item.remainingSeconds.toFloat() / item.initialTimeSeconds.toFloat()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "TileProgress"
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
                shape = RoundedCornerShape(28.dp),
                accentColor = if (isRunning) accentColor else Color.DarkGray,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = isRunning && themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .then(if (isRunning) Modifier.breathingGlow(accentColor, minAlpha = 0.05f, maxAlpha = 0.20f) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = activeColor.copy(alpha = 0.15f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

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
                        fontWeight = FontWeight.Medium,
                        color = if (isRunning) accentColor else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onReset, modifier = Modifier.bounceClick()) {
                    Icon(Icons.Default.Refresh, contentDescription = "Сброс", tint = Color.Gray)
                }

                FloatingActionButton(
                    onClick = onToggle,
                    containerColor = if (isRunning) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isRunning) Color.Black else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Старт/Пауза"
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = if (canDelete) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// КОМПАКТНАЯ КАРТОЧКА
@Composable
private fun TimerGridItemCard(
    item: TimerItem,
    canDelete: Boolean,
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = item.state == TimerState.RUNNING
    val titleColor = if (isRunning) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    val formattedTime = remember(item.remainingSeconds) {
        val minutes = (item.remainingSeconds % 3600) / 60
        val seconds = item.remainingSeconds % 60
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = RoundedCornerShape(20.dp),
                accentColor = if (isRunning) accentColor else Color.Transparent,
                depthDp = if (isRunning) themeConfig.depthIntensityDp else 2.dp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = isRunning && themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = when (item.state) {
                        TimerState.RUNNING -> "Идёт отсчёт"
                        TimerState.PAUSED -> "Пауза"
                        TimerState.COMPLETED -> "Готово"
                        else -> "Ожидание"
                    },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = formattedTime,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp).bounceClick()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp).bounceClick()) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isRunning) accentColor else Color.Gray
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.size(32.dp).bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (canDelete) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}