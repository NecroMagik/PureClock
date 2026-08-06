package com.necromagik.pureclock.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.ClockStylePickerDialog
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.widget.WidgetConfigActivity
import com.necromagik.pureclock.widget.PureClockWidgetProvider
import kotlinx.coroutines.launch

private const val APP_VERSION = "Pure_1.14"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToThemeEngine: () -> Unit = {}
) {
    SettingsMainContent(
        onBackClick = onBackClick,
        onOpenThemeEngine = onNavigateToThemeEngine
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onBackClick: () -> Unit,
    onOpenThemeEngine: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val themeConfig = LocalPureClockConfig.current
    val coroutineScope = rememberCoroutineScope()

    val activeWidgetIds = remember {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PureClockWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(componentName)
    }
    val hasActiveWidget = activeWidgetIds.isNotEmpty()

    var showStyleDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showAutoDismissDialog by remember { mutableStateOf(false) }
    var showDismissMethodDialog by remember { mutableStateOf(false) }
    var showUpcomingNoticeDialog by remember { mutableStateOf(false) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<SettingsManager.UpdateChecker.ReleaseInfo?>(null) }
    var showNoUpdatesToast by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.bounceClick()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsHeader("Оформление") }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Palette,
                    title = "Theme Engine",
                    subtitle = "Цветовые акценты, Depth 3D, стиль элементов",
                    onClick = onOpenThemeEngine,
                    iconTint = themeConfig.accentColor
                )
            }

            item {
                SettingsClickCard(
                    icon = Icons.Default.Widgets,
                    title = "Конструктор виджета",
                    subtitle = if (hasActiveWidget) {
                        "Виджет активен (активных: ${activeWidgetIds.size})"
                    } else {
                        "Виджет не добавлен на рабочий стол"
                    },
                    onClick = {
                        val targetWidgetId = if (hasActiveWidget) activeWidgetIds.first() else AppWidgetManager.INVALID_APPWIDGET_ID
                        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetWidgetId)
                        }
                        context.startActivity(intent)
                    },
                    iconTint = if (hasActiveWidget) themeConfig.accentColor else Color.Gray
                )
            }

            item { SettingsHeader("Будильник") }
            item {
                SettingsSwitchCard(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Плавное нарастание громкости",
                    subtitle = "Громкость сигнала увеличивается постепенно",
                    isChecked = settingsManager.isVolumeRampEnabled,
                    onCheckedChange = { checked: Boolean -> settingsManager.isVolumeRampEnabled = checked }
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Alarm,
                    title = "Длительность повтора",
                    subtitle = "${settingsManager.defaultSnoozeTimeMinutes} минут",
                    onClick = { showSnoozeDialog = true }
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.TimerOff,
                    title = "Автоматическое отключение",
                    subtitle = "Через ${settingsManager.autoDismissMinutes} минут",
                    onClick = { showAutoDismissDialog = true }
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Предварительное уведомление",
                    subtitle = if (settingsManager.upcomingNotificationMinutes > 0)
                        "За ${settingsManager.upcomingNotificationMinutes} мин до сигнала"
                    else "Отключено",
                    onClick = { showUpcomingNoticeDialog = true }
                )
            }
            item {
                val methodTitle = remember(settingsManager.dismissMethod) {
                    when (settingsManager.dismissMethod) {
                        "MATH" -> "Математический пример"
                        "SHAKE" -> "Встряхивание"
                        else -> "Свайп"
                    }
                }
                SettingsClickCard(
                    icon = Icons.Default.Psychology,
                    title = "Способ выключения",
                    subtitle = methodTitle,
                    onClick = { showDismissMethodDialog = true }
                )
            }

            item { SettingsHeader("Мировое время") }
            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Schedule,
                    title = "24-часовой формат",
                    subtitle = if (settingsManager.is24HourFormat) "14:00" else "02:00 PM",
                    isChecked = settingsManager.is24HourFormat,
                    onCheckedChange = { checked: Boolean -> settingsManager.is24HourFormat = checked }
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Palette,
                    title = "Стиль циферблата",
                    subtitle = "${settingsManager.selectedAnalogStyle.title} / ${settingsManager.selectedDigitalStyle.title}",
                    onClick = { showStyleDialog = true }
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Vibration,
                    title = "Тактильный отклик",
                    subtitle = "Вибрация при вращении стрелки",
                    isChecked = settingsManager.isClockHapticsEnabled,
                    onCheckedChange = { checked: Boolean -> settingsManager.isClockHapticsEnabled = checked }
                )
            }

            item { SettingsHeader("Таймер и Секундомер") }
            item {
                SettingsSwitchCard(
                    icon = Icons.Default.HourglassBottom,
                    title = "Вибрация таймера",
                    subtitle = "Вибросигнал по окончании отсчета",
                    isChecked = settingsManager.isTimerVibrate,
                    onCheckedChange = { checked: Boolean -> settingsManager.isTimerVibrate = checked }
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Timer,
                    title = "Отклик кругов секундомера",
                    subtitle = "Вибрация при нажатии кнопки «Круг»",
                    isChecked = settingsManager.isStopwatchLapVibrate,
                    onCheckedChange = { checked: Boolean -> settingsManager.isStopwatchLapVibrate = checked }
                )
            }

            item { SettingsHeader("О приложении") }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Refresh,
                    title = "Проверить обновления",
                    subtitle = if (isCheckingUpdates) "Соединение с GitHub..." else "Текущая: $APP_VERSION",
                    onClick = {
                        if (!isCheckingUpdates) {
                            isCheckingUpdates = true
                            showNoUpdatesToast = false
                            coroutineScope.launch {
                                val release = SettingsManager.UpdateChecker.checkForUpdates(APP_VERSION)
                                isCheckingUpdates = false
                                if (release != null) {
                                    updateResult = release
                                } else {
                                    showNoUpdatesToast = true
                                }
                            }
                        }
                    },
                    iconTint = themeConfig.accentColor
                )
            }
            item {
                val shape = remember(themeConfig.cardCornerRadius) { RoundedCornerShape(themeConfig.cardCornerRadius) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pure3DEffect(
                            shape = shape,
                            accentColor = themeConfig.accentColor,
                            depthDp = themeConfig.depthIntensityDp,
                            is3dEnabled = themeConfig.is3dEnabled,
                            isGlowEnabled = themeConfig.isGlowEnabled,
                            surfaceColor = MaterialTheme.colorScheme.surface
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = themeConfig.accentColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "PureClock",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Версия $APP_VERSION",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )

                        Text(
                            text = "«Pure — это мой личный взгляд на забытые фишки из разных систем и попытка подарить им новую жизнь в собственных приложениях.»",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )

                        Text(
                            text = "Особенности приложения:",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val features = remember {
                            listOf(
                                "• Theme Engine: Кастомизация 3D Depth, свечения и форм",
                                "• Pure Contrast: Абсолютно чёрный AMOLED #000000 / Белый #FFFFFF",
                                "• OxygenOS Calendar: Точное планирование будильника по календарю",
                                "• Мировое время: Интерактивный циферблат и 8 стилей часов",
                                "• Умное выключение: Свайп, решения примеров и Shake-сенсор"
                            )
                        }

                        features.forEach { feature ->
                            Text(
                                text = feature,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Разработчик", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text(text = "NecroMagik", color = themeConfig.accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Экосистема", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text(text = "Zen Space", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Проект Pure © ${java.time.Year.now().value}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Диалоги
    updateResult?.let { release ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text("Доступно обновление ${release.tagName}") },
            text = {
                Column {
                    Text(release.body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, release.downloadUrl.toUri())
                        context.startActivity(intent)
                        updateResult = null
                    }
                ) {
                    Text("Скачать APK")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) {
                    Text("Позже", color = Color.Gray)
                }
            }
        )
    }

    if (showNoUpdatesToast) {
        AlertDialog(
            onDismissRequest = { showNoUpdatesToast = false },
            title = { Text("PureClock") },
            text = { Text("У вас установлена последняя версия!") },
            confirmButton = {
                TextButton(onClick = { showNoUpdatesToast = false }) {
                    Text("Отлично")
                }
            }
        )
    }

    if (showStyleDialog) {
        ClockStylePickerDialog(
            currentAnalogStyle = settingsManager.selectedAnalogStyle,
            currentDigitalStyle = settingsManager.selectedDigitalStyle,
            onAnalogStyleSelected = { style -> settingsManager.selectedAnalogStyle = style },
            onDigitalStyleSelected = { style -> settingsManager.selectedDigitalStyle = style },
            onDismiss = { showStyleDialog = false }
        )
    }

    if (showSnoozeDialog) {
        SingleChoiceDialog(
            title = "Длительность повтора",
            options = listOf(5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 20 to "20 минут"),
            selectedValue = settingsManager.defaultSnoozeTimeMinutes,
            onSelect = { selected: Int ->
                showSnoozeDialog = false
                settingsManager.defaultSnoozeTimeMinutes = selected
            },
            onDismiss = { showSnoozeDialog = false }
        )
    }

    if (showAutoDismissDialog) {
        SingleChoiceDialog(
            title = "Автоотключение будильника",
            options = listOf(5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 30 to "30 минут"),
            selectedValue = settingsManager.autoDismissMinutes,
            onSelect = { selected: Int ->
                showAutoDismissDialog = false
                settingsManager.autoDismissMinutes = selected
            },
            onDismiss = { showAutoDismissDialog = false }
        )
    }

    if (showUpcomingNoticeDialog) {
        SingleChoiceDialog(
            title = "Предварительное уведомление",
            options = listOf(
                0 to "Отключено",
                15 to "За 15 минут",
                30 to "За 30 минут",
                60 to "За 1 час"
            ),
            selectedValue = settingsManager.upcomingNotificationMinutes,
            onSelect = { selected: Int ->
                showUpcomingNoticeDialog = false
                settingsManager.upcomingNotificationMinutes = selected
            },
            onDismiss = { showUpcomingNoticeDialog = false }
        )
    }

    if (showDismissMethodDialog) {
        SingleChoiceDialog(
            title = "Способ выключения",
            options = listOf(
                "SWIPE" to "Обычный свайп",
                "MATH" to "Математический пример",
                "SHAKE" to "Встряхивание телефона"
            ),
            selectedValue = settingsManager.dismissMethod,
            onSelect = { selected: String ->
                showDismissMethodDialog = false
                settingsManager.dismissMethod = selected
            },
            onDismiss = { showDismissMethodDialog = false }
        )
    }
}

// Вспомогательные UI-компоненты
@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val themeConfig = LocalPureClockConfig.current
    val shape = remember(themeConfig.cardCornerRadius) { RoundedCornerShape(themeConfig.cardCornerRadius) }

    var localChecked by remember(isChecked) { mutableStateOf(isChecked) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = shape,
                accentColor = themeConfig.accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Switch(
                checked = localChecked,
                onCheckedChange = { newState ->
                    localChecked = newState
                    onCheckedChange(newState)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun SettingsClickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = Color.Gray
) {
    val themeConfig = LocalPureClockConfig.current
    val shape = remember(themeConfig.cardCornerRadius) { RoundedCornerShape(themeConfig.cardCornerRadius) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .pure3DEffect(
                shape = shape,
                accentColor = themeConfig.accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (value == selectedValue),
                            onClick = {
                                onSelect(value)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.bounceClick()) {
                Text("Отмена", color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}