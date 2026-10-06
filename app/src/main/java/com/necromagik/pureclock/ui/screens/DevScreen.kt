package com.necromagik.pureclock.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.WorldClockRepository
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { WorldClockRepository(db.cityDao(), context) }

    var citiesCount by remember { mutableIntStateOf(0) }
    var alarmsCount by remember { mutableIntStateOf(0) }
    val installSource = remember { settingsManager.getInstallationSource() }

    // --- Предиктивный системный жест "Назад" (Android 13/14+) ---
    var predictiveProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { backEvent ->
                predictiveProgress = backEvent.progress
            }
            onBack()
        } catch (_: CancellationException) {
            // Жест отменён пользователем
        } finally {
            predictiveProgress = 0f
        }
    }

    val backScale = 1f - (predictiveProgress * 0.08f)
    val backCornerRadius = (predictiveProgress * 28.dp.value).dp

    fun refreshCounts() {
        scope.launch {
            val c = withContext(Dispatchers.IO) { db.cityDao().getCount() }
            val a = withContext(Dispatchers.IO) {
                try {
                    db.alarmDao().getAllAlarms().firstOrNull()?.size ?: 0
                } catch (_: Exception) {
                    0
                }
            }
            citiesCount = c
            alarmsCount = a
        }
    }

    LaunchedEffect(Unit) {
        refreshCounts()
    }

    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = backScale
                scaleY = backScale
                clip = predictiveProgress > 0f
                shape = RoundedCornerShape(backCornerRadius)
            }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = accentColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Панель разработчика", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                item {
                    Text(
                        text = "СОСТОЯНИЕ СИСТЕМЫ И БАЗ ДАННЫХ",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pure3DEffect(
                                shape = cardShape,
                                accentColor = accentColor,
                                depthDp = themeConfig.depthIntensityDp,
                                is3dEnabled = themeConfig.is3dEnabled,
                                isGlowEnabled = themeConfig.isGlowEnabled,
                                surfaceColor = MaterialTheme.colorScheme.surface
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoRow(label = "Источник установки", value = installSource.title)
                            InfoRow(label = "Записей городов (cities)", value = "$citiesCount шт.")
                            InfoRow(label = "Сохранённых будильников", value = "$alarmsCount шт.")
                            InfoRow(label = "Стиль времени будильника", value = settingsManager.selectedAlarmPickerStyle.name)
                            InfoRow(label = "Шрифт цифр", value = "OpenType tnum")
                        }
                    }
                }

                item {
                    Text(
                        text = "ОЧИСТКА И ПЕРЕСБОРКА ТАБЛИЦ",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                item {
                    DevActionCard(
                        icon = Icons.Default.LocationCity,
                        title = "Пересобрать базу городов",
                        subtitle = "Очистит таблицу cities и наполнит её заново с русской локализацией",
                        buttonText = "Пересоздать",
                        accentColor = accentColor,
                        shape = cardShape,
                        onAction = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    db.cityDao().clearAll()
                                    repository.searchCities("")
                                }
                                refreshCounts()
                                Toast.makeText(context, "База городов успешно пересобрана", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    DevActionCard(
                        icon = Icons.Default.DeleteSweep,
                        title = "Очистить все города",
                        subtitle = "Полностью очищает таблицу cities без автозаполнения",
                        buttonText = "Очистить",
                        accentColor = Color(0xFFFF5252),
                        shape = cardShape,
                        onAction = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    db.cityDao().clearAll()
                                }
                                refreshCounts()
                                Toast.makeText(context, "Таблица cities очищена", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    DevActionCard(
                        icon = Icons.Default.AlarmOff,
                        title = "Очистить базу будильников",
                        subtitle = "Удалит все созданные будильники из базы Room",
                        buttonText = "Удалить",
                        accentColor = Color(0xFFFF5252),
                        shape = cardShape,
                        onAction = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val all = db.alarmDao().getAllAlarms().firstOrNull() ?: emptyList()
                                    for (alarm in all) {
                                        db.alarmDao().deleteAlarm(alarm)
                                    }
                                }
                                refreshCounts()
                                Toast.makeText(context, "Все будильники удалены", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    DevActionCard(
                        icon = Icons.Default.RestartAlt,
                        title = "Сброс SharedPreferences",
                        subtitle = "Сбросит все настройки стилей, цветов и параметров будильника по умолчанию",
                        buttonText = "Сбросить",
                        accentColor = Color(0xFFFFB74D),
                        shape = cardShape,
                        onAction = {
                            context.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply()
                            context.getSharedPreferences("pure_clock_db_sync", Context.MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply()
                            Toast.makeText(context, "Настройки сброшены. Перезапустите экран", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DevActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    accentColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onAction: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = shape,
                accentColor = accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.bounceClick()
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}