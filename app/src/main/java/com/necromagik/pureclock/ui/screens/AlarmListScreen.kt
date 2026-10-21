package com.necromagik.pureclock.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.edit
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.model.ScenaryItem
import com.necromagik.pureclock.data.model.ScenaryTargetType
import com.necromagik.pureclock.data.repository.ScenaryRepository
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.staggeredEntrance
import com.necromagik.pureclock.ui.components.EmptyAlarmState
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.util.PermissionHelper
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private val RU_LOCALE = Locale.forLanguageTag("ru")

private val OFF_QUOTES = listOf(
    "Все будильники спят. И тебе пора.",
    "Спи спокойно, никаких тревог.",
    "Режим полного релакса активирован.",
    "Будильники берут выходной. Тишина...",
    "Никто тебя не разбудит. Отдыхай."
)

private enum class AlarmSubTab(val title: String) {
    MAIN("Основные"),
    SCENARY("Сценарий")
}

private data class UnifiedAlarmEntry(
    val key: String,
    val uiModel: AlarmUiModel,
    val dbAlarm: AlarmEntity? = null,
    val linkedScenario: ScenaryItem? = null,
    val isStandaloneScenario: Boolean = false
)

private data class PendingDeletionRequest(
    val entries: List<UnifiedAlarmEntry>,
    val isScenarioTab: Boolean,
    val distinctScenarios: List<ScenaryItem>,
    val step: Int = 1
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
    val scenaryRepository = remember { ScenaryRepository.getInstance(context) }

    val scenarios by scenaryRepository.scenariosFlow.collectAsState()
    val alarmBindings by scenaryRepository.alarmBindingsFlow.collectAsState()

    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    val prefs = remember(context) { context.getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE) }
    var isCardView by remember {
        mutableStateOf(prefs.getBoolean("is_alarm_card_view", true))
    }

    val validScenarioIds = remember(scenarios) { scenarios.map { it.id }.toSet() }
    val boundAlarmIds = remember(alarmBindings, validScenarioIds) {
        alarmBindings.filterValues { validScenarioIds.contains(it) }.keys
    }

    val mainEntries = remember(alarms, boundAlarmIds) {
        alarms
            .filterNot { boundAlarmIds.contains(it.id) }
            .map { alarm ->
                UnifiedAlarmEntry(
                    key = "db_${alarm.id}",
                    uiModel = alarm.toUiModel(),
                    dbAlarm = alarm,
                    linkedScenario = null,
                    isStandaloneScenario = false
                )
            }
    }

    val scenarioEntries = remember(alarms, boundAlarmIds, scenarios, alarmBindings) {
        val dbBoundList = alarms
            .filter { boundAlarmIds.contains(it.id) }
            .map { alarm ->
                val scen = scenaryRepository.getScenarioForAlarm(alarm.id)
                val baseUi = alarm.toUiModel()
                UnifiedAlarmEntry(
                    key = "db_${alarm.id}",
                    uiModel = if (scen != null) baseUi.copy(label = "${baseUi.label} • ${scen.title}") else baseUi,
                    dbAlarm = alarm,
                    linkedScenario = scen,
                    isStandaloneScenario = false
                )
            }

        val usedScenarioIds = alarmBindings.values.toSet()
        val standaloneList = scenarios
            .filter { it.targetType == ScenaryTargetType.ALARM && !usedScenarioIds.contains(it.id) }
            .map { scen ->
                val syntheticId = -abs(scen.id.hashCode().toLong()).coerceAtLeast(1L)
                UnifiedAlarmEntry(
                    key = "scen_${scen.id}",
                    uiModel = AlarmUiModel(
                        id = syntheticId,
                        time = String.format(Locale.ROOT, "%02d:%02d", scen.baseHour, scen.baseMinute),
                        days = scen.getFormattedTriggerSummary(),
                        label = scen.title,
                        isEnabled = scen.isEnabled,
                        isSkippedToday = false
                    ),
                    dbAlarm = null,
                    linkedScenario = scen,
                    isStandaloneScenario = true
                )
            }

        dbBoundList + standaloneList
    }

    val hasAnyScenarioAlarms = scenarioEntries.isNotEmpty()
    var selectedSubTab by remember { mutableStateOf(AlarmSubTab.MAIN) }

    LaunchedEffect(hasAnyScenarioAlarms) {
        if (!hasAnyScenarioAlarms && selectedSubTab == AlarmSubTab.SCENARY) {
            selectedSubTab = AlarmSubTab.MAIN
        }
    }

    val currentDisplayedEntries = if (selectedSubTab == AlarmSubTab.SCENARY && hasAnyScenarioAlarms) {
        scenarioEntries
    } else {
        mainEntries
    }

    var selectedEntryKeys by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedEntryKeys.isNotEmpty()
    var alarmToDisable by remember { mutableStateOf<AlarmEntity?>(null) }
    var pendingDeletion by remember { mutableStateOf<PendingDeletionRequest?>(null) }

    BackHandler(enabled = isSelectionMode || alarmToDisable != null || pendingDeletion != null) {
        when {
            pendingDeletion != null -> pendingDeletion = null
            alarmToDisable != null -> alarmToDisable = null
            else -> selectedEntryKeys = emptySet()
        }
    }

    LaunchedEffect(selectedSubTab) {
        selectedEntryKeys = emptySet()
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
            delay(1.seconds)
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

    fun requestDeletionFor(entriesToDelete: List<UnifiedAlarmEntry>) {
        if (entriesToDelete.isEmpty()) return
        val isScenTab = selectedSubTab == AlarmSubTab.SCENARY && hasAnyScenarioAlarms
        val distinctScenarios = entriesToDelete
            .mapNotNull { it.linkedScenario }
            .distinctBy { it.id }

        pendingDeletion = PendingDeletionRequest(
            entries = entriesToDelete,
            isScenarioTab = isScenTab && distinctScenarios.isNotEmpty(),
            distinctScenarios = distinctScenarios,
            step = 1
        )
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
                    title = {
                        Text(
                            text = "Выбрано: ${selectedEntryKeys.size}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedEntryKeys = emptySet() }, modifier = Modifier.bounceClick()) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val allKeys = currentDisplayedEntries.map { it.key }.toSet()
                                selectedEntryKeys = if (selectedEntryKeys.size == allKeys.size) emptySet() else allKeys
                            },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = {
                                val chosen = currentDisplayedEntries.filter { selectedEntryKeys.contains(it.key) }
                                requestDeletionFor(chosen)
                            },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить выбранные", tint = MaterialTheme.colorScheme.error)
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            AnimatedVisibility(
                visible = hasAnyScenarioAlarms && !isSelectionMode,
                enter = fadeIn(tween(250)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                SubTabSelectorRow(
                    tabs = AlarmSubTab.entries.map { it.title },
                    selectedIndex = selectedSubTab.ordinal,
                    accentColor = themeConfig.accentColor,
                    onTabSelected = { index ->
                        selectedSubTab = AlarmSubTab.entries[index]
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (!isAllPermissionsGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .pure3DEffect(
                            shape = cardShape,
                            accentColor = Color(0xFFFF9800),
                            depthDp = themeConfig.depthIntensityDp,
                            is3dEnabled = themeConfig.is3dEnabled,
                            isGlowEnabled = themeConfig.isGlowEnabled,
                            surfaceColor = MaterialTheme.colorScheme.surface
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
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
                            Text(text = subtitle, color = Color.Gray, fontSize = 11.sp)
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
                targetState = currentDisplayedEntries.isEmpty(),
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
                                    .padding(bottom = 14.dp)
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(themeConfig.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (nearestActiveAlarmInfo != null) Icons.Default.Schedule else Icons.Default.Bedtime,
                                            contentDescription = null,
                                            tint = themeConfig.accentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
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
                                            prefs.edit { putBoolean("is_alarm_card_view", newMode) }
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

                        val handleEntryToggle: (UnifiedAlarmEntry) -> Unit = { entry ->
                            val dbAlarm = entry.dbAlarm
                            if (dbAlarm != null) {
                                if (dbAlarm.isEnabled && dbAlarm.skippedDateMillis == null && (dbAlarm.daysOfWeek != 0 || !dbAlarm.extraDatesStr.isNullOrBlank())) {
                                    alarmToDisable = dbAlarm
                                } else {
                                    onToggleAlarm(dbAlarm, !dbAlarm.isEnabled || dbAlarm.skippedDateMillis != null)
                                }
                            } else if (entry.linkedScenario != null) {
                                scenaryRepository.toggleScenario(entry.linkedScenario.id, !entry.linkedScenario.isEnabled)
                            }
                        }

                        val handleEntryEdit: (UnifiedAlarmEntry) -> Unit = { entry ->
                            selectedEntryKeys = emptySet()
                            if (entry.dbAlarm != null) {
                                onEditAlarmClick(entry.dbAlarm)
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                                ) {
                                    itemsIndexed(currentDisplayedEntries, key = { _, item -> item.key }) { index, entry ->
                                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                            AlarmItem(
                                                alarm = entry.uiModel,
                                                isCardView = true,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedEntryKeys.contains(entry.key),
                                                onToggle = { handleEntryToggle(entry) },
                                                onEditClick = { handleEntryEdit(entry) },
                                                onLongClick = { selectedEntryKeys = selectedEntryKeys + entry.key },
                                                onSelectToggle = {
                                                    selectedEntryKeys = if (selectedEntryKeys.contains(entry.key)) {
                                                        selectedEntryKeys - entry.key
                                                    } else {
                                                        selectedEntryKeys + entry.key
                                                    }
                                                },
                                                onDeleteSingleClick = { requestDeletionFor(listOf(entry)) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                                ) {
                                    itemsIndexed(currentDisplayedEntries, key = { _, item -> item.key }) { index, entry ->
                                        Box(modifier = Modifier.staggeredEntrance(index = index)) {
                                            AlarmItem(
                                                alarm = entry.uiModel,
                                                isCardView = false,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedEntryKeys.contains(entry.key),
                                                onToggle = { handleEntryToggle(entry) },
                                                onEditClick = { handleEntryEdit(entry) },
                                                onLongClick = { selectedEntryKeys = selectedEntryKeys + entry.key },
                                                onSelectToggle = {
                                                    selectedEntryKeys = if (selectedEntryKeys.contains(entry.key)) {
                                                        selectedEntryKeys - entry.key
                                                    } else {
                                                        selectedEntryKeys + entry.key
                                                    }
                                                },
                                                onDeleteSingleClick = { requestDeletionFor(listOf(entry)) }
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

    pendingDeletion?.let { req ->
        var selectedScenarioIdsToDelete by remember(req) {
            mutableStateOf(req.distinctScenarios.map { it.id }.toSet())
        }

        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                val titleText = when {
                    !req.isScenarioTab -> if (req.entries.size == 1) "Удалить будильник?" else "Удалить будильники (${req.entries.size})?"
                    req.step == 1 -> "Удаление сценарного будильника"
                    else -> "Выберите сценарии для удаления"
                }
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                if (!req.isScenarioTab) {
                    Text(
                        text = if (req.entries.size == 1) {
                            "Будильник на ${req.entries.first().uiModel.time} будет безвозвратно удалён."
                        } else {
                            "Выбранные будильники (${req.entries.size} шт.) будут безвозвратно удалены."
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                } else if (req.step == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (req.distinctScenarios.size == 1) {
                                "Связанный сценарий: «${req.distinctScenarios.first().title}». Что именно вы хотите удалить?"
                            } else {
                                "Выбраны будильники из ${req.distinctScenarios.size} разных сценариев. Выберите действие:"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    val dbToDelete = req.entries.mapNotNull { it.dbAlarm }
                                    dbToDelete.forEach { scenaryRepository.bindAlarmToScenario(it.id, null) }
                                    if (dbToDelete.isNotEmpty()) {
                                        onDeleteAlarms(dbToDelete)
                                    }
                                    req.entries.filter { it.isStandaloneScenario }.forEach {
                                        it.linkedScenario?.let { sc -> scenaryRepository.deleteScenario(sc.id) }
                                    }
                                    selectedEntryKeys = emptySet()
                                    pendingDeletion = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AlarmOff, contentDescription = null, tint = themeConfig.accentColor)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (req.entries.size == 1) "Удалить только будильник" else "Удалить только будильники (${req.entries.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Сам сценарий останется сохранённым в разделе Сценарии",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    if (req.distinctScenarios.size > 1) {
                                        pendingDeletion = req.copy(step = 2)
                                    } else {
                                        val scenId = req.distinctScenarios.first().id
                                        val dbToDelete = alarms.filter { alarmBindings[it.id] == scenId } +
                                                req.entries.mapNotNull { it.dbAlarm }
                                        scenaryRepository.deleteScenario(scenId)
                                        if (dbToDelete.isNotEmpty()) {
                                            onDeleteAlarms(dbToDelete.distinctBy { it.id })
                                        }
                                        selectedEntryKeys = emptySet()
                                        pendingDeletion = null
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (req.distinctScenarios.size == 1) {
                                            "Удалить целый сценарий «${req.distinctScenarios.first().title}»"
                                        } else {
                                            "Удалить вместе со сценариями..."
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (req.distinctScenarios.size == 1) {
                                            "Полное удаление сценария и его будильника"
                                        } else {
                                            "Выбрать один конкретный или удалить все (${req.distinctScenarios.size})"
                                        },
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Отметьте сценарии, которые нужно полностью удалить:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )

                        req.distinctScenarios.forEach { scen ->
                            val isChecked = selectedScenarioIdsToDelete.contains(scen.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        selectedScenarioIdsToDelete = if (isChecked) {
                                            selectedScenarioIdsToDelete - scen.id
                                        } else {
                                            selectedScenarioIdsToDelete + scen.id
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedScenarioIdsToDelete = if (isChecked) {
                                            selectedScenarioIdsToDelete - scen.id
                                        } else {
                                            selectedScenarioIdsToDelete + scen.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(scen.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(scen.getFormattedTriggerSummary(), fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!req.isScenarioTab) {
                    Button(
                        onClick = {
                            val toDelete = req.entries.mapNotNull { it.dbAlarm }
                            toDelete.forEach { scenaryRepository.bindAlarmToScenario(it.id, null) }
                            onDeleteAlarms(toDelete)
                            selectedEntryKeys = emptySet()
                            pendingDeletion = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("Удалить", fontWeight = FontWeight.Bold)
                    }
                } else if (req.step == 2) {
                    Button(
                        enabled = selectedScenarioIdsToDelete.isNotEmpty(),
                        onClick = {
                            val dbToDelete = alarms.filter { alarm ->
                                val scId = alarmBindings[alarm.id]
                                scId != null && selectedScenarioIdsToDelete.contains(scId)
                            }
                            selectedScenarioIdsToDelete.forEach { scId ->
                                scenaryRepository.deleteScenario(scId)
                            }
                            if (dbToDelete.isNotEmpty()) {
                                onDeleteAlarms(dbToDelete)
                            }
                            selectedEntryKeys = emptySet()
                            pendingDeletion = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.bounceClick()
                    ) {
                        val btnLabel = if (selectedScenarioIdsToDelete.size == req.distinctScenarios.size) {
                            "Удалить оба сценария"
                        } else {
                            "Удалить выбранный (${selectedScenarioIdsToDelete.size})"
                        }
                        Text(btnLabel, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (req.isScenarioTab && req.step == 2) {
                            pendingDeletion = req.copy(step = 1)
                        } else {
                            pendingDeletion = null
                        }
                    },
                    modifier = Modifier.bounceClick()
                ) {
                    Text(if (req.isScenarioTab && req.step == 2) "Назад" else "Отмена", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
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
                                Text("Будильник останется активным и сработает в следующий день", fontSize = 11.sp, color = Color.Gray)
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

@Composable
fun SubTabSelectorRow(
    tabs: List<String>,
    selectedIndex: Int,
    accentColor: Color,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalPureClockConfig.current
    val containerShape = RoundedCornerShape(themeConfig.cardCornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = containerShape,
                accentColor = accentColor,
                depthDp = 4.dp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = false,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val itemShape = RoundedCornerShape((themeConfig.cardCornerRadius.value - 4f).coerceAtLeast(8f).dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(itemShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .bounceClick(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) {
                            SystemThemeUtils.getContrastingColor(accentColor)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        }
                    )
                }
            }
        }
    }
}

private fun AlarmEntity.toUiModel(): AlarmUiModel {
    val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    val today = java.time.LocalDate.now()
    val systemZone = ZoneId.systemDefault()

    val extraDates = parseExtraDates()
    val excludedDates = parseExcludedDates()
    val skippedMillis = skippedDateMillis
    val isSkipped = isEnabled && skippedMillis != null

    val formattedDays = when {
        isSkipped -> {
            val date = Instant.ofEpochMilli(skippedMillis).atZone(systemZone).toLocalDate()
            "Пропущен: ${date.format(DateTimeFormatter.ofPattern("d MMM", RU_LOCALE))}"
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
        isEnabled = isEnabled,
        isSkippedToday = isSkipped
    )
}