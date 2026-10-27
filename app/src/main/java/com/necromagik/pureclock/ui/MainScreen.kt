package com.necromagik.pureclock.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.necromagik.pureclock.ui.animation.rememberScenaryDockChoreography
import com.necromagik.pureclock.ui.components.BottomBarTab
import com.necromagik.pureclock.ui.components.Pure3DIcon
import com.necromagik.pureclock.ui.components.ScenaryPostTriggerDialog
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
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
import com.necromagik.pureclock.ui.theme.ThemeEngineScreen
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.AlarmViewModel
import com.necromagik.pureclock.ui.viewmodel.StopwatchViewModel
import com.necromagik.pureclock.ui.viewmodel.TimerViewModel
import com.necromagik.pureclock.util.ScenaryExecutor
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

enum class ClockTab(val title: String, val tabType: BottomBarTab) {
    ALARM("Будильник", BottomBarTab.ALARM),
    WORLD_CLOCK("Время", BottomBarTab.WORLD_CLOCK),
    TIMER("Таймер", BottomBarTab.TIMER),
    STOPWATCH("Секундомер", BottomBarTab.STOPWATCH),
    SCENARY("Сценарии", BottomBarTab.SCENARY)
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

        if (notchRadiusPx <= 0.5f) {
            return Outline.Generic(basePath)
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
    val hasPlayedScenaryDockIntro by settingsManager.hasPlayedScenaryDockIntroFlow.collectAsState()
    val startMainTab by settingsManager.startMainTabFlow.collectAsState()

    val timerViewModel: TimerViewModel = viewModel()
    val stopwatchViewModel: StopwatchViewModel = viewModel()

    var dockAnimReplayTrigger by remember { mutableIntStateOf(0) }

    val maxPages = if (ifRelease == 1) 5 else 4
    val pagerState = rememberPagerState(initialPage = startMainTab.coerceIn(0, maxPages - 1)) { maxPages }
    val coroutineScope = rememberCoroutineScope()
    val themeConfig = LocalPureClockConfig.current

    var isScenaryTabActive by remember { mutableStateOf(false) }

    LaunchedEffect(ifRelease) {
        if (ifRelease == 1 && isScenaryTabActive) {
            isScenaryTabActive = false
            pagerState.animateScrollToPage(4)
        }
    }

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

    var backPressedOnceTime by remember { mutableLongStateOf(0L) }
    val activity = context as? Activity

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
                isScenaryTabActive = false
            }
            pagerState.currentPage != startMainTab -> {
                coroutineScope.launch { pagerState.animateScrollToPage(startMainTab) }
            }
            else -> {
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
                                onBack = { currentRoute = ScreenRoute.MAIN },
                                onTriggerDockUnlockAnimation = {
                                    settingsManager.hasPlayedScenaryDockIntro = false
                                    settingsManager.ifRelease = 1
                                    dockAnimReplayTrigger += 1
                                    currentRoute = ScreenRoute.MAIN
                                }
                            )
                        }

                        ScreenRoute.MAIN -> {
                            val isBetaUpdateAvailable by settingsManager.isBetaUpdateAvailableFlow.collectAsState()
                            val latestBetaRelease by settingsManager.latestBetaReleaseFlow.collectAsState()

                            val dockChoreography = rememberScenaryDockChoreography(
                                isFiveTabMode = ifRelease == 1,
                                hasPlayedIntro = hasPlayedScenaryDockIntro,
                                replayTrigger = dockAnimReplayTrigger,
                                onIntroFinished = {
                                    settingsManager.hasPlayedScenaryDockIntro = true
                                }
                            )

                            LaunchedEffect(dockChoreography.isScenarySlotVisible) {
                                if (dockChoreography.isScenarySlotVisible && (!hasPlayedScenaryDockIntro || dockAnimReplayTrigger > 0)) {
                                    triggerDockRevealHaptic(context)
                                }
                            }

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
                                        val dockShape = remember(dockChoreography.notchRadius) {
                                            NotchedDockShape(
                                                cornerRadius = 32.dp,
                                                notchRadius = dockChoreography.notchRadius
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .pure3DEffect(
                                                    shape = dockShape,
                                                    accentColor = themeConfig.accentColor,
                                                    surfaceColor = MaterialTheme.colorScheme.surface,
                                                    depthDp = themeConfig.depthIntensityDp + (dockChoreography.dockHighlightAlpha * 6f).dp,
                                                    is3dEnabled = themeConfig.is3dEnabled,
                                                    isGlowEnabled = themeConfig.isGlowEnabled || dockChoreography.dockHighlightAlpha > 0.05f
                                                )
                                                .then(
                                                    if (dockChoreography.dockHighlightAlpha > 0.01f) {
                                                        Modifier.border(
                                                            width = (1.5f + dockChoreography.dockHighlightAlpha * 1.5f).dp,
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    themeConfig.accentColor.copy(alpha = 0.25f * dockChoreography.dockHighlightAlpha),
                                                                    themeConfig.accentColor.copy(alpha = 0.95f * dockChoreography.dockHighlightAlpha),
                                                                    themeConfig.accentColor.copy(alpha = 1f * dockChoreography.dockHighlightAlpha)
                                                                )
                                                            ),
                                                            shape = dockShape
                                                        )
                                                    } else Modifier
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
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

                                                if (dockChoreography.centerSpacerWidth > 0.5.dp) {
                                                    Spacer(modifier = Modifier.width(dockChoreography.centerSpacerWidth))
                                                }

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

                                                // СПРАВА НАЛЕВО ВЫЕЗЖАЕТ 5-Я ВКЛАДКА "СЦЕНАРИИ", СДВИГАЯ ОСТАЛЬНЫЕ 4 ИКОНКИ ВЛЕВО
                                                AnimatedVisibility(
                                                    visible = dockChoreography.isScenarySlotVisible,
                                                    enter = expandHorizontally(
                                                        expandFrom = Alignment.End,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    ) + slideInHorizontally(
                                                        initialOffsetX = { fullWidth -> fullWidth },
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    ) + fadeIn(tween(260)),
                                                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) +
                                                            slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) +
                                                            fadeOut(tween(200)),
                                                    modifier = Modifier.weight(
                                                        dockChoreography.iconRevealProgress.coerceAtLeast(0.01f)
                                                    )
                                                ) {
                                                    ScenaryDockTabItem3D(
                                                        isSelected = isScenaryTabActive || pagerState.currentPage == 4,
                                                        revealProgress = dockChoreography.iconRevealProgress,
                                                        wiggleDegrees = dockChoreography.iconWiggleDegrees,
                                                        spotlightRingProgress = dockChoreography.spotlightRingProgress,
                                                        onClick = {
                                                            if (ifRelease == 1) {
                                                                isScenaryTabActive = false
                                                                coroutineScope.launch { pagerState.animateScrollToPage(4) }
                                                            } else {
                                                                isScenaryTabActive = true
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }

                                        // Всплывающий презентационный бейдж "НОВОЕ" над правым краем док-бара
                                        AnimatedVisibility(
                                            visible = dockChoreography.showNewBadge,
                                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                    slideInVertically(initialOffsetY = { it / 2 }) +
                                                    fadeIn(tween(220)),
                                            exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(300)),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 14.dp)
                                                .offset(y = (-80).dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = themeConfig.accentColor,
                                                shadowElevation = 8.dp
                                            ) {
                                                Text(
                                                    text = "НОВОЕ",
                                                    color = SystemThemeUtils.getContrastingColor(themeConfig.accentColor),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.8.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        val isStopwatchTab = activeTab == ClockTab.STOPWATCH
                                        val secondaryFabOffsetY = dockChoreography.fabOffsetY - 38.dp

                                        AnimatedVisibility(
                                            visible = isStopwatchTab && (stopwatchElapsed > 0 && !isStopwatchRunning),
                                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                    slideInHorizontally(initialOffsetX = { it }) +
                                                    slideInVertically(initialOffsetY = { it }),
                                            exit = scaleOut() + slideOutHorizontally(targetOffsetX = { it }) + slideOutVertically(targetOffsetY = { it }),
                                            modifier = Modifier.offset(x = (-84).dp, y = secondaryFabOffsetY)
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
                                            modifier = Modifier.offset(x = 84.dp, y = secondaryFabOffsetY)
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
                                                .offset(y = dockChoreography.fabOffsetY)
                                                .size(58.dp)
                                                .graphicsLayer {
                                                    scaleX = dockChoreography.fabScale
                                                    scaleY = dockChoreography.fabScale
                                                }
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
                                                        onOpenScenaryScreen = {
                                                            if (ifRelease == 1) {
                                                                coroutineScope.launch { pagerState.animateScrollToPage(4) }
                                                            } else {
                                                                isScenaryTabActive = true
                                                            }
                                                        },
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

/**
 * Вкладка "Сценарии" в док-панели, использующая единый компонент Pure3DIcon(BottomBarTab.SCENARY)
 * и добавляющая презентационное световое кольцо при первом появлении справа налево.
 */
@Composable
private fun ScenaryDockTabItem3D(
    isSelected: Boolean,
    revealProgress: Float,
    wiggleDegrees: Float,
    spotlightRingProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val defaultColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val isHighlighted = isSelected || spotlightRingProgress > 0.05f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                alpha = revealProgress.coerceIn(0f, 1f)
                val entranceScale = 0.72f + 0.28f * revealProgress
                scaleX = entranceScale
                scaleY = entranceScale
                rotationZ = wiggleDegrees
            }
            .clickable { onClick() }
            .bounceClick()
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (spotlightRingProgress in 0.01f..0.99f) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val ringRadius = (size.minDimension * 0.45f) + (size.minDimension * 0.90f * spotlightRingProgress)
                    val ringAlpha = (1f - spotlightRingProgress).coerceIn(0f, 1f)
                    drawCircle(
                        color = accentColor.copy(alpha = 0.70f * ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 2.2.dp.toPx() * ringAlpha)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f * ringAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ringRadius
                        ),
                        radius = ringRadius,
                        center = center
                    )
                }
            }

            Pure3DIcon(
                tab = BottomBarTab.SCENARY,
                isSelected = isHighlighted,
                onClick = onClick,
                size = 24.dp,
                activeColor = accentColor,
                inactiveColor = defaultColor
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = ClockTab.SCENARY.title,
            fontSize = 10.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlighted) accentColor else defaultColor,
            maxLines = 1,
            softWrap = false
        )
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

private fun triggerDockRevealHaptic(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 45), -1))
    } catch (_: Exception) {}
}