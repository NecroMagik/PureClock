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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.widget.WidgetRenderEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetEditorScreen(
    initialConfig: WidgetConfig,
    onSaveConfig: (WidgetConfig) -> Unit,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val themeConfig = LocalPureClockConfig.current
    var config by remember { mutableStateOf(initialConfig) }

    var expandedBubble by remember { mutableStateOf<String?>(null) }

    val liveBitmap = remember(config) {
        WidgetRenderEngine.renderCustomWidgetBitmap(context, config)
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
            // Живое превью Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = liveBitmap.asImageBitmap(),
                    contentDescription = "Live Canvas Preview",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // БАББЛ 1: ПОЗИЦИОНИРОВАНИЕ (3x3)
                item {
                    ExpandableSettingBubble(
                        title = "Позиционирование (Сетка 3×3)",
                        subtitle = "Смещение часов и содержимого",
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

                // БАББЛ 2: ЧАСЫ И 5 СТИЛЕЙ
                item {
                    ExpandableSettingBubble(
                        title = "Настройка часов",
                        subtitle = "${if (config.safeDisplayMode == ClockDisplayMode.ANALOG) "Аналоговые" else "Цифровые"} • ${config.timeFontSizeSp} sp",
                        icon = Icons.Default.Schedule,
                        isExpanded = expandedBubble == "CLOCK",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "CLOCK") null else "CLOCK" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Формат циферблата", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ClockDisplayMode.values().forEachIndexed { index, mode ->
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
                            Text("Выбор стиля циферблата (5 вариантов)", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                            if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                                AnalogStyleType.values().forEach { style ->
                                    FilterChip(
                                        selected = config.safeAnalogStyle == style,
                                        onClick = { config = config.copy(analogStyle = style) },
                                        label = { Text(style.name) },
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            } else {
                                DigitalStyleType.values().forEach { style ->
                                    FilterChip(
                                        selected = config.safeDigitalStyle == style,
                                        onClick = { config = config.copy(digitalStyle = style) },
                                        label = { Text(style.name) },
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Размер часов: ${config.timeFontSizeSp} sp (45 - 200)", fontSize = 13.sp)
                            Slider(
                                value = config.timeFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(timeFontSizeSp = it.toInt()) },
                                valueRange = 45f..200f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // БАББЛ 3: ДАТА
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
                            Text("Размер шрифта даты: ${config.dateFontSizeSp} sp (12 - 70)", fontSize = 13.sp)
                            Slider(
                                value = config.dateFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(dateFontSizeSp = it.toInt()) },
                                valueRange = 12f..70f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // БАББЛ 4: ПОГОДА
                item {
                    ExpandableSettingBubble(
                        title = "Блок погоды",
                        subtitle = if (config.showWeather) "Включена • ${config.weatherFontSizeSp} sp" else "Отключена",
                        icon = Icons.Default.WbSunny,
                        isExpanded = expandedBubble == "WEATHER",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showWeather,
                        onCheckChange = { config = config.copy(showWeather = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "WEATHER") null else "WEATHER" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Размер шрифта погоды: ${config.weatherFontSizeSp} sp (12 - 70)", fontSize = 13.sp)
                            Slider(
                                value = config.weatherFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(weatherFontSizeSp = it.toInt()) },
                                valueRange = 12f..70f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // БАББЛ 5: ФОН
                item {
                    ExpandableSettingBubble(
                        title = "Параметры фона",
                        subtitle = "Прозрачность ${(config.backgroundAlpha * 100).toInt()}%",
                        icon = Icons.Default.FormatPaint,
                        isExpanded = expandedBubble == "BACKGROUND",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "BACKGROUND") null else "BACKGROUND" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Прозрачность подложки: ${(config.backgroundAlpha * 100).toInt()}%", fontSize = 13.sp)
                            Slider(
                                value = config.backgroundAlpha,
                                onValueChange = { config = config.copy(backgroundAlpha = it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )
                        }
                    }
                }

                // БАББЛ 6: КОНТУРНАЯ РАМКА И СRoundings
                item {
                    ExpandableSettingBubble(
                        title = "Контурная рамка и скругление",
                        subtitle = if (config.showBorder) "Толщина ${config.borderWidthDp} dp • Скругление ${config.cornerRadiusDp} dp" else "Рамка выключена",
                        icon = Icons.Default.CropFree,
                        isExpanded = expandedBubble == "BORDER",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showBorder,
                        onCheckChange = { config = config.copy(showBorder = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "BORDER") null else "BORDER" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Толщина рамки: ${config.borderWidthDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.borderWidthDp.toFloat(),
                                onValueChange = { config = config.copy(borderWidthDp = it.toInt()) },
                                valueRange = 1f..12f,
                                colors = SliderDefaults.colors(thumbColor = themeConfig.accentColor)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Скругление углов: ${config.cornerRadiusDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.cornerRadiusDp.toFloat(),
                                onValueChange = { config = config.copy(cornerRadiusDp = it.toInt()) },
                                valueRange = 0f..48f,
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
                        Switch(
                            checked = isChecked,
                            onCheckedChange = {
                                onCheckChange(it)
                                if (it && !isExpanded) onToggle()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
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