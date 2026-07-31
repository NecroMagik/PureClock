package com.necromagik.pureclock.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.AlarmCalendarView
import com.necromagik.pureclock.ui.components.TwentyFourHourDial
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val RU_LOCALE = Locale("ru")

// ============================================================================
// СЕКЦИЯ 1: ИНИЦИАЛИЗАЦИЯ СОСТОЯНИЙ И ТОП-БАР СО ХРАНЕНИЕМ
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlarmScreen(
    editingAlarmId: Long? = null,
    initialHour: Int = 8,
    initialMinute: Int = 0,
    initialLabel: String = "Будильник",
    initialRingtoneUri: String? = null,
    initialDaysOfWeek: Int = 0,
    initialSpecificDateMillis: Long? = null,
    onSave: (
        hour: Int,
        minute: Int,
        selectedDays: Set<DayOfWeek>,
        selectedDates: Set<LocalDate>,
        label: String,
        ringtoneUri: String?,
        isVibrate: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val context = LocalContext.current

    // Часы и минуты
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    // Состояние дней недели (Битовая маска)
    var daysMask by remember { mutableIntStateOf(initialDaysOfWeek) }

    // Точечно добавленные даты вне дней недели
    var extraDates by remember {
        mutableStateOf<Set<LocalDate>>(
            initialSpecificDateMillis?.let { millis ->
                setOf(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
            } ?: emptySet()
        )
    }

    // Точечно исключенные даты внутри выбранных дней недели
    var excludedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }

    var isCalendarExpanded by remember { mutableStateOf(false) }

    // Доп. параметры
    var label by remember { mutableStateOf(initialLabel) }
    var isVibrate by remember { mutableStateOf(true) }
    var ringtoneUriString by remember { mutableStateOf(initialRingtoneUri) }
    var ringtoneTitle by remember { mutableStateOf("По умолчанию") }

    var showLabelDialog by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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

// ============================================================================
// СЕКЦИЯ 2: ИНТЕРАКТИВНЫЙ ВЫБОР ВРЕМЕНИ (24H DIAL)
// ============================================================================
            TwentyFourHourDial(
                selectedHour = selectedHour,
                selectedMinute = selectedMinute,
                onHourSelected = { selectedHour = it },
                onMinuteSelected = { selectedMinute = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

// ============================================================================
// СЕКЦИЯ 3: КАРТОЧКА ПОВТОРОВ И КАЛЕНДАРЬ OXYGENOS
// ============================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCalendarExpanded = !isCalendarExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Повтор",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (extraDates.isNotEmpty()) {
                                val firstDate = extraDates.first()
                                Text(
                                    text = "Следующий звонок: ${firstDate.format(DateTimeFormatter.ofPattern("d MMMM", RU_LOCALE))}",
                                    fontSize = 12.sp,
                                    color = accentColor
                                )
                            }
                        }

                        IconButton(onClick = { isCalendarExpanded = !isCalendarExpanded }) {
                            Icon(
                                imageVector = if (isCalendarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Календарь"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    .size(40.dp)
                                    .bounceClick()
                                    .clip(CircleShape)
                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surface)
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
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
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            AlarmCalendarView(
                                selectedHour = selectedHour,
                                selectedMinute = selectedMinute,
                                daysMask = daysMask,
                                extraDates = extraDates,
                                excludedDates = excludedDates,
                                onDateToggled = { date ->
                                    val dayBit = 1 shl (date.dayOfWeek.value - 1)
                                    val isDayOfWeekActive = (daysMask and dayBit) != 0

                                    if (isDayOfWeekActive) {
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

// ============================================================================
// СЕКЦИЯ 4: НАЗВАНИЕ, ВЫБОР РИНГТОНА И ВИБРАЦИЯ
// ============================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text("Название", fontWeight = FontWeight.Medium) },
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Звук будильника", fontWeight = FontWeight.Medium) },
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Вибрация", fontWeight = FontWeight.Medium) },
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

// ============================================================================
// СЕКЦИЯ 5: ДИАЛОГ ВВОДА ТЕКСТОВОЙ МЕТКИ (LABEL DIALOG)
// ============================================================================
    if (showLabelDialog) {
        var tempLabel by remember { mutableStateOf(label) }

        AlertDialog(
            onDismissRequest = { showLabelDialog = false },
            title = { Text("Название будильника") },
            text = {
                OutlinedTextField(
                    value = tempLabel,
                    onValueChange = { tempLabel = it },
                    singleLine = true,
                    label = { Text("Введите текст") },
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