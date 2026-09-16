package com.necromagik.pureclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
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
import com.necromagik.pureclock.ui.animation.PureClockAnimationSpecs
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.BottomBarTab
import com.necromagik.pureclock.ui.components.Pure3DIcon
import com.necromagik.pureclock.ui.components.SettingsTopBarIcon
import com.necromagik.pureclock.ui.screens.AddEditAlarmScreen
import com.necromagik.pureclock.ui.screens.AlarmListScreen
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
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

enum class ClockTab(val title: String, val tabType: BottomBarTab) {
    ALARM("Будильник", BottomBarTab.ALARM),
    WORLD_CLOCK("Время", BottomBarTab.WORLD_CLOCK),
    TIMER("Таймер", BottomBarTab.TIMER),
    STOPWATCH("Секундомер", BottomBarTab.STOPWATCH)
}

enum class ScreenRoute {
    MAIN,
    SETTINGS,
    THEME_ENGINE
}

/**
 * Островная форма док-панели с вырезом под выступающую центральную кнопку
 */
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
                    topLeft = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                    topRight = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                    bottomLeft = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                    bottomRight = androidx.compose.ui.geometry.CornerRadius(cornerPx)
                )
            )
        }

        val cutoutPath = Path().apply {
            addOval(
                Rect(
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
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

    val timerViewModel: TimerViewModel = viewModel()
    val stopwatchViewModel: StopwatchViewModel = viewModel()
    val pagerState = rememberPagerState(initialPage = 0) { ClockTab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    val themeConfig = LocalPureClockConfig.current

    var isAddingAlarm by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    var onTimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isStopwatchRunning by stopwatchViewModel.isRunning.collectAsState()
    val stopwatchElapsed by stopwatchViewModel.elapsedMillis.collectAsState()

    val alarms by viewModel.alarms.collectAsState()

    var isUpdateBubbleDismissed by remember { mutableStateOf(false) }

    BackHandler(enabled = currentRoute != ScreenRoute.MAIN || isAddingAlarm || editingAlarm != null) {
        when {
            isAddingAlarm || editingAlarm != null -> {
                isAddingAlarm = false
                editingAlarm = null
            }
            currentRoute == ScreenRoute.THEME_ENGINE -> currentRoute = ScreenRoute.SETTINGS
            currentRoute == ScreenRoute.SETTINGS -> currentRoute = ScreenRoute.MAIN
        }
    }

    AnimatedContent(
        targetState = isAddingAlarm || editingAlarm != null,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn()).togetherWith(
                    scaleOut(targetScale = 0.95f, animationSpec = PureClockAnimationSpecs.SmoothTween) + fadeOut()
                )
            } else {
                (scaleIn(initialScale = 0.95f, animationSpec = PureClockAnimationSpecs.SmoothTween) + fadeIn()).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
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
                    when {
                        initialState == ScreenRoute.MAIN && targetState == ScreenRoute.SETTINGS -> {
                            (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(300))).togetherWith(fadeOut(tween(200)))
                        }
                        initialState == ScreenRoute.SETTINGS && targetState == ScreenRoute.MAIN -> {
                            fadeIn(tween(300)).togetherWith(slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(200)))
                        }
                        targetState == ScreenRoute.THEME_ENGINE -> {
                            (slideInHorizontally(initialOffsetX = { it }) + fadeIn()).togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut())
                        }
                        else -> {
                            (slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()).togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
                        }
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

                    ScreenRoute.MAIN -> {
                        val isBetaUpdateAvailable by settingsManager.isBetaUpdateAvailableFlow.collectAsState()
                        val latestBetaRelease by settingsManager.latestBetaReleaseFlow.collectAsState()

                        LaunchedEffect(Unit) {
                            if (settingsManager.allowBetaUpdates) {
                                val release = SettingsManager.UpdateChecker.checkForBetaUpdates("1.29")
                                settingsManager.setBetaUpdateInfo(release)
                            }
                        }

                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            topBar = {
                                val currentTab = ClockTab.entries[pagerState.currentPage]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTab.title,
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

                                    // Парящая широкая док-панель
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
                                                isSelected = pagerState.currentPage == 0,
                                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                modifier = Modifier.weight(1f)
                                            )
                                            NavTabItem3D(
                                                tab = ClockTab.WORLD_CLOCK,
                                                isSelected = pagerState.currentPage == 1,
                                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(modifier = Modifier.width(56.dp))

                                            NavTabItem3D(
                                                tab = ClockTab.TIMER,
                                                isSelected = pagerState.currentPage == 2,
                                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                                modifier = Modifier.weight(1f)
                                            )
                                            NavTabItem3D(
                                                tab = ClockTab.STOPWATCH,
                                                isSelected = pagerState.currentPage == 3,
                                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    val currentTab = ClockTab.entries[pagerState.currentPage]
                                    val isStopwatchTab = currentTab == ClockTab.STOPWATCH

                                    // Кнопка секундомера "Сброс"
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

                                    // Кнопка секундомера "Круг"
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

                                    val fabIcon = when (currentTab) {
                                        ClockTab.ALARM -> Icons.Default.Add
                                        ClockTab.WORLD_CLOCK -> Icons.Default.Search
                                        ClockTab.TIMER -> Icons.Default.HourglassEmpty
                                        ClockTab.STOPWATCH -> if (isStopwatchRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                                    }

                                    // Компактная центральная кнопка с заметным выступом вверх
                                    FloatingActionButton(
                                        onClick = {
                                            when (currentTab) {
                                                ClockTab.ALARM -> isAddingAlarm = true
                                                ClockTab.WORLD_CLOCK -> showAddCityDialog = true
                                                ClockTab.TIMER -> onTimerAction?.invoke()
                                                ClockTab.STOPWATCH -> stopwatchViewModel.toggleStartPause()
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
                                            targetState = fabIcon,
                                            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                                            label = "FabIconTransition"
                                        ) { icon ->
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Действие",
                                                modifier = Modifier.size(26.dp)
                                            )
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
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    when (ClockTab.entries[page]) {
                                        ClockTab.ALARM -> AlarmListScreen(
                                            alarms = alarms,
                                            onToggleAlarm = { alarm, isEnabled -> viewModel.toggleAlarm(alarm, isEnabled) },
                                            onSkipNextAlarm = { alarm, skippedMillis -> viewModel.skipNextOccurrence(alarm, skippedMillis) },
                                            onDeleteAlarms = { toDelete -> toDelete.forEach { alarm -> viewModel.deleteAlarm(alarm) } },
                                            onAddAlarmClick = { isAddingAlarm = true },
                                            onEditAlarmClick = { alarm -> editingAlarm = alarm }
                                        )

                                        ClockTab.WORLD_CLOCK -> WorldClockScreen(
                                            onOpenSettings = { currentRoute = ScreenRoute.SETTINGS },
                                            externalShowAddDialog = showAddCityDialog,
                                            onDialogDismiss = { showAddCityDialog = false }
                                        )

                                        ClockTab.TIMER -> {
                                            TimerScreen(
                                                timerViewModel = timerViewModel,
                                                onOpenSettings = { currentRoute = ScreenRoute.SETTINGS },
                                                onTimerStateChanged = { _, action ->
                                                    onTimerAction = action
                                                }
                                            )
                                        }

                                        ClockTab.STOPWATCH -> {
                                            StopwatchScreen(stopwatchViewModel = stopwatchViewModel)
                                        }
                                    }
                                }

                                // Баббл обновления
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
                                                    Text(
                                                        text = "Вышла бета ${latestBetaRelease?.tagName ?: ""}",
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