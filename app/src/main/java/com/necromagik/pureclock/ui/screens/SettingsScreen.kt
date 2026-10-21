package com.necromagik.pureclock.ui.screens

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.necromagik.pureclock.data.AlarmPickerStyle
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.ClockStylePickerDialog
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.components.SwitchIconType
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.util.ApkUpdateInstaller
import com.necromagik.pureclock.widget.PureClockWidgetProvider
import com.necromagik.pureclock.widget.WidgetConfigActivity
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToThemeEngine: () -> Unit = {}
) {
    BackHandler {
        onBackClick()
    }

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
    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    val isVolumeRamp by settingsManager.isVolumeRampEnabledFlow.collectAsState()
    val defaultSnooze by settingsManager.defaultSnoozeTimeMinutesFlow.collectAsState()
    val autoDismiss by settingsManager.autoDismissMinutesFlow.collectAsState()
    val upcomingNotice by settingsManager.upcomingNotificationMinutesFlow.collectAsState()
    val dismissMethod by settingsManager.dismissMethodFlow.collectAsState()
    val is24Hour by settingsManager.is24HourFormatFlow.collectAsState()
    val isClockHaptics by settingsManager.isClockHapticsEnabledFlow.collectAsState()
    val isTimerVibrate by settingsManager.isTimerVibrateFlow.collectAsState()
    val isStopwatchLapVibrate by settingsManager.isStopwatchLapVibrateFlow.collectAsState()
    val allowBetaUpdates by settingsManager.allowBetaUpdatesFlow.collectAsState()
    val isBetaUpdateAvailable by settingsManager.isBetaUpdateAvailableFlow.collectAsState()
    val latestBetaRelease by settingsManager.latestBetaReleaseFlow.collectAsState()

    val appVersion = remember { settingsManager.appVersionName }
    val installSource = remember { settingsManager.getInstallationSource() }

    val activeWidgetIds = remember {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PureClockWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        ids.filter { it > 0 }.toIntArray()
    }
    val hasActiveWidget = activeWidgetIds.isNotEmpty()

    var showStyleDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showAutoDismissDialog by remember { mutableStateOf(false) }
    var showDismissMethodDialog by remember { mutableStateOf(false) }
    var showUpcomingNoticeDialog by remember { mutableStateOf(false) }
    var showDeveloperInfoDialog by remember { mutableStateOf(false) }
    var showBetaWarningDialog by remember { mutableStateOf(false) }

    var isCheckingStoreUpdates by remember { mutableStateOf(false) }
    var isCheckingBetaUpdates by remember { mutableStateOf(false) }
    var playUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var betaUpdateResultDialog by remember { mutableStateOf<SettingsManager.UpdateChecker.ReleaseInfo?>(null) }
    var showNoUpdatesToast by remember { mutableStateOf(false) }

    var showStartMainTabDialog by remember { mutableStateOf(false) }
    val startMainTab by settingsManager.startMainTabFlow.collectAsState()

    val warningColor = Color(0xFFFF9800)
    val pureOsBlueColor = Color(0xFF2979FF)

    val playUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            playUpdateInfo = null
        }
    }

    var showAlarmPickerStyleDialog by remember { mutableStateOf(false) }
    val alarmPickerStyle by settingsManager.alarmPickerStyleFlow.collectAsState()

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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item { SettingsHeader("Оформление") }

            // 1. Главная страница теперь стоит первой (на месте настроек тем)
            item {
                val mainTabTitles = listOf("Будильник", "Мировое время", "Таймер", "Секундомер") +
                        if (settingsManager.ifRelease == 1) listOf("Сценарии") else emptyList()

                SettingsClickCard(
                    icon = Icons.Default.Home,
                    title = "Главная страница",
                    subtitle = mainTabTitles.getOrNull(startMainTab) ?: "Будильник",
                    onClick = { showStartMainTabDialog = true },
                    shape = cardShape
                )
            }

            // 2. Theme Engine
            item {
                SettingsClickCard(
                    icon = Icons.Default.Palette,
                    title = "Theme Engine",
                    subtitle = "Цветовые акценты, Depth 3D, стиль элементов",
                    onClick = onOpenThemeEngine,
                    shape = cardShape
                )
            }

            // 3. Конструктор виджета
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
                    shape = cardShape
                )
            }

            item { SettingsHeader("Будильник") }
            item {
                SettingsClickCard(
                    icon = Icons.Default.AccessTime,
                    title = "Стиль выбора времени",
                    subtitle = "${alarmPickerStyle.title} • ${alarmPickerStyle.description}",
                    onClick = { showAlarmPickerStyleDialog = true },
                    shape = cardShape
                )
            }
            item {
                SettingsSwitchCard(
                    icon = if (isVolumeRamp) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    title = "Плавное нарастание громкости",
                    subtitle = "Громкость сигнала увеличивается постепенно",
                    isChecked = isVolumeRamp,
                    iconType = SwitchIconType.SOUNDS,
                    onCheckedChange = { settingsManager.isVolumeRampEnabled = it },
                    shape = cardShape
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Alarm,
                    title = "Длительность повтора",
                    subtitle = "$defaultSnooze минут",
                    onClick = { showSnoozeDialog = true },
                    shape = cardShape
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.TimerOff,
                    title = "Автоматическое отключение",
                    subtitle = "Через $autoDismiss минут",
                    onClick = { showAutoDismissDialog = true },
                    shape = cardShape
                )
            }
            item {
                SettingsClickCard(
                    icon = if (upcomingNotice > 0) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    title = "Предварительное уведомление",
                    subtitle = if (upcomingNotice > 0)
                        "За $upcomingNotice мин до сигнала"
                    else "Отключено",
                    onClick = { showUpcomingNoticeDialog = true },
                    shape = cardShape
                )
            }
            item {
                val methodTitle = when (dismissMethod) {
                    "MATH" -> "Математический пример"
                    "SHAKE" -> "Встряхивание"
                    else -> "Свайп"
                }
                SettingsClickCard(
                    icon = Icons.Default.Psychology,
                    title = "Способ выключения",
                    subtitle = methodTitle,
                    onClick = { showDismissMethodDialog = true },
                    shape = cardShape
                )
            }

            item { SettingsHeader("Мировое время") }
            item {
                SettingsSwitchCard(
                    icon = if (is24Hour) Icons.Default.Schedule else Icons.Default.HistoryToggleOff,
                    title = "24-часовой формат",
                    subtitle = if (is24Hour) "14:00" else "02:00 PM",
                    isChecked = is24Hour,
                    onCheckedChange = { settingsManager.is24HourFormat = it },
                    shape = cardShape
                )
            }
            item {
                SettingsClickCard(
                    icon = Icons.Default.Palette,
                    title = "Стиль циферблата",
                    subtitle = "${settingsManager.selectedAnalogStyle.title} / ${settingsManager.selectedDigitalStyle.title}",
                    onClick = { showStyleDialog = true },
                    shape = cardShape
                )
            }
            item {
                SettingsSwitchCard(
                    icon = if (isClockHaptics) Icons.Default.Vibration else Icons.Default.Smartphone,
                    title = "Тактильный отклик",
                    subtitle = "Вибрация при вращении стрелки",
                    isChecked = isClockHaptics,
                    onCheckedChange = { settingsManager.isClockHapticsEnabled = it },
                    shape = cardShape
                )
            }

            item { SettingsHeader("Таймер и Секундомер") }
            item {
                SettingsSwitchCard(
                    icon = if (isTimerVibrate) Icons.Default.HourglassBottom else Icons.Default.HourglassEmpty,
                    title = "Вибрация таймера",
                    subtitle = "Вибрация по окончании отсчета",
                    isChecked = isTimerVibrate,
                    onCheckedChange = { settingsManager.isTimerVibrate = it },
                    shape = cardShape
                )
            }
            item {
                SettingsSwitchCard(
                    icon = if (isStopwatchLapVibrate) Icons.Default.Timer else Icons.Default.TimerOff,
                    title = "Отклик кругов секундомера",
                    subtitle = "Вибрация при нажатии кнопки «Круг»",
                    isChecked = isStopwatchLapVibrate,
                    onCheckedChange = { settingsManager.isStopwatchLapVibrate = it },
                    shape = cardShape
                )
            }

            item { SettingsHeader("О приложении") }

            item {
                val isSystemPrebuilt = installSource == SettingsManager.InstallSource.SYSTEM_PREBUILT
                val isOfficial = installSource.isOfficial

                val sourceAccent = when {
                    isSystemPrebuilt -> pureOsBlueColor
                    isOfficial -> themeConfig.accentColor
                    else -> warningColor
                }

                val statusBadgeText = when (installSource) {
                    SettingsManager.InstallSource.SYSTEM_PREBUILT -> "Pure OS"
                    SettingsManager.InstallSource.GOOGLE_PLAY,
                    SettingsManager.InstallSource.RUSTORE -> "Официальная версия"
                    else -> "Сторонний источник"
                }

                val statusDescription = when (installSource) {
                    SettingsManager.InstallSource.SYSTEM_PREBUILT ->
                        "Приложение является системным. Интеграция с Google Play"
                    SettingsManager.InstallSource.GOOGLE_PLAY,
                    SettingsManager.InstallSource.RUSTORE ->
                        "Сборка проверена цифровой подписью репозитория маркета."
                    else ->
                        "Внимание: сборка из стороннего источника. Переустановите приложение из маркета, если не являетесь бета-тестером"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pure3DEffect(
                            shape = cardShape,
                            accentColor = sourceAccent,
                            depthDp = themeConfig.depthIntensityDp,
                            is3dEnabled = themeConfig.is3dEnabled,
                            isGlowEnabled = themeConfig.isGlowEnabled,
                            surfaceColor = MaterialTheme.colorScheme.surface
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(sourceAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isSystemPrebuilt -> Icons.Default.SettingsSuggest
                                    isOfficial -> Icons.Default.Verified
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = sourceAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSystemPrebuilt) "Система " else installSource.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sourceAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = statusBadgeText,
                                        color = sourceAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = statusDescription,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (isOfficial) Color.Gray else warningColor,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // ДВОЙНОЙ АПДЕЙТЕР: Маркеты (Google Play / RuStore) + резервный фоллбэк на GitHub Releases
            val isPlay = installSource == SettingsManager.InstallSource.GOOGLE_PLAY ||
                    installSource == SettingsManager.InstallSource.SYSTEM_PREBUILT
            val isRuStore = installSource == SettingsManager.InstallSource.RUSTORE

            if (!allowBetaUpdates && installSource.isOfficial) {
                item {
                    SettingsClickCard(
                        icon = Icons.Default.SystemUpdate,
                        title = when {
                            playUpdateInfo != null -> "Установить обновление Google Play"
                            isBetaUpdateAvailable && latestBetaRelease != null -> "Доступна версия ${latestBetaRelease?.tagName}"
                            isPlay -> "Проверить обновление в Google Play"
                            isRuStore -> "Проверить обновление в RuStore"
                            else -> "Проверить обновления"
                        },
                        subtitle = when {
                            isCheckingStoreUpdates -> "Опрос серверов обновлений..."
                            playUpdateInfo != null -> "Новая версия готова к установке через Play Store"
                            isBetaUpdateAvailable && latestBetaRelease != null -> "Найдена новая сборка (нажмите для загрузки)"
                            isPlay -> "Текущая версия: $appVersion • Google Play API + GitHub"
                            isRuStore -> "Текущая версия: $appVersion • RuStore SDK + GitHub"
                            else -> "Текущая версия: $appVersion"
                        },
                        onClick = {
                            if (isPlay && playUpdateInfo != null && context is Activity) {
                                settingsManager.getGooglePlayUpdateManager().startUpdateFlow(
                                    activity = context,
                                    appUpdateInfo = playUpdateInfo!!,
                                    launcher = playUpdateLauncher,
                                    updateType = AppUpdateType.FLEXIBLE
                                )
                            } else if (isBetaUpdateAvailable && latestBetaRelease != null) {
                                betaUpdateResultDialog = latestBetaRelease
                            } else if (!isCheckingStoreUpdates) {
                                isCheckingStoreUpdates = true
                                showNoUpdatesToast = false
                                coroutineScope.launch {
                                    var foundInStore = false
                                    if (isPlay) {
                                        val info = settingsManager.checkGooglePlayInAppUpdates()
                                        playUpdateInfo = info
                                        if (info != null && context is Activity) {
                                            foundInStore = true
                                            settingsManager.getGooglePlayUpdateManager().startUpdateFlow(
                                                activity = context,
                                                appUpdateInfo = info,
                                                launcher = playUpdateLauncher,
                                                updateType = AppUpdateType.FLEXIBLE
                                            )
                                        }
                                    } else if (isRuStore) {
                                        val updateInfo = settingsManager.checkRuStoreInAppUpdates()
                                        if (updateInfo != null) {
                                            foundInStore = true
                                            settingsManager.getRuStoreUpdateManager().openStorePage()
                                        }
                                    }

                                    // Если Маркет еще не раскатил обновление — проверяем стабильные релизы на GitHub
                                    if (!foundInStore) {
                                        val ghRelease = SettingsManager.UpdateChecker.checkForBetaUpdates(
                                            currentVersion = appVersion,
                                            allowBeta = false
                                        )
                                        settingsManager.setBetaUpdateInfo(ghRelease)
                                        if (ghRelease != null) {
                                            betaUpdateResultDialog = ghRelease
                                        } else {
                                            showNoUpdatesToast = true
                                        }
                                    }
                                    isCheckingStoreUpdates = false
                                }
                            }
                        },
                        shape = cardShape
                    )
                }
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Science,
                    title = "Бета-канал (GitHub)",
                    subtitle = "Возможность получать обновления в первых рядах. Перевод на экспериментальную ветку обновлений",
                    isChecked = allowBetaUpdates,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showBetaWarningDialog = true
                        } else {
                            settingsManager.allowBetaUpdates = false
                            settingsManager.setBetaUpdateInfo(null)
                        }
                    },
                    shape = cardShape
                )
            }

            if (allowBetaUpdates || !installSource.isOfficial) {
                item {
                    SettingsClickCard(
                        icon = Icons.Default.Upgrade,
                        title = if (isBetaUpdateAvailable) "Скачать обновление ${latestBetaRelease?.tagName}" else "Проверить обновления (GitHub)",
                        subtitle = when {
                            isCheckingBetaUpdates -> "Связь с репозиторием GitHub..."
                            isBetaUpdateAvailable -> "Нажмите для просмотра списка изменений и загрузки APK"
                            else -> "Текущая сборка: $appVersion"
                        },
                        onClick = {
                            if (isBetaUpdateAvailable && latestBetaRelease != null) {
                                betaUpdateResultDialog = latestBetaRelease
                            } else if (!isCheckingBetaUpdates) {
                                isCheckingBetaUpdates = true
                                showNoUpdatesToast = false
                                coroutineScope.launch {
                                    val release = SettingsManager.UpdateChecker.checkForBetaUpdates(
                                        currentVersion = appVersion,
                                        allowBeta = true
                                    )
                                    isCheckingBetaUpdates = false
                                    settingsManager.setBetaUpdateInfo(release)
                                    if (release != null) {
                                        betaUpdateResultDialog = release
                                    } else {
                                        showNoUpdatesToast = true
                                    }
                                }
                            }
                        },
                        shape = cardShape
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pure3DEffect(
                            shape = cardShape,
                            accentColor = themeConfig.accentColor,
                            depthDp = themeConfig.depthIntensityDp,
                            is3dEnabled = themeConfig.is3dEnabled,
                            isGlowEnabled = themeConfig.isGlowEnabled,
                            surfaceColor = MaterialTheme.colorScheme.surface
                        )
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDeveloperInfoDialog = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(themeConfig.accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = themeConfig.accentColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "PureClock",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Версия $appVersion • by NecroMagik",
                                        color = themeConfig.accentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = themeConfig.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.Gray.copy(alpha = 0.15f)
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
                            color = Color.Gray.copy(alpha = 0.15f)
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
                            color = Color.Gray.copy(alpha = 0.15f)
                        )

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
                Spacer(modifier = Modifier.height(6.dp))
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

    if (showBetaWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBetaWarningDialog = false },
            title = { Text("Внимание: Бета-канал", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Вы переходите на бета-канал обновлений из ветки tree/beta (GitHub).\n\n" +
                            "Каждая бета-версия имеет буквенный индекс (например, 1.29a) и может работать нестабильно. " +
                            "Все обновления будут доставляться вручную через GitHub вместо официальных маркетов.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.allowBetaUpdates = true
                        showBetaWarningDialog = false
                        coroutineScope.launch {
                            val release = SettingsManager.UpdateChecker.checkForBetaUpdates(
                                currentVersion = appVersion,
                                allowBeta = true
                            )
                            settingsManager.setBetaUpdateInfo(release)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = warningColor)
                ) {
                    Text("Включить бета", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBetaWarningDialog = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeveloperInfoDialog) {
        DeveloperInfo3DDialog(
            accentColor = themeConfig.accentColor,
            depthDp = themeConfig.depthIntensityDp,
            is3dEnabled = themeConfig.is3dEnabled,
            isGlowEnabled = themeConfig.isGlowEnabled,
            onDismiss = { showDeveloperInfoDialog = false },
            onOpenUrl = { url ->
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            }
        )
    }

    betaUpdateResultDialog?.let { release ->
        var isDownloading by remember(release) { mutableStateOf(false) }
        var downloadProgress by remember(release) { mutableFloatStateOf(0f) }
        var downloadedFile by remember(release) { mutableStateOf<File?>(null) }
        var downloadError by remember(release) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) {
                    betaUpdateResultDialog = null
                }
            },
            title = {
                val titlePrefix = if (release.isBeta) "⚠️ Доступна бета-версия" else "🚀 Доступно обновление"
                Text(
                    text = "$titlePrefix ${release.tagName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (release.isBeta) {
                        Text(
                            text = "Сборка из ветки tree/beta на GitHub. Возможна экспериментальная работа.\n",
                            fontSize = 12.sp,
                            color = warningColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = release.body,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Загрузка пакета обновления...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeConfig.accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (release.isBeta) warningColor else themeConfig.accentColor,
                            trackColor = Color.Gray.copy(alpha = 0.2f)
                        )
                    }

                    if (downloadError) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Не удалось скачать APK напрямую. Попробуйте ещё раз или скачайте через браузер.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                val isDirectApk = release.downloadUrl.endsWith(".apk", ignoreCase = true)

                Button(
                    enabled = !isDownloading,
                    onClick = {
                        if (downloadedFile != null && downloadedFile!!.exists()) {
                            ApkUpdateInstaller.installApk(context, downloadedFile!!)
                            return@Button
                        }

                        if (isDirectApk) {
                            isDownloading = true
                            downloadError = false
                            downloadProgress = 0f
                            coroutineScope.launch {
                                val file = ApkUpdateInstaller.downloadApk(
                                    context = context,
                                    downloadUrl = release.downloadUrl,
                                    tagName = release.tagName,
                                    onProgress = { progress ->
                                        downloadProgress = progress
                                    }
                                )
                                isDownloading = false
                                if (file != null) {
                                    downloadedFile = file
                                    ApkUpdateInstaller.installApk(context, file)
                                } else {
                                    downloadError = true
                                }
                            }
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, release.downloadUrl.toUri())
                            context.startActivity(intent)
                            betaUpdateResultDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (release.isBeta) warningColor else themeConfig.accentColor,
                        contentColor = Color.Black
                    )
                ) {
                    val btnText = when {
                        isDownloading -> "Скачивание..."
                        downloadedFile != null -> "Установить"
                        else -> "Скачать и установить"
                    }
                    Text(btnText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { betaUpdateResultDialog = null }) {
                        Text("Позже", color = Color.Gray)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showNoUpdatesToast) {
        AlertDialog(
            onDismissRequest = { showNoUpdatesToast = false },
            title = { Text("Обновления", fontWeight = FontWeight.Bold) },
            text = {
                val msg = if (allowBetaUpdates) {
                    "Свежих тестовых сборок на GitHub пока нет. У вас установлена актуальная версия $appVersion."
                } else {
                    "У вас установлена последняя версия приложения ($appVersion)."
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(onClick = { showNoUpdatesToast = false }) {
                    Text("Понятно", color = themeConfig.accentColor)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showStyleDialog) {
        ClockStylePickerDialog(
            currentAnalogStyle = settingsManager.selectedAnalogStyle,
            currentDigitalStyle = settingsManager.selectedDigitalStyle,
            onAnalogStyleSelected = { settingsManager.selectedAnalogStyle = it },
            onDigitalStyleSelected = { settingsManager.selectedDigitalStyle = it },
            onDismiss = { showStyleDialog = false }
        )
    }

    if (showSnoozeDialog) {
        SingleChoiceDialog(
            title = "Длительность повтора",
            options = listOf(5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 20 to "20 минут"),
            selectedValue = defaultSnooze,
            onSelect = {
                showSnoozeDialog = false
                settingsManager.defaultSnoozeTimeMinutes = it
            },
            onDismiss = { showSnoozeDialog = false }
        )
    }

    if (showAutoDismissDialog) {
        SingleChoiceDialog(
            title = "Автоотключение будильника",
            options = listOf(5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 30 to "30 минут"),
            selectedValue = autoDismiss,
            onSelect = {
                showAutoDismissDialog = false
                settingsManager.autoDismissMinutes = it
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
            selectedValue = upcomingNotice,
            onSelect = {
                showUpcomingNoticeDialog = false
                settingsManager.upcomingNotificationMinutes = it
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
            selectedValue = dismissMethod,
            onSelect = {
                showDismissMethodDialog = false
                settingsManager.dismissMethod = it
            },
            onDismiss = { showDismissMethodDialog = false }
        )
    }

    if (showAlarmPickerStyleDialog) {
        SingleChoiceDialog(
            title = "Стиль выбора времени",
            options = listOf(
                AlarmPickerStyle.DIAL to "Циферблат (круговой)",
                AlarmPickerStyle.WHEEL to "Барабанный селектор",
                AlarmPickerStyle.TIMELINE to "Временная шкала (лента)"
            ),
            selectedValue = alarmPickerStyle,
            onSelect = {
                showAlarmPickerStyleDialog = false
                settingsManager.selectedAlarmPickerStyle = it
            },
            onDismiss = { showAlarmPickerStyleDialog = false }
        )
    }

    if (showStartMainTabDialog) {
        val options = mutableListOf(
            0 to "Будильник",
            1 to "Мировое время",
            2 to "Таймер",
            3 to "Секундомер"
        )
        if (settingsManager.ifRelease == 1) {
            options.add(4 to "Сценарии")
        }
        SingleChoiceDialog(
            title = "Выбор главной страницы",
            options = options,
            selectedValue = startMainTab,
            onSelect = {
                showStartMainTabDialog = false
                settingsManager.startMainTab = it
            },
            onDismiss = { showStartMainTabDialog = false }
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    iconType: SwitchIconType = SwitchIconType.NONE
) {
    val themeConfig = LocalPureClockConfig.current

    // Динамический цвет иконки и её фона в зависимости от состояния переключателя
    val dynamicIconTint by animateColorAsState(
        targetValue = if (isChecked) themeConfig.accentColor else Color.Gray,
        animationSpec = tween(250),
        label = "SwitchIconTint"
    )
    val dynamicIconBg by animateColorAsState(
        targetValue = if (isChecked) {
            themeConfig.accentColor.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(250),
        label = "SwitchIconBg"
    )

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
            .padding(18.dp)
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(dynamicIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = dynamicIconTint)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            PureSwitch(
                checked = isChecked,
                iconType = iconType,
                onCheckedChange = onCheckedChange
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
    shape: androidx.compose.ui.graphics.Shape,
    iconTint: Color? = null
) {
    val themeConfig = LocalPureClockConfig.current
    // Все обычные значки теперь акцентные по умолчанию
    val resolvedTint = iconTint ?: themeConfig.accentColor

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
            .padding(18.dp)
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(resolvedTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = resolvedTint)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = themeConfig.accentColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun DeveloperInfo3DDialog(
    accentColor: Color,
    depthDp: Dp,
    is3dEnabled: Boolean,
    isGlowEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dialogShape = RoundedCornerShape(28.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {},
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .pure3DEffect(
                        shape = dialogShape,
                        accentColor = accentColor,
                        depthDp = depthDp + 2.dp,
                        is3dEnabled = is3dEnabled,
                        isGlowEnabled = isGlowEnabled,
                        surfaceColor = MaterialTheme.colorScheme.surface
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "NecroMagik",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Android Software Developer & Creator",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                    Spacer(modifier = Modifier.height(16.dp))

                    DeveloperLinkButton(
                        icon = Icons.Default.Adjust,
                        title = "Сайт разработчика",
                        subtitle = "NecroMagik.github.io",
                        accentColor = accentColor,
                        onClick = { onOpenUrl("https://NecroMagik.github.io") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DeveloperLinkButton(
                        icon = Icons.Default.Code,
                        title = "GitHub Repository",
                        subtitle = "github.com/NecroMagik",
                        accentColor = accentColor,
                        onClick = { onOpenUrl("https://github.com/NecroMagik") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DeveloperLinkButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = "Telegram Канал",
                        subtitle = "t.me/NecroThemik",
                        accentColor = accentColor,
                        onClick = { onOpenUrl("https://t.me/NecroThemik") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DeveloperLinkButton(
                        icon = Icons.Default.PhoneAndroid,
                        title = "OnePlus 12 Series Club",
                        subtitle = "t.me/OnePlus12SeriesClub",
                        accentColor = accentColor,
                        onClick = { onOpenUrl("https://t.me/OnePlus12SeriesClub") }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Закрыть", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun DeveloperLinkButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
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
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (value == selectedValue),
                            onClick = { onSelect(value) },
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