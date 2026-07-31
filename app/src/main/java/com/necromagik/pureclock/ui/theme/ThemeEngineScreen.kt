package com.necromagik.pureclock.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.ThemeState
import com.necromagik.pureclock.ui.animation.bounceClick

// ============================================================================
// СЕКЦИЯ 1: ТИПЫ ТЕМ И ПРЕСЕТЫ
// ============================================================================
enum class AppThemeStyle {
    SYSTEM_MONET,
    TACTILE_3D,
    NEON_CYBER
}

const val SYSTEM_COLOR_MARKER = "SYSTEM_MONET"

val ExtendedAccents = listOf(
    SYSTEM_COLOR_MARKER to "Система (Monet)",
    "#00E676" to "Pure Green",
    "#EB0029" to "Oxygen Red",
    "#00E5FF" to "Cyber Cyan",
    "#7C4DFF" to "Electric Purple",
    "#FF6D00" to "Sunset Orange",
    "#FFD600" to "Volt Yellow",
    "#FF4081" to "Neon Pink",
    "#00B0FF" to "Sky Blue",
    "#AEEA00" to "Lime Volt",
    "#FFFFFF" to "Pure White",
    "#000000" to "Pure Black"
)

data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val accentHex: String,
    val isPureMonocolor: Boolean,
    val themeMode: String,
    val cornerRadiusDp: Int,
    val is3dEnabled: Boolean = true,
    val isGlowEnabled: Boolean = true,
    val depthDp: Int = 8
)

val ThemePresetsList = listOf(
    ThemePreset(
        id = "TACTILE_3D_CYBER",
        name = "Tactile 3D",
        description = "Выраженный объем, неоновое свечение канта и мягкие многослойные тени",
        accentHex = "#00E5FF",
        isPureMonocolor = false,
        themeMode = "DARK",
        cornerRadiusDp = 20,
        is3dEnabled = true,
        isGlowEnabled = true,
        depthDp = 12
    ),
    ThemePreset(
        id = "PURE_AMOLED",
        name = "Pure AMOLED",
        description = "Глубокий чёрный #000000 фон, экономия энергии и умеренный объем",
        accentHex = "#00E676",
        isPureMonocolor = true,
        themeMode = "DARK",
        cornerRadiusDp = 16,
        is3dEnabled = true,
        isGlowEnabled = true,
        depthDp = 6
    ),
    ThemePreset(
        id = "PURE_FLAT_MINIMAL",
        name = "Flat Minimal",
        description = "Абсолютно плоский чистый интерфейс без теней и объёма для максимального энергосбережения",
        accentHex = "#FFFFFF",
        isPureMonocolor = true,
        themeMode = "DARK",
        cornerRadiusDp = 12,
        is3dEnabled = false,
        isGlowEnabled = false,
        depthDp = 0
    ),
    ThemePreset(
        id = "OXYGEN_CLASSIC",
        name = "Oxygen Classic",
        description = "Фирменный стиль OnePlus с акцентом Oxygen Red и строгой геометрией",
        accentHex = "#EB0029",
        isPureMonocolor = false,
        themeMode = "SYSTEM",
        cornerRadiusDp = 16,
        is3dEnabled = true,
        isGlowEnabled = false,
        depthDp = 6
    )
)

