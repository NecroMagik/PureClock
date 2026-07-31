package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.staggeredEntrance
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import kotlinx.coroutines.delay
import java.util.Locale

// ============================================================================
// СЕКЦИЯ 1: ЭКРАН СЕКУНДОМЕРА И ТАЙМЕР МИЛЛИСЕКУНД
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    onOpenSettings: () -> Unit = {},
    onStopwatchStateChanged: (isRunning: Boolean, action: () -> Unit) -> Unit = { _, _ -> }
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    var elapsedTimeMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    val lapTimes = remember { mutableStateListOf<Long>() }

    LaunchedEffect(isRunning) {
        var lastTime = System.currentTimeMillis()
        while (isRunning) {
            delay(10L)
            val now = System.currentTimeMillis()
            elapsedTimeMs += (now - lastTime)
            lastTime = now
        }
    }

    LaunchedEffect(isRunning) {
        onStopwatchStateChanged(isRunning) {
            if (isRunning) {
                lapTimes.add(0, elapsedTimeMs)
            } else {
                isRunning = true
            }
        }
    }

    fun formatTime(timeMs: Long): Triple<String, String, String> {
        val minutes = (timeMs / 1000) / 60
        val seconds = (timeMs / 1000) % 60
        val millis = (timeMs % 1000) / 10

        return Triple(
            String.format(Locale.ROOT, "%02d", minutes),
            String.format(Locale.ROOT, "%02d", seconds),
            String.format(Locale.ROOT, "%02d", millis)
        )
    }

    val (minStr, secStr, msStr) = formatTime(elapsedTimeMs)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Секундомер", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

// ============================================================================
// СЕКЦИЯ 2: ЦИФРОВОЕ ТАБЛО СЕКУНДОМЕРА И ИНФОРМЕР КРУГОВ
// ============================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .staggeredEntrance(index = 0),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$minStr:$secStr",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-2).sp
                    )
                    Text(
                        text = ".$msStr",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .staggeredEntrance(index = 1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lapTimes.isNotEmpty()) "Кругов: ${lapTimes.size}" else "",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                if (elapsedTimeMs > 0 && !isRunning) {
                    IconButton(
                        onClick = {
                            elapsedTimeMs = 0L
                            lapTimes.clear()
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Сброс",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

// ============================================================================
// СЕКЦИЯ 3: СПИСОК КРУГОВ (LAP TIMES LIST)
// ============================================================================
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(lapTimes) { index, lapMs ->
                    val (lMin, lSec, lMs) = formatTime(lapMs)
                    val lapNum = lapTimes.size - index

                    Card(
                        shape = RoundedCornerShape(themeConfig.cardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Круг $lapNum",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Text(
                                text = "$lMin:$lSec.$lMs",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (index == 0) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}