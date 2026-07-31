package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import java.util.Locale

val QuickTimerPresets = listOf(
    1 to "1 мин",
    5 to "5 мин",
    10 to "10 мин",
    15 to "15 мин",
    30 to "30 мин"
)

// ============================================================================
// СЕКЦИЯ 1: ИНИЦИАЛИЗАЦИЯ И КРУГОВОЙ CANVAS ПРОГРЕСС-БАР ТАЙМЕРА
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onOpenSettings: () -> Unit = {},
    onTimerStateChanged: (isRunning: Boolean, action: () -> Unit) -> Unit = { _, _ -> }
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    var totalTimeSeconds by remember { mutableLongStateOf(300L) }
    var remainingSeconds by remember { mutableLongStateOf(300L) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        onTimerStateChanged(isRunning) {
            isRunning = !isRunning
        }
    }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        } else if (remainingSeconds == 0L) {
            isRunning = false
        }
    }

    val progress = remember(remainingSeconds, totalTimeSeconds) {
        if (totalTimeSeconds == 0L) 0f else remainingSeconds.toFloat() / totalTimeSeconds.toFloat()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "TimerProgress"
    )

    val formattedTime = remember(remainingSeconds) {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60

        if (hours > 0) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Таймер", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.bounceClick()) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .staggeredEntrance(index = 0)
                    .then(if (isRunning) Modifier.breathingGlow(accentColor, minAlpha = 0.05f, maxAlpha = 0.20f) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = accentColor.copy(alpha = 0.15f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    val sweepAngle = 360f * animatedProgress
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (isRunning || remainingSeconds != totalTimeSeconds) {
                        Text(
                            text = if (isRunning) "Отсчёт идет" else "Пауза",
                            fontSize = 13.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

// ============================================================================
// СЕКЦИЯ 2: БЫСТРЫЕ ПРЕСЕТЫ (QUICK PRESETS CHIPS)
// ============================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index = 1),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "БЫСТРЫЙ ВЫБОР",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(QuickTimerPresets) { (minutes, title) ->
                        FilterChip(
                            selected = (totalTimeSeconds == minutes * 60L && !isRunning),
                            onClick = {
                                isRunning = false
                                totalTimeSeconds = minutes * 60L
                                remainingSeconds = minutes * 60L
                            },
                            label = { Text(title, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.bounceClick(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

// ============================================================================
// СЕКЦИЯ 3: КНОПКИ УПРАВЛЕНИЯ ТАЙМЕРОМ (СБРОСИ И ДОБАВИТЬ МИНУТУ)
// ============================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp)
                    .staggeredEntrance(index = 2),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isRunning = false
                        remainingSeconds = totalTimeSeconds
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сброс",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        remainingSeconds += 60L
                        totalTimeSeconds += 60L
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .bounceClick()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+1 мин",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "1m",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }
        }
    }
}