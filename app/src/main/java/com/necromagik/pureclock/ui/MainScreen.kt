package com.necromagik.pureclock.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.BottomBarTab
import com.necromagik.pureclock.ui.components.Pure3DIcon
import com.necromagik.pureclock.ui.components.SettingsTopBarIcon
import com.necromagik.pureclock.ui.screens.AddEditAlarmScreen
import com.necromagik.pureclock.ui.screens.AlarmListScreen
import com.necromagik.pureclock.ui.screens.DevScreen
import com.necromagik.pureclock.ui.screens.ScenaryScreen
import com.necromagik.pureclock.ui.screens.SettingsScreen
import com.necromagik.pureclock.ui.screens.StopwatchScreen
import com.necromagik.pureclock.ui.screens.TimerScreen
import com.necromagik.pureclock.ui.screens.WorldClockScreen
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.ThemeEngineScreen
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.AlarmViewModel
import com.necromagik.pureclock.ui.viewmodel.StopwatchViewModel
import com.necromagik.pureclock.ui.viewmodel.TimerViewModel
import com.necromagik.pureclock.ui.components.ScenaryPostTriggerDialog
import com.necromagik.pureclock.util.ScenaryExecutor
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

enum class ClockTab(val title: String, val tabType: BottomBarTab) {
    ALARM("Будильник", BottomBarTab.ALARM),
    WORLD_CLOCK("Время", BottomBarTab.WORLD_CLOCK),
    TIMER("Таймер", BottomBarTab.TIMER),
    STOPWATCH("Секундомер", BottomBarTab.STOPWATCH),
    SCENARY("Сценарии", BottomBarTab.WORLD_CLOCK)
}

enum class ScreenRoute {
    MAIN,
    SETTINGS,
    THEME_ENGINE,
    DEV
}

