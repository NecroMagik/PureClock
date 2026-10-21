package com.necromagik.pureclock.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.data.repository.ScenaryRepository
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.components.EmptyScenaryState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenaryScreen(
    onRegisterFabAction: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    val repository = remember { ScenaryRepository.getInstance(context) }
    val scenarios by repository.scenariosFlow.collectAsState()
    val quickTemplates = remember { repository.getQuickTemplates() }

    var showBuilderSheet by remember { mutableStateOf(false) }
    var editingScenario by remember { mutableStateOf<ScenaryItem?>(null) }

    BackHandler(enabled = showBuilderSheet) {
        showBuilderSheet = false
        editingScenario = null
    }

    // Привязываем центральную кнопку док-бара (иконка документа с "+") к открытию конструктора
    LaunchedEffect(Unit) {
        onRegisterFabAction {
            editingScenario = null
            showBuilderSheet = true
        }
    }

    if (showBuilderSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBuilderSheet = false
                editingScenario = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            ScenaryBuilderSheet(
                initialScenario = editingScenario,
                accentColor = accentColor,
                onSave = { builtScenario ->
                    repository.addOrUpdateScenario(builtScenario)
                    showBuilderSheet = false
                    editingScenario = null
                },
                onCancel = {
                    showBuilderSheet = false
                    editingScenario = null
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Шапка с быстрыми шаблонами ("Охота за закатом", "Луна в зените" и т.д.)
        Text(
            text = "БЫСТРЫЕ ШАБЛОНЫ СЦЕНАРИЕВ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickTemplates) { template ->
                Box(
                    modifier = Modifier
                        .width(215.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        .clickable {
                            repository.addOrUpdateScenario(
                                template.copy(id = java.util.UUID.randomUUID().toString())
                            )
                        }
                        .bounceClick()
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (template.triggerType) {
                                    ScenaryTriggerType.SUNSET, ScenaryTriggerType.GOLDEN_HOUR -> Icons.Default.WbTwilight
                                    ScenaryTriggerType.FULL_MOON -> Icons.Default.NightsStay
                                    ScenaryTriggerType.SUNRISE -> Icons.Default.WbSunny
                                    else -> Icons.Default.Timer
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "+ Добавить",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = template.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = template.getFormattedTriggerSummary(),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "МОИ СЦЕНАРИИ (${scenarios.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            val alarmCount = scenarios.count { it.targetType == ScenaryTargetType.ALARM }
            val timerCount = scenarios.count { it.targetType == ScenaryTargetType.TIMER }
            Text(
                text = "Будильников: $alarmCount • Таймеров: $timerCount",
                fontSize = 11.sp,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedContent(
            targetState = scenarios.isEmpty(),
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "ScenaryEmptyStateTransition",
            modifier = Modifier.weight(1f)
        ) { isEmpty ->
            if (isEmpty) {
                EmptyScenaryState(
                    onAddScenaryClick = {
                        editingScenario = null
                        showBuilderSheet = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(scenarios, key = { it.id }) { item ->
                        ScenarioRecipeCard(
                            item = item,
                            accentColor = accentColor,
                            onToggle = { enabled -> repository.toggleScenario(item.id, enabled) },
                            onEdit = {
                                editingScenario = item
                                showBuilderSheet = true
                            },
                            onDelete = { repository.deleteScenario(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioRecipeCard(
    item: ScenaryItem,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val cardShape = RoundedCornerShape(themeConfig.cardCornerRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = cardShape,
                accentColor = if (item.isEnabled) accentColor else Color.Gray,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = item.isEnabled && themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .clickable { onEdit() }
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .background(accentColor.copy(alpha = if (item.isEnabled) 0.18f else 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.targetType == ScenaryTargetType.ALARM) Icons.Default.Alarm else Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = if (item.isEnabled) accentColor else Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.getFormattedTriggerSummary(),
                            fontSize = 12.sp,
                            color = if (item.isEnabled) accentColor else Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                PureSwitch(
                    checked = item.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (item.condition != ScenaryConditionType.ALWAYS) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Условие: ${item.condition.title}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Визуализация цепочки кубиков пост-действий
            if (item.actions.isNotEmpty()) {
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.actions.forEachIndexed { idx, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${step.type.title}: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                            Text(
                                text = step.parameter.ifBlank { "По умолчанию" },
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { com.necromagik.pureclock.util.ScenaryExecutor.executeScenario(context, item) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Проверить сценарий",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить сценарий",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// МОДАЛЬНЫЙ КОНСТРУКТОР СЦЕНАРИЯ (ВИЗАРД СБОРКИ КУБИКОВ)
// ============================================================================
@Composable
private fun ScenaryBuilderSheet(
    initialScenario: ScenaryItem?,
    accentColor: Color,
    onSave: (ScenaryItem) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(initialScenario?.title ?: "") }
    var targetType by remember { mutableStateOf(initialScenario?.targetType ?: ScenaryTargetType.ALARM) }
    var triggerType by remember { mutableStateOf(initialScenario?.triggerType ?: ScenaryTriggerType.SUNSET) }
    var offsetMinutes by remember { mutableIntStateOf(initialScenario?.offsetMinutes ?: -40) }
    var baseHour by remember { mutableIntStateOf(initialScenario?.baseHour ?: 7) }
    var baseMinute by remember { mutableIntStateOf(initialScenario?.baseMinute ?: 0) }
    var timerMinutes by remember { mutableIntStateOf(((initialScenario?.timerDurationSeconds ?: 300L) / 60L).toInt()) }
    var condition by remember { mutableStateOf(initialScenario?.condition ?: ScenaryConditionType.ALWAYS) }

    val actionsList = remember {
        mutableStateListOf<ScenaryActionStep>().apply {
            if (initialScenario != null && initialScenario.actions.isNotEmpty()) {
                addAll(initialScenario.actions)
            } else {
                add(
                    ScenaryActionStep(
                        type = ScenaryActionType.CUSTOM_NOTIFICATION,
                        parameter = "40 минут до захода — иди лови хороший свет!"
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (initialScenario == null) "Конструктор нового сценария" else "Редактирование сценария",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Название сценария") },
            placeholder = { Text("Поле ввода") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // ШАГ 1: К ЧЕМУ ПРИМЕНЯЕМ (БУДИЛЬНИК ИЛИ ТАЙМЕР)
        Text("1. Тип привязки", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ScenaryTargetType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = targetType == type,
                    onClick = {
                        targetType = type
                        if (type == ScenaryTargetType.TIMER) {
                            triggerType = ScenaryTriggerType.TIMER_COUNTDOWN
                        } else if (triggerType == ScenaryTriggerType.TIMER_COUNTDOWN) {
                            triggerType = ScenaryTriggerType.SUNSET
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(type.title, fontSize = 12.sp)
                }
            }
        }

        // ШАГ 2: ВЫБОР ТРИГГЕРА И СМЕЩЕНИЯ
        Text("2. Когда запускать?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor)

        if (targetType == ScenaryTargetType.ALARM) {
            val alarmTriggers = ScenaryTriggerType.entries.filter { it != ScenaryTriggerType.TIMER_COUNTDOWN }
            alarmTriggers.forEach { trig ->
                FilterChip(
                    selected = triggerType == trig,
                    onClick = { triggerType = trig },
                    label = { Text("${trig.title} • ${trig.description}", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (triggerType.isAstro) {
                val offsetLabel = when {
                    offsetMinutes < 0 -> "Сработать за ${-offsetMinutes} мин до события"
                    offsetMinutes > 0 -> "Сработать через +$offsetMinutes мин после события"
                    else -> "Ровно в момент наступления события"
                }
                Text(offsetLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = offsetMinutes.toFloat(),
                    onValueChange = { offsetMinutes = (it / 5f).roundToInt() * 5 },
                    valueRange = -90f..60f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("Время запуска: %02d:%02d", baseHour, baseMinute),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { baseHour = (baseHour + 1) % 24 }) { Text("+1 ч") }
                        OutlinedButton(onClick = { baseMinute = (baseMinute + 15) % 60 }) { Text("+15 м") }
                    }
                }
            }
        } else {
            Text("Длительность сценарного таймера: $timerMinutes мин", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = timerMinutes.toFloat(),
                onValueChange = { timerMinutes = it.roundToInt().coerceAtLeast(1) },
                valueRange = 1f..120f,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
            )
        }

        // ШАГ 3: УСЛОВИЕ СРАБАТЫВАНИЯ
        Text("3. Условие проверки", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScenaryConditionType.entries) { cond ->
                FilterChip(
                    selected = condition == cond,
                    onClick = { condition = cond },
                    label = { Text(cond.title, fontSize = 12.sp) }
                )
            }
        }

        // ШАГ 4: КОНСТРУКТОР КУБИКОВ ПОСТ-ДЕЙСТВИЙ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("4. Цепочка действий после сигнала", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor)
            TextButton(
                onClick = {
                    actionsList.add(
                        ScenaryActionStep(
                            type = ScenaryActionType.SHOW_BRIEFING,
                            parameter = "Показать сводку погоды и света"
                        )
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Добавить шаг", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        actionsList.forEachIndexed { index, step ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Шаг #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)
                        if (actionsList.size > 1) {
                            IconButton(
                                onClick = { actionsList.removeAt(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Убрать шаг", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Переключатель типа действия для данного кубика
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ScenaryActionType.entries) { actType ->
                            FilterChip(
                                selected = step.type == actType,
                                onClick = {
                                    actionsList[index] = step.copy(type = actType)
                                },
                                label = { Text(actType.title, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = step.parameter,
                        onValueChange = { newVal ->
                            actionsList[index] = step.copy(parameter = newVal)
                        },
                        label = { Text(step.type.defaultParamHint, fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f))
            ) {
                Text("Отмена", color = Color.Gray, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val finalTitle = title.trim().ifEmpty {
                        if (targetType == ScenaryTargetType.ALARM) triggerType.title else "Сценарный таймер"
                    }
                    onSave(
                        ScenaryItem(
                            id = initialScenario?.id ?: java.util.UUID.randomUUID().toString(),
                            title = finalTitle,
                            targetType = targetType,
                            isEnabled = initialScenario?.isEnabled ?: true,
                            triggerType = triggerType,
                            offsetMinutes = offsetMinutes,
                            baseHour = baseHour,
                            baseMinute = baseMinute,
                            timerDurationSeconds = timerMinutes * 60L,
                            condition = condition,
                            actions = actionsList.toList()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = SystemThemeUtils.getContrastingColor(accentColor)
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp)
                    .bounceClick(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Сохранить сценарий", fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}