// ============================================================================
// СЕКЦИЯ 2: ОСНОВНОЙ ЭКРАН КОНСТРУКТОРА ТЕМ
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEngineScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val themeState by settingsManager.themeState.collectAsState()
    val currentConfig = LocalPureClockConfig.current

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    val animatedCornerRadius by animateDpAsState(
        targetValue = currentConfig.cardCornerRadius,
        animationSpec = spring(stiffness = 300f),
        label = "PreviewCornerRadius"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.bounceClick()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ThemePreviewCard(
                accentColor = currentConfig.accentColor,
                cornerRadius = animatedCornerRadius
            )

            ThemePresetsSection(
                accentColor = currentConfig.accentColor,
                themeState = themeState,
                onPresetSelect = { preset ->
                    settingsManager.accentColorHex = preset.accentHex
                    settingsManager.isPureMonocolor = preset.isPureMonocolor
                    settingsManager.themeMode = preset.themeMode
                    settingsManager.cardCornerRadiusDp = preset.cornerRadiusDp
                    settingsManager.is3DEffectsEnabled = preset.is3dEnabled
                    settingsManager.isNeonGlowEnabled = preset.isGlowEnabled
                    settingsManager.depthIntensityDp = preset.depthDp
                }
            )

            // БЛОК УПРАВЛЕНИЯ 3D DEPTH ENGINE
            DepthEngineSection(
                accentColor = currentConfig.accentColor,
                cornerRadius = currentConfig.cardCornerRadius,
                themeState = themeState,
                on3DToggle = { settingsManager.is3DEffectsEnabled = it },
                onGlowToggle = { settingsManager.isNeonGlowEnabled = it },
                onDepthChange = { settingsManager.depthIntensityDp = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThemeBaseModeSection(
                accentColor = currentConfig.accentColor,
                cornerRadius = currentConfig.cardCornerRadius,
                currentThemeMode = themeState.themeMode,
                onModeSelect = { settingsManager.themeMode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdvancedCustomizationSection(
                accentColor = currentConfig.accentColor,
                cornerRadius = currentConfig.cardCornerRadius,
                themeState = themeState,
                isExpanded = isAdvancedExpanded,
                onToggleExpand = { isAdvancedExpanded = !isAdvancedExpanded },
                onPureMonocolorToggle = { settingsManager.isPureMonocolor = it },
                onOpenColorDialog = { showCustomColorDialog = true },
                onSelectSystemAccent = {
                    val freshSystemHex = SystemThemeUtils.getSystemAccentHex(context)
                    settingsManager.accentColorHex = freshSystemHex
                },
                onAccentColorSelect = { hex -> settingsManager.accentColorHex = hex },
                onCornerRadiusSelect = { radius -> settingsManager.cardCornerRadiusDp = radius }
            )
        }
    }

    if (showCustomColorDialog) {
        AdvancedColorPickerDialog(
            accentColor = currentConfig.accentColor,
            initialHex = themeState.accentColorHex,
            onDismiss = { showCustomColorDialog = false },
            onApply = { hex -> settingsManager.accentColorHex = hex }
        )
    }
}

// ============================================================================
// СЕКЦИЯ 3: СЕКЦИЯ DEPTH ENGINE (3D И СВЕЧЕНИЕ)
// ============================================================================
@Composable
private fun DepthEngineSection(
    accentColor: Color,
    cornerRadius: Dp,
    themeState: ThemeState,
    on3DToggle: (Boolean) -> Unit,
    onGlowToggle: (Boolean) -> Unit,
    onDepthChange: (Int) -> Unit
) {
    SectionHeader(
        text = "ОБЪЁМ И 3D-ЭФФЕКТЫ (DEPTH ENGINE)",
        accentColor = accentColor,
        icon = {
            Icon(
                imageVector = Icons.Default.ViewInAr,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    )

    Card(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Переключатель 3D Обмена
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3D-Эффекты и слоистые тени",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (themeState.is3DEffectsEnabled) "Включен объем Drop Shadow" else "Плоский минимализм (2D Flat)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = themeState.is3DEffectsEnabled,
                    onCheckedChange = on3DToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accentColor,
                        checkedThumbColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            AnimatedVisibility(visible = themeState.is3DEffectsEnabled) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    // Переключатель Неонового Свечения
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Неоновое свечение (Glow Effect)",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Светодиодные фаски карточек и неоновый ореол цифр",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = themeState.isNeonGlowEnabled,
                            onCheckedChange = onGlowToggle,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = accentColor,
                                checkedThumbColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Слайдер Глубины
                    Text(
                        text = "Глубина объема: ${themeState.depthIntensityDp} dp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = themeState.depthIntensityDp.toFloat(),
                        onValueChange = { onDepthChange(it.toInt()) },
                        valueRange = 2f..16f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// СЕКЦИЯ 4: ИЗОЛИРОВАННЫЕ ВПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================================
@Composable
private fun SectionHeader(
    text: String,
    accentColor: Color,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    ) {
        icon?.invoke()
        if (icon != null) Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

@Composable
private fun ThemePreviewCard(
    accentColor: Color,
    cornerRadius: Dp
) {
    val themeConfig = LocalPureClockConfig.current
    SectionHeader(text = "ПРЕДПРОСМОТР В РЕАЛЬНОМ ВРЕМЕНИ", accentColor = accentColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .bounceClick()
            .pure3DEffect(
                shape = RoundedCornerShape(cornerRadius),
                accentColor = accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "07:30",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Будильник • Пн - Пт",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Сигнал сработает через 8 ч 15 мин",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accentColor,
                        checkedThumbColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemePresetsSection(
    accentColor: Color,
    themeState: ThemeState,
    onPresetSelect: (ThemePreset) -> Unit
) {
    SectionHeader(
        text = "ГОТОВЫЕ ПРЕСЕТЫ",
        accentColor = accentColor,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        items(ThemePresetsList, key = { it.id }) { preset ->
            val isSelected = themeState.accentColorHex.equals(preset.accentHex, ignoreCase = true) &&
                    themeState.cardCornerRadiusDp == preset.cornerRadiusDp &&
                    themeState.is3DEffectsEnabled == preset.is3dEnabled

            Card(
                shape = RoundedCornerShape(preset.cornerRadiusDp.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isSelected) BorderStroke(2.dp, accentColor) else null,
                modifier = Modifier
                    .width(200.dp)
                    .bounceClick { onPresetSelect(preset) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(preset.accentHex.toColorInt()))
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preset.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preset.description,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp,
                        maxLines = 3
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeBaseModeSection(
    accentColor: Color,
    cornerRadius: Dp,
    currentThemeMode: String,
    onModeSelect: (String) -> Unit
) {
    SectionHeader(text = "БАЗОВЫЙ РЕЖИМ", accentColor = accentColor)

    Card(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Системная тема",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentThemeMode == "SYSTEM",
                    onClick = { onModeSelect("SYSTEM") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Система", fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = currentThemeMode == "DARK",
                    onClick = { onModeSelect("DARK") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Тёмная", fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = currentThemeMode == "LIGHT",
                    onClick = { onModeSelect("LIGHT") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Светлая", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AdvancedCustomizationSection(
    accentColor: Color,
    cornerRadius: Dp,
    themeState: ThemeState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPureMonocolorToggle: (Boolean) -> Unit,
    onOpenColorDialog: () -> Unit,
    onSelectSystemAccent: () -> Unit,
    onAccentColorSelect: (String) -> Unit,
    onCornerRadiusSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val systemAccentColor = SystemThemeUtils.rememberSystemAccentColor()
    val isSystemAccentSelected = SystemThemeUtils.isSystemSelected(themeState.accentColorHex, context)
    val contrastingContentColor = SystemThemeUtils.getContrastingColor(systemAccentColor)

    Card(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onToggleExpand() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Расширенная кастомизация",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isExpanded) "Нажмите, чтобы скрыть" else "Свои цвета, AMOLED, скругления",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
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
        enter = fadeIn(tween(300)) + expandVertically(),
        exit = fadeOut(tween(200)) + shrinkVertically()
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Card(
                shape = RoundedCornerShape(cornerRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Чистый монохром (Pure Contrast)",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Абсолютно чёрный #000000 / Белый #FFFFFF",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = themeState.isPureMonocolor,
                            onCheckedChange = onPureMonocolorToggle,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = accentColor,
                                checkedThumbColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Цветовой акцент",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = onOpenColorDialog,
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Точный выбор", color = accentColor, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ExtendedAccents) { (hex, name) ->
                            val isSystemMarker = hex == SYSTEM_COLOR_MARKER
                            val color = if (isSystemMarker) systemAccentColor else Color(hex.toColorInt())
                            val isSelected = if (isSystemMarker) {
                                isSystemAccentSelected
                            } else {
                                !isSystemAccentSelected && themeState.accentColorHex.equals(hex, ignoreCase = true)
                            }

                            val itemContentColor = SystemThemeUtils.getContrastingColor(color)

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else if (isSystemMarker) 1.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else if (isSystemMarker) Color.Gray.copy(alpha = 0.3f) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .bounceClick {
                                        if (isSystemMarker) {
                                            onSelectSystemAccent()
                                        } else {
                                            onAccentColorSelect(hex)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSystemMarker) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = name,
                                        tint = contrastingContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = itemContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    Text(
                        text = "Форма элементов (Скругления)",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCornerRadiusSelect(8) },
                            modifier = Modifier.weight(1f).bounceClick(),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (themeState.cardCornerRadiusDp == 8) accentColor else Color.Gray.copy(alpha = 0.4f)
                                )
                            )
                        ) {
                            Text("Sharp (8dp)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onCornerRadiusSelect(16) },
                            modifier = Modifier.weight(1f).bounceClick(),
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (themeState.cardCornerRadiusDp == 16) accentColor else Color.Gray.copy(alpha = 0.4f)
                                )
                            )
                        ) {
                            Text("Classic (16dp)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onCornerRadiusSelect(24) },
                            modifier = Modifier.weight(1f).bounceClick(),
                            shape = RoundedCornerShape(24.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (themeState.cardCornerRadiusDp == 24) accentColor else Color.Gray.copy(alpha = 0.4f)
                                )
                            )
                        ) {
                            Text("Round (24dp)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// СЕКЦИЯ 5: РАСШИРЕННЫЙ ДИАЛОГ ТОЧНОГО ВЫБОРА ЦВЕТА
// ============================================================================
@Composable
private fun AdvancedColorPickerDialog(
    accentColor: Color,
    initialHex: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(initialHex) }
    var hueValue by remember { mutableFloatStateOf(180f) }
    var isError by remember { mutableStateOf(false) }

    val quickPalette = listOf(
        "#00E676", "#00E5FF", "#3D5AFE", "#7C4DFF",
        "#FF1744", "#FF9100", "#FFD600", "#FFFFFF"
    )

    val parsedPreviewColor = remember(hexInput) {
        try {
            val formattedHex = if (!hexInput.startsWith("#")) "#$hexInput" else hexInput
            Color(formattedHex.toColorInt())
        } catch (_: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Конструктор цвета", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Выберите оттенок слайдером или введите HEX-код:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(parsedPreviewColor ?: Color.Transparent)
                            .border(2.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (parsedPreviewColor != null) "Активный цвет" else "Ошибка формата",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (parsedPreviewColor != null) accentColor else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = hexInput.uppercase(),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Спектр оттенков (Hue):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )

                Slider(
                    value = hueValue,
                    onValueChange = { newHue ->
                        hueValue = newHue
                        val colorFromHue = android.graphics.Color.HSVToColor(floatArrayOf(newHue, 0.9f, 0.95f))
                        hexInput = String.format("#%06X", 0xFFFFFF and colorFromHue)
                        isError = false
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Быстрый выбор:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickPalette.forEach { qHex ->
                        val qColor = Color(qHex.toColorInt())
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(qColor)
                                .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    hexInput = qHex
                                    isError = false
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        hexInput = it
                        isError = false
                    },
                    label = { Text("HEX / Color Code") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Text(
                        text = "Некорректный HEX-формат (например, #00E676)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val formattedHex = if (!hexInput.startsWith("#")) "#$hexInput" else hexInput
                        formattedHex.toColorInt()
                        onApply(formattedHex)
                        onDismiss()
                    } catch (_: Exception) {
                        isError = true
                    }
                },
                modifier = Modifier.bounceClick()
            ) {
                Text("Применить", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bounceClick()
            ) {
                Text("Отмена", color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}