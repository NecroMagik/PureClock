package com.necromagik.pureclock.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.AlarmCalendarView
import com.necromagik.pureclock.ui.components.TwentyFourHourDial
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val RU_LOCALE = Locale.forLanguageTag("ru")

private fun getPluralDays(count: Int, isExcluded: Boolean = false): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod100 in 11..19 -> "дат"
        mod10 == 1 -> "дата"
        mod10 in 2..4 -> "даты"
        else -> "дат"
    }
    return if (isExcluded) "$count искл. $word" else "$count $word"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlarmScreen(
    editingAlarmId: Long? = null,
    initialHour: Int = LocalTime.now().plusMinutes(1).hour,
    initialMinute: Int = LocalTime.now().plusMinutes(1).minute,
    initialLabel: String = "Будильник",
    initialRingtoneUri: String? = null,
    initialDaysOfWeek: Int = 0,
    initialExtraDates: Set<LocalDate> = emptySet(),
    initialExcludedDates: Set<LocalDate> = emptySet(),
    onSave: (
        hour: Int,
        minute: Int,
        selectedDays: Set<DayOfWeek>,
        extraDates: Set<LocalDate>,
        excludedDates: Set<LocalDate>,
        label: String,
        ringtoneUri: String?,
        isVibrate: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val context = LocalContext.current

    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    var daysMask by remember { mutableIntStateOf(initialDaysOfWeek) }
    var extraDates by remember { mutableStateOf(initialExtraDates) }
    var excludedDates by remember { mutableStateOf(initialExcludedDates) }

    // Проверка: можно ли выбрать сегодняшнюю дату (выбранное время >= текущее + 1 минута)
    val isTodaySelectable by remember(selectedHour, selectedMinute) {
        derivedStateOf {
            val nowPlusOne = LocalTime.now().plusMinutes(1)
            val selectedTime = LocalTime.of(selectedHour, selectedMinute)
            !selectedTime.isBefore(nowPlusOne)
        }
    }

    // Если время сдвинуто назад, автоматически сбрасываем сегодняшнюю дату из extraDates
    LaunchedEffect(isTodaySelectable) {
        if (!isTodaySelectable) {
            val today = LocalDate.now()
            if (extraDates.contains(today)) {
                extraDates = extraDates - today
            }
        }
    }

    var isCalendarExpanded by remember { mutableStateOf(extraDates.isNotEmpty() || excludedDates.isNotEmpty()) }

    var label by remember { mutableStateOf(initialLabel) }
    var isVibrate by remember { mutableStateOf(true) }
    var ringtoneUriString by remember { mutableStateOf(initialRingtoneUri) }
    var ringtoneTitle by remember { mutableStateOf("По умолчанию") }
    var showLabelDialog by remember { mutableStateOf(false) }

    val repeatSubtitle by remember(extraDates, excludedDates, daysMask) {
        derivedStateOf {
            val dateFormat = DateTimeFormatter.ofPattern("d MMM", RU_LOCALE)
            val sortedExtra = extraDates.sorted()
            val sortedExcluded = excludedDates.sorted()

            when {
                sortedExtra.isNotEmpty() && sortedExcluded.isNotEmpty() -> {
                    "+${sortedExtra.size} дат, -${sortedExcluded.size} искл."
                }
                sortedExtra.isNotEmpty() -> {
                    if (sortedExtra.size == 1) {
                        val date = sortedExtra.first()
                        val today = LocalDate.now()
                        when (date) {
                            today -> "Сегодня (${date.format(dateFormat)})"
                            today.plusDays(1) -> "Завтра (${date.format(dateFormat)})"
                            else -> date.format(dateFormat)
                        }
                    } else if (sortedExtra.size <= 3) {
                        sortedExtra.joinToString(", ") { it.format(dateFormat) }
                    } else {
                        val preview = sortedExtra.take(2).joinToString(", ") { it.format(dateFormat) }
                        val remaining = sortedExtra.size - 2
                        "$preview и ещё ${getPluralDays(remaining)}"
                    }
                }
                sortedExcluded.isNotEmpty() -> {
                    if (sortedExcluded.size <= 2) {
                        "Искл: " + sortedExcluded.joinToString(", ") { it.format(dateFormat) }
                    } else {
                        "Исключено ${getPluralDays(sortedExcluded.size, isExcluded = true)}"
                    }
                }
                daysMask != 0 -> "По выбранным дням"
                else -> "Однократно (ближайшее время)"
            }
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtoneUriString = uri?.toString()
            ringtoneTitle = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "Без звука"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingAlarmId == null) "Новый будильник" else "Настройка будильника",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val daysSet = mutableSetOf<DayOfWeek>()
                            val daysList = listOf(
                                DayOfWeek.MONDAY to 1,
                                DayOfWeek.TUESDAY to 2,
                                DayOfWeek.WEDNESDAY to 4,
                                DayOfWeek.THURSDAY to 8,
                                DayOfWeek.FRIDAY to 16,
                                DayOfWeek.SATURDAY to 32,
                                DayOfWeek.SUNDAY to 64
                            )
                            daysList.forEach { (day, bit) ->
                                if ((daysMask and bit) != 0) daysSet.add(day)
                            }

                            onSave(
                                selectedHour,
                                selectedMinute,
                                daysSet,
                                extraDates,
                                excludedDates,
                                label,
                                ringtoneUriString,
                                isVibrate
                            )
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                    Icon(Icons.Default.Check, contentDescription = "Сохранить", tint = accentColor)
                }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(12.dp))

        TwentyFourHourDial(
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            onHourSelected = { selectedHour = it },
            onMinuteSelected = { selectedMinute = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Блок Повторов и Календаря
        Box(
            modifier = Modifier
                .fillMaxWidth()
        .pure3DEffect(
        shape = RoundedCornerShape(themeConfig.cardCornerRadius),
        accentColor = accentColor,
        depthDp = themeConfig.depthIntensityDp,
        is3dEnabled = themeConfig.is3dEnabled,
        isGlowEnabled = themeConfig.isGlowEnabled,
        surfaceColor = MaterialTheme.colorScheme.surface
    )
        .padding(16.dp)
        ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            .clickable { isCalendarExpanded = !isCalendarExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
            ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Повтор",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = repeatSubtitle,
                    fontSize = 12.sp,
                    color = if (extraDates.isNotEmpty() || excludedDates.isNotEmpty() || daysMask != 0) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { isCalendarExpanded = !isCalendarExpanded }) {
                Icon(
                    imageVector = if (isCalendarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Календарь",
                tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

            Spacer(modifier = Modifier.height(12.dp))

            // Дни недели
            Row(
                modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
            ) {
            val days = listOf(
                DayOfWeek.MONDAY to 1,
                DayOfWeek.TUESDAY to 2,
                DayOfWeek.WEDNESDAY to 4,
                DayOfWeek.THURSDAY to 8,
                DayOfWeek.FRIDAY to 16,
                DayOfWeek.SATURDAY to 32,
                DayOfWeek.SUNDAY to 64
            )

            days.forEach { (dayOfWeek, bit) ->
                val isSelected = (daysMask and bit) != 0

                Box(
                    modifier = Modifier
                        .size(38.dp)
                .bounceClick()
                .clip(CircleShape)
                .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                excludedDates = excludedDates.filterTo(mutableSetOf()) { it.dayOfWeek != dayOfWeek }
                extraDates = extraDates.filterTo(mutableSetOf()) { it.dayOfWeek != dayOfWeek }

                daysMask = if (isSelected) {
                    daysMask and bit.inv()
                } else {
                    daysMask or bit
                }
            },
                contentAlignment = Alignment.Center
                ) {
                Text(
                    text = dayOfWeek.getDisplayName(JavaTextStyle.SHORT, RU_LOCALE)
                        .take(2)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(RU_LOCALE) else it.toString() },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
            }
        }

            AnimatedVisibility(
                visible = isCalendarExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
            ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                AlarmCalendarView(
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    daysMask = daysMask,
                    extraDates = extraDates,
                    excludedDates = excludedDates,
                    onDateToggled = { date ->
                        val today = LocalDate.now()

                        // Запрещаем выбирать сегодня, если время уже прошло
                        if (date == today && !isTodaySelectable) {
                            return@AlarmCalendarView
                        }

                        val dayBit = 1 shl (date.dayOfWeek.value - 1)
                        val isRecurringDay = (daysMask and dayBit) != 0

                        if (isRecurringDay) {
                            excludedDates = if (excludedDates.contains(date)) {
                                excludedDates - date
                            } else {
                                excludedDates + date
                            }
                        } else {
                            extraDates = if (extraDates.contains(date)) {
                                extraDates - date
                            } else {
                                extraDates + date
                            }
                        }
                    },
                    accentColor = accentColor
                )
            }
        }
        }
    }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок мелодии, названия и вибрации
        Box(
            modifier = Modifier
                .fillMaxWidth()
        .pure3DEffect(
        shape = RoundedCornerShape(themeConfig.cardCornerRadius),
        accentColor = accentColor,
        depthDp = themeConfig.depthIntensityDp,
        is3dEnabled = themeConfig.is3dEnabled,
        isGlowEnabled = themeConfig.isGlowEnabled,
        surfaceColor = MaterialTheme.colorScheme.surface
    )
        .padding(vertical = 4.dp)
        ) {
        Column {
            ListItem(
                headlineContent = { Text("Название", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) },
            supportingContent = {
                Text(
                    if (label.isEmpty()) "Не задано" else label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier
                .bounceClick()
            .clickable { showLabelDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            ListItem(
                headlineContent = { Text("Звук будильника", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) },
            supportingContent = { Text(ringtoneTitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
            leadingContent = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor) },
            modifier = Modifier
                .bounceClick()
            .clickable {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите мелодию")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUriString?.let { Uri.parse(it) })
            }
            ringtonePickerLauncher.launch(intent)
        },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            ListItem(
                headlineContent = { Text("Вибрация", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) },
            leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null, tint = accentColor) },
            trailingContent = {
                Switch(
                    checked = isVibrate,
                onCheckedChange = { isVibrate = it },
                colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

        Spacer(modifier = Modifier.height(24.dp))
    }
    }

    if (showLabelDialog) {
        var tempLabel by remember { mutableStateOf(label) }

        AlertDialog(
            onDismissRequest = { showLabelDialog = false },
        title = { Text("Название будильника", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            OutlinedTextField(
                value = tempLabel,
            onValueChange = { tempLabel = it },
            singleLine = true,
            label = { Text("Введите текст") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    label = tempLabel
                    showLabelDialog = false
                },
                modifier = Modifier.bounceClick()
            ) {
            Text("ОК", color = accentColor, fontWeight = FontWeight.Bold)
        }
        },
        dismissButton = {
            TextButton(
                onClick = { showLabelDialog = false },
            modifier = Modifier.bounceClick()
            ) {
            Text("Отмена", color = Color.Gray)
        }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
        )
    }
}