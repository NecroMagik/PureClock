package com.necromagik.pureclock.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.WorldClockRepository
import com.necromagik.pureclock.data.repository.ScenaryRepository
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(
    onBack: () -> Unit,
    onTriggerDockUnlockAnimation: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scenaryRepository = remember { ScenaryRepository.getInstance(context) }
    val ifRelease by settingsManager.ifReleaseFlow.collectAsState()
    val scenarios by scenaryRepository.scenariosFlow.collectAsState()

    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { WorldClockRepository(db.cityDao(), context) }

    var citiesCount by remember { mutableIntStateOf(0) }
    var alarmsCount by remember { mutableIntStateOf(0) }
    var isBackupBusy by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var showRestartAfterRestoreDialog by remember { mutableStateOf(false) }

    val installSource = remember { settingsManager.getInstallationSource() }

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

    // Лаунчер экспорта резервной копии (.json)
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isBackupBusy = true
            scope.launch {
                val result = BackupManager.exportBackupToUri(context, uri)
                isBackupBusy = false
                result.onSuccess { count ->
                    val msg = "Бэкап сохранён (будильников: $count, сценариев: ${scenarios.size})"
                    backupStatusMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    val msg = "Ошибка экспорта: ${err.localizedMessage}"
                    backupStatusMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Лаунчер импорта резервной копии (.json)
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isBackupBusy = true
            scope.launch {
                val result = BackupManager.importBackupFromUri(context, uri)
                isBackupBusy = false
                result.onSuccess { summary ->
                    settingsManager.reloadFromPreferences()
                    refreshCounts()
                    backupStatusMessage = "Успешно: $summary"
                    showRestartAfterRestoreDialog = true
                }.onFailure { err ->
                    val msg = "Ошибка импорта: ${err.localizedMessage}"
                    backupStatusMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

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
            // =========================================================================
            // СЕКЦИЯ 1: СОСТОЯНИЕ СИСТЕМЫ И БАЗ ДАННЫХ (НАВЕРХУ)
            // =========================================================================
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
                        InfoRow(label = "Статус IfRelease", value = "$ifRelease (${if (ifRelease == 1) "PUBLIC" else "HIDDEN"})")
                        InfoRow(label = "Записей городов (cities)", value = "$citiesCount шт.")
                        InfoRow(label = "Сохранённых будильников", value = "$alarmsCount шт.")
                        InfoRow(label = "Сохранённых сценариев", value = "${scenarios.size} шт.")
                        InfoRow(label = "Стиль времени будильника", value = settingsManager.selectedAlarmPickerStyle.name)
                        InfoRow(label = "Шрифт цифр", value = "OpenType tnum")
                    }
                }
            }

            // =========================================================================
            // СЕКЦИЯ 2: ИЗМЕНЕНИЯ ДОК-БАРА
            // =========================================================================
            item {
                Text(
                    text = "ИЗМЕНЕНИЯ ДОК-БАРА",
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
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Новый дизайн",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (ifRelease == 1) "Активен" else "Не активен",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            PureSwitch(
                                checked = ifRelease == 1,
                                onCheckedChange = { enabled ->
                                    settingsManager.ifRelease = if (enabled) 1 else 0
                                }
                            )
                        }

                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                        Button(
                            onClick = {
                                settingsManager.ifRelease = 1
                                onTriggerDockUnlockAnimation()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Проиграть анимацию внедрения в док-бар",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // СЕКЦИЯ 3: РЕЗЕРВНОЕ КОПИРОВАНИЕ И ВОССТАНОВЛЕНИЕ
            // =========================================================================
            item {
                Text(
                    text = "РЕЗЕРВНОЕ КОПИРОВАНИЕ И ВОССТАНОВЛЕНИЕ",
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Полный слепок PureClock (.json)",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Сохраняет будильники Room, сценарии, таймеры, города и настройки Theme Engine перед переустановкой",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        backupStatusMessage?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accentColor.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = status,
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                enabled = !isBackupBusy,
                                onClick = {
                                    exportBackupLauncher.launch(BackupManager.generateDefaultBackupFileName())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .bounceClick()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Экспорт", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                enabled = !isBackupBusy,
                                onClick = {
                                    importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .bounceClick()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Импорт",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // СЕКЦИЯ 4: ОЧИСТКА И ПЕРЕСБОРКА ТАБЛИЦ
            // =========================================================================
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
                    icon = Icons.Default.CleaningServices,
                    title = "Очистить фантомные виджеты",
                    subtitle = "Удаляет из памяти RemoteViews невидимых призраков лаунчера",
                    buttonText = "Очистить",
                    accentColor = Color(0xFFFFB74D),
                    shape = cardShape,
                    onAction = {
                        val purged = com.necromagik.pureclock.widget.PureClockWidgetProvider.purgeGhostWidgetsMemory(context)
                        refreshCounts()
                        Toast.makeText(
                            context,
                            if (purged > 0) "Очищено фантомов: $purged" else "Фантомных виджетов не найдено",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                            .edit { clear() }
                        context.getSharedPreferences("pure_clock_db_sync", Context.MODE_PRIVATE)
                            .edit { clear() }
                        settingsManager.reloadFromPreferences()
                        Toast.makeText(context, "Настройки сброшены", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (showRestartAfterRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestartAfterRestoreDialog = false },
            title = {
                Text("Резервная копия восстановлена", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Все будильники, сценарии, таймеры и настройки успешно импортированы.\n\n" +
                            "Перезапустить интерфейс сейчас, чтобы все репозитории и цвета обновились?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartAfterRestoreDialog = false
                        val activity = context as? Activity
                        val restartIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(restartIntent)
                        activity?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                ) {
                    Text("Перезапустить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartAfterRestoreDialog = false }) {
                    Text("Позже", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
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