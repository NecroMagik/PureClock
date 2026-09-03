package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.widget.WidgetRenderEngine

enum class WidgetPreviewRatio(val label: String, val widthPx: Int, val heightPx: Int, val ratio: Float) {
    SIZE_5x2("5×2", 1080, 440, 2.45f),
    SIZE_4x2("4×2", 900, 450, 2.0f),
    SIZE_3x3("3×3", 720, 720, 1.0f),
    SIZE_2x2("2×2", 500, 500, 1.0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetEditorScreen(
    initialConfig: WidgetConfig,
    onSaveConfig: (WidgetConfig) -> Unit,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    var config by remember { mutableStateOf(initialConfig) }

    var expandedBubble by remember { mutableStateOf<String?>(null) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedRatio by remember { mutableStateOf(WidgetPreviewRatio.SIZE_5x2) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val liveBitmap = remember(config, currentTimeMillis, selectedRatio) {
        WidgetRenderEngine.renderCustomWidgetBitmap(
            context = context,
            config = config,
            canvasWidth = selectedRatio.widthPx,
            canvasHeight = selectedRatio.heightPx
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конструктор виджета", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.bounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                .padding(horizontal = 16.dp)
        ) {
            // Реалистичное превью с точным соотношением сторон
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(24.dp))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(selectedRatio.ratio)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = liveBitmap.asImageBitmap(),
                        contentDescription = "Live Canvas Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Переключатель пропорций сетки
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    WidgetPreviewRatio.entries.forEach { ratio ->
                        val isSelected = selectedRatio == ratio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) themeConfig.accentColor else Color.Transparent)
                                .clickable { selectedRatio = ratio }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Порядок слоев
                item {
                    ExpandableSettingBubble(
                        title = "Порядок элементов",
                        subtitle = "Вертикальная последовательность",
                        icon = Icons.Default.Reorder,
                        isExpanded = expandedBubble == "ORDER",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "ORDER") null else "ORDER" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentList = config.safeElementOrder.toMutableList()
                            currentList.forEachIndexed { index, type ->
                                val title = when (type) {
                                    WidgetElementType.TIME -> "⏰ Циферблат / Часы"
                                    WidgetElementType.DATE -> "📅 Блок даты"
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val updated = currentList.toMutableList()
                                                        val temp = updated[index]
                                                        updated[index] = updated[index - 1]
                                                        updated[index - 1] = temp
                                                        config = config.copy(elementOrder = updated)
                                                    }
                                                },
                                                enabled = index > 0
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Вверх")
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < currentList.size - 1) {
                                                        val updated = currentList.toMutableList()
                                                        val temp = updated[index]
                                                        updated[index] = updated[index + 1]
                                                        updated[index + 1] = temp
                                                        config = config.copy(elementOrder = updated)
                                                    }
                                                },
                                                enabled = index < currentList.size - 1
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Вниз")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Сетка 3x3
                item {
                    ExpandableSettingBubble(
                        title = "Позиционирование (Сетка 3×3)",
                        subtitle = "Смещение часов на экране",
                        icon = Icons.Default.GridView,
                        isExpanded = expandedBubble == "POSITION",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "POSITION") null else "POSITION" }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            JoystickGrid3x3(
                                currentPosition = config.safePosition,
                                accentColor = themeConfig.accentColor,
                                onPositionSelected = { config = config.copy(position = it) }
                            )
                        }
                    }
                }