private class NotchedDockShape(
    private val cornerRadius: Dp = 32.dp,
    private val notchRadius: Dp = 34.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cornerPx = with(density) { cornerRadius.toPx() }
        val notchRadiusPx = with(density) { notchRadius.toPx() }

        val basePath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    topLeft = CornerRadius(cornerPx),
                    topRight = CornerRadius(cornerPx),
                    bottomLeft = CornerRadius(cornerPx),
                    bottomRight = CornerRadius(cornerPx)
                )
            )
        }

        val cutoutPath = Path().apply {
            addOval(
                Rect(
                    center = Offset(size.width / 2f, 0f),
                    radius = notchRadiusPx
                )
            )
        }

        val resultPath = Path.combine(PathOperation.Difference, basePath, cutoutPath)
        return Outline.Generic(resultPath)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AlarmViewModel) {
    var currentRoute by remember { mutableStateOf(ScreenRoute.MAIN) }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val ifRelease by settingsManager.ifReleaseFlow.collectAsState()
    val startMainTab by settingsManager.startMainTabFlow.collectAsState()

    val timerViewModel: TimerViewModel = viewModel()
    val stopwatchViewModel: StopwatchViewModel = viewModel()

    // Количество доступных вкладок в пейджере: 4 (или 5, если IfRelease == 1)
    val maxPages = if (ifRelease == 1) 5 else 4
    val pagerState = rememberPagerState(initialPage = startMainTab.coerceIn(0, maxPages - 1)) { maxPages }
    val coroutineScope = rememberCoroutineScope()
    val themeConfig = LocalPureClockConfig.current

    var isScenaryTabActive by remember { mutableStateOf(false) }

    var isAddingAlarm by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    var onTimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onScenaryAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isStopwatchRunning by stopwatchViewModel.isRunning.collectAsState()
    val stopwatchElapsed by stopwatchViewModel.elapsedMillis.collectAsState()

    val alarms by viewModel.alarms.collectAsState()

    val postTriggerPayload by ScenaryExecutor.activePostTriggerPayload.collectAsState()

    postTriggerPayload?.let { payload ->
        ScenaryPostTriggerDialog(
            payload = payload,
            onDismiss = { ScenaryExecutor.dismissPostTriggerPayload() }
        )
    }

    var isUpdateBubbleDismissed by remember { mutableStateOf(false) }

    // Переменная для обработки двойного тапа "Назад" при нахождении на главной странице
    var backPressedOnceTime by remember { mutableLongStateOf(0L) }
    val activity = context as? Activity

    // Обработка жеста "Назад":
    BackHandler(enabled = true) {
        when {
            isAddingAlarm || editingAlarm != null -> {
                isAddingAlarm = false
                editingAlarm = null
            }
            currentRoute == ScreenRoute.THEME_ENGINE -> currentRoute = ScreenRoute.SETTINGS
            currentRoute == ScreenRoute.DEV -> currentRoute = ScreenRoute.MAIN
            currentRoute == ScreenRoute.SETTINGS -> currentRoute = ScreenRoute.MAIN
            isScenaryTabActive -> {
                // Если открыта скрытая вкладка сценариев, возвращаем на главную
                isScenaryTabActive = false
            }
            pagerState.currentPage != startMainTab -> {
                // Если открыта неглавная вкладка в пейджере — возврат на главную за 1 свайп
                coroutineScope.launch { pagerState.animateScrollToPage(startMainTab) }
            }
            else -> {
                // Если мы находимся на главной странице: двойной свайп для выхода из приложения
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedOnceTime < 2000) {
                    activity?.finish()
                } else {
                    backPressedOnceTime = currentTime
                    android.widget.Toast.makeText(context, "Нажмите ещё раз для выхода", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = isAddingAlarm || editingAlarm != null,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn()).togetherWith(
                        scaleOut(targetScale = 0.94f, animationSpec = tween(280)) + fadeOut()
                    )
                } else {
                    (scaleIn(initialScale = 0.94f, animationSpec = tween(280)) + fadeIn()).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeOut()
                    )
                }
            },
            label = "AddEditAlarmTransition"
        ) { isEditing ->
            if (isEditing) {
                val alarmToEdit = editingAlarm
                val defaultTime = remember { LocalTime.now().plusMinutes(1) }

                AddEditAlarmScreen(
                    editingAlarmId = alarmToEdit?.id,
                    initialHour = alarmToEdit?.hour ?: defaultTime.hour,
                    initialMinute = alarmToEdit?.minute ?: defaultTime.minute,
                    initialLabel = alarmToEdit?.label ?: "Будильник",
                    initialRingtoneUri = alarmToEdit?.ringtoneUri,
                    initialDaysOfWeek = alarmToEdit?.daysOfWeek ?: 0,
                    initialExtraDates = alarmToEdit?.parseExtraDates() ?: emptySet(),
                    initialExcludedDates = alarmToEdit?.parseExcludedDates() ?: emptySet(),
                    onSave = { hour, minute, days, extraDates, excludedDates, label, ringtoneUri, isVibrate ->
                        val daysMask = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
                        val extraDatesStr = extraDates.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }
                        val excludedDatesStr = excludedDates.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }
                        val specificDateMillis = extraDates.firstOrNull()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

                        viewModel.saveAlarm(
                            id = alarmToEdit?.id ?: 0L,
                            hour = hour,
                            minute = minute,
                            daysOfWeek = daysMask,
                            specificDateMillis = specificDateMillis,
                            extraDatesStr = extraDatesStr,
                            excludedDatesStr = excludedDatesStr,
                            label = label,
                            ringtoneUri = ringtoneUri,
                            isVibrate = isVibrate
                        )

                        isAddingAlarm = false
                        editingAlarm = null
                    },
                    onBack = {
                        isAddingAlarm = false
                        editingAlarm = null
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = {
                        val isGoingBack = targetState == ScreenRoute.MAIN ||
                                (initialState == ScreenRoute.THEME_ENGINE && targetState == ScreenRoute.SETTINGS)

                        if (isGoingBack) {
                            (scaleIn(initialScale = 0.94f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                    fadeIn(tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                                    ) + fadeOut(tween(180))
                                )
                        } else {
                            (slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(220)))
                                .togetherWith(
                                    scaleOut(targetScale = 0.94f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                            fadeOut(tween(180))
                                )
                        }
                    },
                    label = "RouteTransition"
                ) { route ->
                    when (route) {
                        ScreenRoute.SETTINGS -> {
                            SettingsScreen(
                                onBackClick = { currentRoute = ScreenRoute.MAIN },
                                onNavigateToThemeEngine = { currentRoute = ScreenRoute.THEME_ENGINE }
                            )
                        }

                        ScreenRoute.THEME_ENGINE -> {
                            ThemeEngineScreen(
                                onBackClick = { currentRoute = ScreenRoute.SETTINGS }
                            )
                        }

                        ScreenRoute.DEV -> {
                            DevScreen(
                                onBack = { currentRoute = ScreenRoute.MAIN }
                            )
                        }

                        ScreenRoute.MAIN -> {
                            val isBetaUpdateAvailable by settingsManager.isBetaUpdateAvailableFlow.collectAsState()
                            val latestBetaRelease by settingsManager.latestBetaReleaseFlow.collectAsState()

                            val activeTab = if (isScenaryTabActive) {
                                ClockTab.SCENARY
                            } else {
                                ClockTab.entries.getOrElse(pagerState.currentPage) { ClockTab.ALARM }
                            }

                            LaunchedEffect(Unit) {
                                val source = settingsManager.getInstallationSource()
                                val shouldCheckGithub =
                                    settingsManager.allowBetaUpdates || !source.isOfficial
                                if (shouldCheckGithub) {
                                    val release = SettingsManager.UpdateChecker.checkForBetaUpdates(
                                        currentVersion = settingsManager.appVersionName,
                                        allowBeta = settingsManager.allowBetaUpdates || !source.isOfficial
                                    )
                                    settingsManager.setBetaUpdateInfo(release)
                                }
                            }

                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding(),
                                topBar = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = activeTab.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        SettingsTopBarIcon(
                                            hasUpdate = isBetaUpdateAvailable,
                                            accentColor = themeConfig.accentColor,
                                            onClick = { currentRoute = ScreenRoute.SETTINGS }
                                        )
                                    }
                                },
                                bottomBar = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        val dockShape = remember { NotchedDockShape(cornerRadius = 32.dp, notchRadius = 34.dp) }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .pure3DEffect(
                                                    shape = dockShape,
                                                    accentColor = themeConfig.accentColor,
                                                    surfaceColor = MaterialTheme.colorScheme.surface,
                                                    depthDp = themeConfig.depthIntensityDp,
                                                    is3dEnabled = themeConfig.is3dEnabled,
                                                    isGlowEnabled = themeConfig.isGlowEnabled
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                NavTabItem3D(
                                                    tab = ClockTab.ALARM,
                                                    isSelected = !isScenaryTabActive && pagerState.currentPage == 0,
                                                    onClick = {
                                                        isScenaryTabActive = false
                                                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                NavTabItem3D(
                                                    tab = ClockTab.WORLD_CLOCK,
                                                    isSelected = !isScenaryTabActive && pagerState.currentPage == 1,
                                                    onClick = {
                                                        isScenaryTabActive = false
                                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Spacer(modifier = Modifier.width(56.dp))

                                                NavTabItem3D(
                                                    tab = ClockTab.TIMER,
                                                    isSelected = !isScenaryTabActive && pagerState.currentPage == 2,
                                                    onClick = {
                                                        isScenaryTabActive = false
                                                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                NavTabItem3D(
                                                    tab = ClockTab.STOPWATCH,
                                                    isSelected = !isScenaryTabActive && pagerState.currentPage == 3,
                                                    onClick = {
                                                        isScenaryTabActive = false
                                                        coroutineScope.launch { pagerState.animateScrollToPage(3) }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (ifRelease == 1) {
                                                    NavTabItem3D(
                                                        tab = ClockTab.SCENARY,
                                                        isSelected = !isScenaryTabActive && pagerState.currentPage == 4,
                                                        onClick = {
                                                            isScenaryTabActive = false
                                                            coroutineScope.launch { pagerState.animateScrollToPage(4) }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }

                                        val isStopwatchTab = activeTab == ClockTab.STOPWATCH

                                        AnimatedVisibility(
                                            visible = isStopwatchTab && (stopwatchElapsed > 0 && !isStopwatchRunning),
                                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                    slideInHorizontally(initialOffsetX = { it }) +
                                                    slideInVertically(initialOffsetY = { it }),
                                            exit = scaleOut() + slideOutHorizontally(targetOffsetX = { it }) + slideOutVertically(targetOffsetY = { it }),
                                            modifier = Modifier.offset(x = (-84).dp, y = (-78).dp)
                                        ) {
                                            SmallFloatingActionButton(
                                                onClick = { stopwatchViewModel.reset() },
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = Color.Gray,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .bounceClick()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Сброс",
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = isStopwatchTab && isStopwatchRunning,
                                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                    slideInHorizontally(initialOffsetX = { -it }) +
                                                    slideInVertically(initialOffsetY = { it }),
                                            exit = scaleOut() + slideOutHorizontally(targetOffsetX = { -it }) + slideOutVertically(targetOffsetY = { it }),
                                            modifier = Modifier.offset(x = 84.dp, y = (-78).dp)
                                        ) {
                                            SmallFloatingActionButton(
                                                onClick = { stopwatchViewModel.recordLap() },
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = themeConfig.accentColor,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .bounceClick()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Flag,
                                                    contentDescription = "Отсечка",
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        // ЦЕНТРАЛЬНАЯ КНОПКА ДЕЙСТВИЯ
                                        FloatingActionButton(
                                            onClick = {
                                                when (activeTab) {
                                                    ClockTab.ALARM -> isAddingAlarm = true
                                                    ClockTab.WORLD_CLOCK -> showAddCityDialog = true
                                                    ClockTab.TIMER -> onTimerAction?.invoke()
                                                    ClockTab.STOPWATCH -> stopwatchViewModel.toggleStartPause()
                                                    ClockTab.SCENARY -> onScenaryAction?.invoke()
                                                }
                                            },
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .offset(y = (-40).dp)
                                                .size(58.dp)
                                                .bounceClick()
                                        ) {
                                            AnimatedContent(
                                                targetState = Pair(activeTab, isStopwatchRunning),
                                                transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                                                label = "FabIconTransition"
                                            ) { (tab, swRunning) ->
                                                when (tab) {
                                                    ClockTab.SCENARY -> {
                                                        ScenaryDocumentPlusIcon(
                                                            accentColor = MaterialTheme.colorScheme.primary,
                                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                    ClockTab.ALARM -> Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Добавить будильник",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    ClockTab.WORLD_CLOCK -> Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = "Поиск города",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    ClockTab.TIMER -> Icon(
                                                        imageVector = Icons.Default.HourglassEmpty,
                                                        contentDescription = "Новый таймер",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    ClockTab.STOPWATCH -> Icon(
                                                        imageVector = if (swRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "Старт/Пауза",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.background
                            ) { paddingValues ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                                    AnimatedContent(
                                        targetState = isScenaryTabActive,
                                        transitionSpec = {
                                            (fadeIn(tween(260)) + scaleIn(initialScale = 0.95f))
                                                .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.95f))
                                        },
                                        label = "ScenaryTabSwitch"
                                    ) { showScenary ->
                                        if (showScenary) {
                                            ScenaryScreen(
                                                onRegisterFabAction = { action ->
                                                    onScenaryAction = action
                                                }
                                            )
                                        } else {
                                            HorizontalPager(
                                                state = pagerState,
                                                modifier = Modifier.fillMaxSize()
                                            ) { page ->
                                                when (page) {
                                                    0 -> AlarmListScreen(
                                                        alarms = alarms,
                                                        onToggleAlarm = { alarm, isEnabled -> viewModel.toggleAlarm(alarm, isEnabled) },
                                                        onSkipNextAlarm = { alarm, skippedMillis -> viewModel.skipNextOccurrence(alarm, skippedMillis) },
                                                        onDeleteAlarms = { toDelete -> toDelete.forEach { alarm -> viewModel.deleteAlarm(alarm) } },
                                                        onAddAlarmClick = { isAddingAlarm = true },
                                                        onEditAlarmClick = { alarm -> editingAlarm = alarm }
                                                    )

                                                    1 -> WorldClockScreen(
                                                        onOpenSettings = { currentRoute = ScreenRoute.SETTINGS },
                                                        onOpenDevScreen = { currentRoute = ScreenRoute.DEV },
                                                        onOpenScenaryScreen = { isScenaryTabActive = true },
                                                        externalShowAddDialog = showAddCityDialog,
                                                        onDialogDismiss = { showAddCityDialog = false }
                                                    )

                                                    2 -> TimerScreen(
                                                        timerViewModel = timerViewModel,
                                                        onOpenSettings = { currentRoute = ScreenRoute.SETTINGS },
                                                        onTimerStateChanged = { _, action ->
                                                            onTimerAction = action
                                                        }
                                                    )

                                                    3 -> StopwatchScreen(stopwatchViewModel = stopwatchViewModel)
                                                    4 -> ScenaryScreen(
                                                        onRegisterFabAction = { action ->
                                                            onScenaryAction = action
                                                        }
                                                    )
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = isBetaUpdateAvailable && !isUpdateBubbleDismissed,
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 120.dp, start = 20.dp, end = 20.dp)
                                    ) {
                                        val bubbleShape = RoundedCornerShape(20.dp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pure3DEffect(
                                                    shape = bubbleShape,
                                                    accentColor = themeConfig.accentColor,
                                                    depthDp = 6.dp,
                                                    is3dEnabled = themeConfig.is3dEnabled,
                                                    isGlowEnabled = themeConfig.isGlowEnabled,
                                                    surfaceColor = MaterialTheme.colorScheme.surface
                                                )
                                                .clickable { currentRoute = ScreenRoute.SETTINGS }
                                                .bounceClick()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(themeConfig.accentColor.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Upgrade,
                                                            contentDescription = null,
                                                            tint = themeConfig.accentColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        val releaseTitle = if (latestBetaRelease?.isBeta == true) {
                                                            "Доступна новая бета ${latestBetaRelease?.tagName ?: ""}"
                                                        } else {
                                                            "Доступно новое обновление ${latestBetaRelease?.tagName ?: ""}"
                                                        }
                                                        Text(
                                                            text = releaseTitle,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = "Нажмите для перехода в настройки",
                                                            color = Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { isUpdateBubbleDismissed = true },
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .bounceClick()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Закрыть",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
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
        }
    }
}

@Composable
private fun ScenaryDocumentPlusIcon(
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val docLeft = w * 0.12f
        val docTop = h * 0.18f
        val docRight = w * 0.76f
        val docBottom = h * 0.90f
        val docWidth = docRight - docLeft
        val docHeight = docBottom - docTop
        val docCorner = w * 0.14f

        val docPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(
                        left = docLeft,
                        top = docTop,
                        right = docRight,
                        bottom = docBottom
                    ),
                    cornerRadius = CornerRadius(docCorner, docCorner)
                )
            )
        }

        val badgeCenter = Offset(w * 0.73f, h * 0.27f)
        val cutoutRadius = w * 0.25f

        val cutoutPath = Path().apply {
            addOval(Rect(center = badgeCenter, radius = cutoutRadius))
        }

        val finalDocPath = Path.combine(PathOperation.Difference, docPath, cutoutPath)

        drawPath(
            path = finalDocPath,
            color = contentColor
        )

        val lineStroke = h * 0.082f
        val lineStartX = docLeft + docWidth * 0.22f
        val lineShortEndX = docLeft + docWidth * 0.50f
        val lineFullEndX = docLeft + docWidth * 0.78f

        val line1Y = docTop + docHeight * 0.33f
        drawLine(
            color = accentColor,
            start = Offset(lineStartX, line1Y),
            end = Offset(lineShortEndX, line1Y),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )

        val line2Y = docTop + docHeight * 0.56f
        drawLine(
            color = accentColor,
            start = Offset(lineStartX, line2Y),
            end = Offset(lineFullEndX, line2Y),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )

        val line3Y = docTop + docHeight * 0.78f
        drawLine(
            color = accentColor,
            start = Offset(lineStartX, line3Y),
            end = Offset(lineFullEndX, line3Y),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )

        val plusHalfArm = cutoutRadius * 0.48f
        val plusStroke = w * 0.085f

        drawLine(
            color = contentColor,
            start = Offset(badgeCenter.x - plusHalfArm, badgeCenter.y),
            end = Offset(badgeCenter.x + plusHalfArm, badgeCenter.y),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = contentColor,
            start = Offset(badgeCenter.x, badgeCenter.y - plusHalfArm),
            end = Offset(badgeCenter.x, badgeCenter.y + plusHalfArm),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun NavTabItem3D(
    tab: ClockTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val defaultColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .bounceClick()
    ) {
        Pure3DIcon(
            tab = tab.tabType,
            isSelected = isSelected,
            onClick = onClick,
            size = 24.dp,
            activeColor = accentColor,
            inactiveColor = defaultColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tab.title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accentColor else defaultColor,
            maxLines = 1,
            softWrap = false
        )
    }
}