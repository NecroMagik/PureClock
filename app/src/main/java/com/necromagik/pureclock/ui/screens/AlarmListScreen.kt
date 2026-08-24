package com.necromagik.pureclock.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.staggeredEntrance
import com.necromagik.pureclock.ui.components.EmptyAlarmState
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.util.PermissionHelper
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RU_LOCALE = Locale.forLanguageTag("ru")

private val OFF_QUOTES = listOf(
    "Все будильники спят. И тебе пора! 💤",
    "Спи спокойно, сладкая булочка! Никаких тревог 😴",
    "Режим полнейшего релакса активирован 🧘‍♂️",
    "Будильники берут выходной. Тишина... 🛌",
    "Никто тебя не разбудит. Спи сколько хочешь! ✨"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    alarms: List<AlarmEntity>,
    onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
    onSkipNextAlarm: (AlarmEntity, Long) -> Unit = { _, _ -> },
    onDeleteAlarms: (List<AlarmEntity>) -> Unit,
    onAddAlarmClick: () -> Unit,
    onEditAlarmClick: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val scheduler = remember { AlarmScheduler(context) }

    val prefs = remember(context) { context.getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE) }
    var isCardView by remember {
        mutableStateOf(prefs.getBoolean("is_alarm_card_view", true))
    }

    var selectedAlarmIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedAlarmIds.isNotEmpty()
    var alarmToDisable by remember { mutableStateOf<AlarmEntity?>(null) }

    LaunchedEffect(alarms) {
        selectedAlarmIds = emptySet()
    }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    var isAllPermissionsGranted by remember {
        mutableStateOf(PermissionHelper.hasAllRequiredPermissions(context))
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        isAllPermissionsGranted = PermissionHelper.hasAllRequiredPermissions(context)
        onDispose {}
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val randomQuote = remember(alarms.none { it.isEnabled }) { OFF_QUOTES.random() }

    val nearestActiveAlarmInfo = remember(alarms, nowMillis) {
        val enabledAlarms = alarms.filter { it.isEnabled }
        if (enabledAlarms.isEmpty()) null
        else {
            enabledAlarms
                .map { alarm -> alarm to scheduler.calculateTriggerTime(alarm) }
                .filter { it.second > nowMillis }
                .minByOrNull { it.second }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TopAppBar(
                    title = { Text("Выбрано: ${selectedAlarmIds.size}", color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = { selectedAlarmIds = emptySet() }, modifier = Modifier.bounceClick()) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedAlarmIds = if (selectedAlarmIds.size == alarms.size) emptySet() else alarms.map { it.id }.toSet()
                        }, modifier = Modifier.bounceClick()) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            val toDelete = alarms.filter { selectedAlarmIds.contains(it.id) }
                            onDeleteAlarms(toDelete)
                            selectedAlarmIds = emptySet()
                        }, modifier = Modifier.bounceClick()) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            if (!isAllPermissionsGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .pure3DEffect(
                            shape = RoundedCornerShape(themeConfig.cardCornerRadius),
                            accentColor = themeConfig.accentColor,
                            depthDp = themeConfig.depthIntensityDp,
                            is3dEnabled = themeConfig.is3dEnabled,
                            isGlowEnabled = themeConfig.isGlowEnabled,
                            surfaceColor = MaterialTheme.colorScheme.surface
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Требуются разрешения",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val subtitle = when {
                                !PermissionHelper.hasNotificationPermission(context) -> "Разрешите отправку уведомлений"
                                !PermissionHelper.hasExactAlarmPermission(context) -> "Разрешите точные будильники"
                                !PermissionHelper.hasFullScreenIntentPermission(context) -> "Разрешите полноэкранные сигналы"
                                else -> "Настройте параметры работы приложения"
                            }
                            Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                when {
                                    !PermissionHelper.hasNotificationPermission(context) -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    !PermissionHelper.hasExactAlarmPermission(context) -> {
                                        PermissionHelper.openExactAlarmSettings(context)
                                    }
                                    !PermissionHelper.hasFullScreenIntentPermission(context) -> {
                                        PermissionHelper.openFullScreenIntentSettings(context)
                                    }
                                    else -> {
                                        PermissionHelper.openAppSettings(context)
                                    }
                                }
                            },
                            modifier = Modifier.bounceClick()
                        ) {
                            Text("Выдать", color = themeConfig.accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = alarms.isEmpty(),
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label = "EmptyStateTransition"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyAlarmState(onAddAlarmClick = onAddAlarmClick)
                } else {
                    Column {
                        if (!isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .pure3DEffect(
                                        shape = RoundedCornerShape(themeConfig.cardCornerRadius),
                                        accentColor = themeConfig.accentColor,
                                        depthDp = themeConfig.depthIntensityDp,
                                        is3dEnabled = themeConfig.is3dEnabled,
                                        isGlowEnabled = themeConfig.isGlowEnabled,
                                        surfaceColor = MaterialTheme.colorScheme.surface
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (nearestActiveAlarmInfo != null) Icons.Default.Schedule else Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = themeConfig.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (nearestActiveAlarmInfo != null) {
                                            val (alarm, triggerMillis) = nearestActiveAlarmInfo
                                            val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(triggerMillis))
                                            val totalMinutes = duration.toMinutes()
                                            val days = totalMinutes / (24 * 60)
                                            val hours = (totalMinutes / 60) % 24
                                            val minutes = totalMinutes % 60

                                            val timeText = String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute)
                                            val countdownText = buildString {
                                                append("Через ")
                                                if (days > 0) append("$days дн ")
                                                if (hours > 0 || days > 0) append("$hours ч ")
                                                append("$minutes мин")
                                            }

                                            Text("Ближайший сигнал в $timeText", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(countdownText, fontSize = 12.sp, color = themeConfig.accentColor, fontWeight = FontWeight.SemiBold)
                                        } else {
                                            Text("Все будильники выключены", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(randomQuote, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val newMode = !isCardView
                                            isCardView = newMode
                                            prefs.edit().putBoolean("is_alarm_card_view", newMode).apply()
                                        },
                                        modifier = Modifier.bounceClick()
                                    ) {
                                        Icon(
                                            imageVector = if (isCardView) Icons.Default.GridView else Icons.Default.ViewDay,
                                            contentDescription = "Сменить вид",
                                            tint = themeConfig.accentColor
                                        )
                                    }
                                }
                            }
                        }

                        val onToggleHandler: (AlarmEntity) -> Unit = { alarm ->
                            if (alarm.isEnabled && (alarm.daysOfWeek != 0 || !alarm.extraDatesStr.isNullOrBlank())) {
                                alarmToDisable = alarm
                            } else {
                                onToggleAlarm(alarm, !alarm.isEnabled)
                            }
                        }

                        AnimatedContent(
                            targetState = isCardView,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "ViewModeTransition"
                        ) { cardView ->
                            if (cardView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp)
                                ) {
                                    itemsIndexed(alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                            AlarmItem(
                                                alarm = alarm.toUiModel(),
                                                isCardView = true,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedAlarmIds.contains(alarm.id),
                                                onToggle = { onToggleHandler(alarm) },
                                                onEditClick = {
                                                    selectedAlarmIds = emptySet()
                                                    onEditAlarmClick(alarm)
                                                },
                                                onLongClick = { selectedAlarmIds = selectedAlarmIds + alarm.id },
                                                onSelectToggle = {
                                                    selectedAlarmIds = if (selectedAlarmIds.contains(alarm.id)) selectedAlarmIds - alarm.id else selectedAlarmIds + alarm.id
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp)
                                ) {
                                    itemsIndexed(alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                            AlarmItem(
                                                alarm = alarm.toUiModel(),
                                                isCardView = false,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedAlarmIds.contains(alarm.id),
                                                onToggle = { onToggleHandler(alarm) },
                                                onEditClick = {
                                                    selectedAlarmIds = emptySet()
                                                    onEditAlarmClick(alarm)
                                                },
                                                onLongClick = { selectedAlarmIds = selectedAlarmIds + alarm.id },
                                                onSelectToggle = {
                                                    selectedAlarmIds = if (selectedAlarmIds.contains(alarm.id)) selectedAlarmIds - alarm.id else selectedAlarmIds + alarm.id
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

    alarmToDisable?.let { alarm ->
        val nextTriggerMillis = remember(alarm) { scheduler.calculateTriggerTime(alarm) }
        val dateText = remember(nextTriggerMillis) {
            val date = Instant.ofEpochMilli(nextTriggerMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = java.time.LocalDate.now()
            val dayPrefix = if (date == today.plusDays(1)) "Завтра, " else ""
            dayPrefix + date.format(DateTimeFormatter.ofPattern("d MMMM", RU_LOCALE))
        }

        AlertDialog(
            onDismissRequest = { alarmToDisable = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeConfig.accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Отключение сигнала",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Выберите действие для повторяющегося будильника:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick {
                                onSkipNextAlarm(alarm, nextTriggerMillis)
                                alarmToDisable = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = themeConfig.accentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Пропустить 1 раз: $dateText", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Автоматически включится к следующему дню", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick {
                                onToggleAlarm(alarm, false)
                                alarmToDisable = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Отключить все повторы", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Будильник будет выключен до ручного включения", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { alarmToDisable = null }, modifier = Modifier.bounceClick()) {
                    Text("Отмена", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

private fun AlarmEntity.toUiModel(): AlarmUiModel {
    val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    val today = java.time.LocalDate.now()
    val systemZone = ZoneId.systemDefault()

    val extraDates = parseExtraDates()
    val excludedDates = parseExcludedDates()

    val formattedDays = when {
        skippedDateMillis != null -> {
            val date = Instant.ofEpochMilli(skippedDateMillis).atZone(systemZone).toLocalDate()
            "Пропущен: ${date.format(DateTimeFormatter.ofPattern("d MMMM", RU_LOCALE))}"
        }
        daysOfWeek != 0 -> {
            val base = when (daysOfWeek) {
                127 -> "Каждый день"
                31 -> "Будни"
                96 -> "Выходные"
                else -> DayOfWeek.entries
                    .filter { day -> (daysOfWeek and (1 shl (day.value - 1))) != 0 }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, RU_LOCALE) }
            }
            if (excludedDates.isNotEmpty()) "$base (-${excludedDates.size} дн.)" else base
        }
        extraDates.isNotEmpty() -> {
            if (extraDates.size == 1) {
                val date = extraDates.first()
                when (date) {
                    today -> "Сегодня"
                    today.plusDays(1) -> "Завтра"
                    else -> date.format(DateTimeFormatter.ofPattern("d MMMM", RU_LOCALE))
                }
            } else {
                "${extraDates.size} выбранных дат"
            }
        }
        else -> {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
            }
            if (calendar.timeInMillis <= System.currentTimeMillis()) "Завтра" else "Сегодня"
        }
    }

    return AlarmUiModel(
        id = id,
        time = formattedTime,
        days = formattedDays,
        label = label,
        timeRemaining = "",
        isEnabled = isEnabled
    )
}