                // 3. Часы
                item {
                    ExpandableSettingBubble(
                        title = "Стилизация циферблата",
                        subtitle = "${if (config.safeDisplayMode == ClockDisplayMode.ANALOG) config.safeAnalogStyle.title else config.safeDigitalStyle.title}",
                        icon = Icons.Default.Schedule,
                        isExpanded = expandedBubble == "CLOCK",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "CLOCK") null else "CLOCK" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Формат циферблата", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ClockDisplayMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = config.safeDisplayMode == mode,
                                        onClick = { config = config.copy(displayMode = mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                    ) {
                                        Text(if (mode == ClockDisplayMode.ANALOG) "Аналоговый" else "Цифровой", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Дизайн циферблата", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                            if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                                AnalogStyleType.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.safeAnalogStyle == style,
                                        onClick = { config = config.copy(analogStyle = style) },
                                        label = { Text(style.title) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    )
                                }
                            } else {
                                DigitalStyleType.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.safeDigitalStyle == style,
                                        onClick = { config = config.copy(digitalStyle = style) },
                                        label = { Text(style.title) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Масштаб часов: ${config.timeFontSizeSp} sp", fontSize = 13.sp)
                            Slider(
                                value = config.timeFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(timeFontSizeSp = it.toInt()) },
                                valueRange = 36f..240f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // 4. Дата
                item {
                    ExpandableSettingBubble(
                        title = "Блок даты",
                        subtitle = if (config.showDate) "Включена • ${config.dateFontSizeSp} sp" else "Отключена",
                        icon = Icons.Default.Event,
                        isExpanded = expandedBubble == "DATE",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showDate,
                        onCheckChange = { config = config.copy(showDate = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "DATE") null else "DATE" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Жирный шрифт даты", fontSize = 13.sp)
                                PureSwitch(
                                    checked = config.isDateBold,
                                    onCheckedChange = { config = config.copy(isDateBold = it) }
                                )
                            }

                            Text("Размер шрифта даты: ${config.dateFontSizeSp} sp", fontSize = 13.sp)
                            Slider(
                                value = config.dateFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(dateFontSizeSp = it.toInt()) },
                                valueRange = 12f..80f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // 5. Фон и скругление
                item {
                    ExpandableSettingBubble(
                        title = "Фон подложки",
                        subtitle = if (config.showBackground) "Прозрачность ${(config.backgroundAlpha * 100).toInt()}% • ${config.cornerRadiusDp} dp" else "Фон отключен",
                        icon = Icons.Default.FormatPaint,
                        isExpanded = expandedBubble == "BACKGROUND",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showBackground,
                        onCheckChange = { config = config.copy(showBackground = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "BACKGROUND") null else "BACKGROUND" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Прозрачность фона: ${(config.backgroundAlpha * 100).toInt()}%", fontSize = 13.sp)
                            Slider(
                                value = config.backgroundAlpha,
                                onValueChange = { config = config.copy(backgroundAlpha = it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )

                            Text("Скругление углов: ${config.cornerRadiusDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.cornerRadiusDp.toFloat(),
                                onValueChange = { config = config.copy(cornerRadiusDp = it.toInt()) },
                                valueRange = 0f..80f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // 6. Рамка
                item {
                    ExpandableSettingBubble(
                        title = "Контурная рамка",
                        subtitle = if (config.showBorder) "Стиль ${config.borderStyle.name} • ${config.borderWidthDp} dp" else "Рамка отключена",
                        icon = Icons.Default.CropFree,
                        isExpanded = expandedBubble == "BORDER",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showBorder,
                        onCheckChange = { config = config.copy(showBorder = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "BORDER") null else "BORDER" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Стиль границы", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BorderStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.borderStyle == style,
                                        onClick = { config = config.copy(borderStyle = style) },
                                        label = { Text(style.name) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Неоновое свечение границы", fontSize = 13.sp)
                                PureSwitch(
                                    checked = config.enableBorderGlow,
                                    onCheckedChange = { config = config.copy(enableBorderGlow = it) }
                                )
                            }

                            Text("Толщина границы: ${config.borderWidthDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.borderWidthDp.toFloat(),
                                onValueChange = { config = config.copy(borderWidthDp = it.toInt()) },
                                valueRange = 1f..10f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onSaveConfig(config) },
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(),
                colors = ButtonDefaults.buttonColors(containerColor = themeConfig.accentColor, contentColor = Color.Black)
            ) {
                Text("Сохранить изменения", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExpandableSettingBubble(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    accentColor: Color,
    hasSwitch: Boolean = false,
    isChecked: Boolean = false,
    onCheckChange: ((Boolean) -> Unit)? = null,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSwitch && onCheckChange != null) {
                        PureSwitch(
                            checked = isChecked,
                            onCheckedChange = {
                                onCheckChange(it)
                                if (it && !isExpanded) onToggle()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(250)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun JoystickGrid3x3(
    currentPosition: ClockPosition,
    accentColor: Color,
    onPositionSelected: (ClockPosition) -> Unit
) {
    val positionsGrid = listOf(
        listOf(ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT),
        listOf(ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT),
        listOf(ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT)
    )

    Column(
        modifier = Modifier
            .width(180.dp)
            .height(180.dp)
            .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        positionsGrid.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { pos ->
                    val isSelected = currentPosition == pos
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.08f))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .bounceClick { onPositionSelected(pos) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 12.dp else 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color.Black else Color.Gray)
                        )
                    }
                }
            }
        }
    }
}