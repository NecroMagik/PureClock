package com.necromagik.pureclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.ui.animation.PureClockAnimationSpecs
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.BottomBarTab
import com.necromagik.pureclock.ui.components.Pure3DIcon
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AlarmViewModel) {
    var currentRoute by remember { mutableStateOf(ScreenRoute.MAIN) }

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
            AddEditAlarmScreen(
                editingAlarmId = alarmToEdit?.id,
                initialHour = alarmToEdit?.hour ?: 14,
                initialMinute = alarmToEdit?.minute ?: 10,
                initialLabel = alarmToEdit?.label ?: "Будильник",
                initialRingtoneUri = alarmToEdit?.ringtoneUri,
                initialDaysOfWeek = alarmToEdit?.daysOfWeek ?: 0,
                initialSpecificDateMillis = alarmToEdit?.specificDateMillis,
                onSave = { hour, minute, days, dates, label, _, isVibrate ->
                    val daysMask = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
                    val specificDate = if (dates.isNotEmpty()) {
                        dates.minOrNull()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    } else null

                    viewModel.saveAlarm(
                        id = alarmToEdit?.id ?: 0L,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = daysMask,
                        specificDateMillis = specificDate,
                        label = label,
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
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                            topBar = {
                                val currentTab = ClockTab.entries[pagerState.currentPage]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTab.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { currentRoute = ScreenRoute.SETTINGS },
                                        modifier = Modifier.bounceClick()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Настройки"
                                        )
                                    }
                                }
                            },
                            bottomBar = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pure3DEffect(
                                                shape = barShape,
                                                accentColor = themeConfig.accentColor,
                                                surfaceColor = MaterialTheme.colorScheme.surface,
                                                depthDp = themeConfig.depthIntensityDp,
                                                is3dEnabled = themeConfig.is3dEnabled,
                                                isGlowEnabled = themeConfig.isGlowEnabled
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(84.dp)
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
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

                                            Spacer(modifier = Modifier.width(80.dp))

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

                                    Box(
                                        modifier = Modifier
                                            .offset(y = (-40).dp)
                                            .size(87.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.background)
                                    )

                                    val currentTab = ClockTab.entries[pagerState.currentPage]
                                    val isStopwatchTab = currentTab == ClockTab.STOPWATCH

                                    // ЛЕВАЯ КНОПКА: СБРОС (по диагонали влево-вверх под 45°)
                                    AnimatedVisibility(
                                        visible = isStopwatchTab && (stopwatchElapsed > 0 && !isStopwatchRunning),
                                        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                slideInHorizontally(initialOffsetX = { it }) +
                                                slideInVertically(initialOffsetY = { it }),
                                        exit = scaleOut() + slideOutHorizontally(targetOffsetX = { it }) + slideOutVertically(targetOffsetY = { it }),
                                        modifier = Modifier.offset(x = (-52).dp, y = (-98).dp)
                                    ) {
                                        SmallFloatingActionButton(
                                            onClick = { stopwatchViewModel.reset() },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = Color.Gray,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .bounceClick()
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Сброс")
                                        }
                                    }

                                    // ПРАВАЯ КНОПКА: ОТСЕЧКА КРУГА (по диагонали вправо-вверх под 45°)
                                    AnimatedVisibility(
                                        visible = isStopwatchTab && isStopwatchRunning,
                                        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                slideInHorizontally(initialOffsetX = { -it }) +
                                                slideInVertically(initialOffsetY = { it }),
                                        exit = scaleOut() + slideOutHorizontally(targetOffsetX = { -it }) + slideOutVertically(targetOffsetY = { it }),
                                        modifier = Modifier.offset(x = 52.dp, y = (-98).dp)
                                    ) {
                                        SmallFloatingActionButton(
                                            onClick = { stopwatchViewModel.recordLap() },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = themeConfig.accentColor,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .bounceClick()
                                        ) {
                                            Icon(Icons.Default.Flag, contentDescription = "Отсечка")
                                        }
                                    }

                                    // ЦЕНТРАЛЬНАЯ КНОПКА FAB
                                    val fabIcon = when (currentTab) {
                                        ClockTab.ALARM -> Icons.Default.Add
                                        ClockTab.WORLD_CLOCK -> Icons.Default.Search
                                        ClockTab.TIMER -> Icons.Default.HourglassEmpty
                                        ClockTab.STOPWATCH -> if (isStopwatchRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                                    }

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
                                            .offset(y = (-46).dp)
                                            .size(75.dp)
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
                                                modifier = Modifier.size(38.dp)
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
        modifier = modifier.fillMaxHeight()
    ) {
        Pure3DIcon(
            tab = tab.tabType,
            isSelected = isSelected,
            onClick = onClick,
            size = 26.dp,
            activeColor = accentColor,
            inactiveColor = defaultColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tab.title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accentColor else defaultColor
        )
    }
}