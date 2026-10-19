package com.necromagik.pureclock.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.widget.WidgetRenderEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WidgetPreviewRatio(val label: String, val desc: String, val widthPx: Int, val heightPx: Int, val ratio: Float) {
    SIZE_5x2("5×2", "Широкий", 1080, 440, 2.45f),
    SIZE_4x2("4×2", "Стандарт", 900, 450, 2.0f),
    SIZE_3x3("3×3", "Крупный", 720, 720, 1.0f),
    SIZE_2x2("2×2", "Компакт", 500, 500, 1.0f)
}

enum class PreviewWallpaperPreset(val title: String) {
    CYBER_NIGHT("Ночь"),
    DAYLIGHT("Светлый"),
    VIBRANT_ART("Яркий"),
    TRANSPARENT_GRID("Сетка")
}

private val WidgetColorPalette = listOf(
    "#FFFFFF" to "Белый",
    "#00E676" to "Pure Green",
    "#EB0029" to "Oxygen Red",
    "#00E5FF" to "Cyber Cyan",
    "#FFD600" to "Янтарь",
    "#FF6D00" to "Оранжевый",
    "#7C4DFF" to "Фиолетовый",
    "#FF4081" to "Розовый",
    "#00B0FF" to "Голубой",
    "#AEEA00" to "Лайм",
    "#8E94A0" to "Сталь",
    "#181B24" to "Графит",
    "#053326" to "Изумруд",
    "#422C03" to "Тёмный янтарь",
    "#000000" to "Чёрный"
)

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

    var expandedBubble by remember { mutableStateOf<String?>("BACKGROUND") }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedRatio by remember { mutableStateOf(WidgetPreviewRatio.SIZE_5x2) }
    var selectedWallpaper by remember { mutableStateOf(PreviewWallpaperPreset.CYBER_NIGHT) }
    var selectedDigitIndex by remember { mutableIntStateOf(0) }

    BackHandler(enabled = expandedBubble != null) {
        expandedBubble = null
    }

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
                title = {
                    Column {
                        Text("Конструктор виджета", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "Живой макет рабочего стола",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.bounceClick()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val sysHex = SystemThemeUtils.getSystemAccentHex(context)
                            config = WidgetConfig(
                                id = config.id,
                                dateColorHexNullable = sysHex,
                                borderColorHexNullable = sysHex,
                                digitColorsHex = listOf(sysHex, "#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFFFFF")
                            )
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Сбросить по умолчанию",
                            tint = Color.Gray
                        )
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
                .padding(horizontal = 18.dp)
        ) {
            // =========================================================================
            // ПРЕМИАЛЬНЫЙ БЛОК ПРЕДПРОСМОТРА (ИМИТАЦИЯ ЭКРАНА СМАРТФОНА С ОБОЯМИ)
            // =========================================================================
            PremiumHomescreenPreviewCard(
                bitmap = liveBitmap,
                selectedRatio = selectedRatio,
                selectedWallpaper = selectedWallpaper,
                accentColor = themeConfig.accentColor,
                onSelectRatio = { selectedRatio = it },
                onSelectWallpaper = { selectedWallpaper = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // СПИСОК НАСТРОЕК КОНСТРУКТОРА
            // =========================================================================
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // 1. ФОН И КОНТРАСТ С СИСТЕМОЙ (КАК НА СКРИНШОТАХ)
                item {
                    ExpandableSettingBubble(
                        title = "Фон и контраст с системой",
                        subtitle = if (config.showBackground) {
                            "${config.safeBackgroundMode.title} • ${(config.backgroundAlpha * 100).toInt()}%"
                        } else {
                            "Прозрачный фон (только текст)"
                        },
                        icon = Icons.Default.FormatPaint,
                        isExpanded = expandedBubble == "BACKGROUND",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showBackground,
                        onCheckChange = { config = config.copy(showBackground = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "BACKGROUND") null else "BACKGROUND" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Режим окраски подложки", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                            WidgetBackgroundMode.entries.forEach { mode ->
                                val isSelected = config.safeBackgroundMode == mode
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) themeConfig.accentColor.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) themeConfig.accentColor else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { config = config.copy(backgroundMode = mode, showBackground = true) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isSelected) themeConfig.accentColor else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = themeConfig.accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (config.safeBackgroundMode == WidgetBackgroundMode.SYSTEM_CONTRAST) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Синхронизация с акцентом PureClock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Выкл = чистый цвет обоев Android (Monet)", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    PureSwitch(
                                        checked = config.useAppTheme,
                                        onCheckedChange = { config = config.copy(useAppTheme = it) }
                                    )
                                }
                            }

                            if (config.safeBackgroundMode == WidgetBackgroundMode.CUSTOM_COLOR) {
                                Text("Выберите цвет фона:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                ColorSwatchesRow(
                                    selectedHex = config.backgroundColorHex,
                                    onColorSelect = { hex -> config = config.copy(backgroundColorHexNullable = hex) }
                                )
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                            Text("Непрозрачность подложки: ${(config.backgroundAlpha * 100).toInt()}%", fontSize = 13.sp)
                            Slider(
                                value = config.backgroundAlpha,
                                onValueChange = { config = config.copy(backgroundAlpha = it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )

                            Text("Скругление углов карточки: ${config.cornerRadiusDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.cornerRadiusDp.toFloat(),
                                onValueChange = { config = config.copy(cornerRadiusDp = it.toInt()) },
                                valueRange = 0f..64f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )
                        }
                    }
                }

                // 2. СТИЛИ ЧАСОВ И ПОСИМВОЛЬНАЯ ПОКРАСКА КАЖДОЙ ЦИФРЫ
                item {
                    ExpandableSettingBubble(
                        title = "Часы и покраска цифр",
                        subtitle = "${if (config.safeDisplayMode == ClockDisplayMode.ANALOG) config.safeAnalogStyle.title else config.safeDigitalStyle.title} • ${config.safeTimeColoringMode.title}",
                        icon = Icons.Default.Schedule,
                        isExpanded = expandedBubble == "CLOCK",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "CLOCK") null else "CLOCK" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Тип часов", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ClockDisplayMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = config.safeDisplayMode == mode,
                                        onClick = { config = config.copy(displayMode = mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                    ) {
                                        Text(if (mode == ClockDisplayMode.ANALOG) "Аналоговые" else "Цифровые", fontSize = 12.sp)
                                    }
                                }
                            }

                            Text("Стиль циферблата", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                                AnalogStyleType.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.safeAnalogStyle == style,
                                        onClick = { config = config.copy(analogStyle = style) },
                                        label = { Text(style.title) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                DigitalStyleType.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.safeDigitalStyle == style,
                                        onClick = { config = config.copy(digitalStyle = style) },
                                        label = { Text(style.title) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                            // РЕЖИМЫ ПОКРАСКИ ЦИФР
                            Text("Режим раскраски цифр", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            TimeColoringMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = config.safeTimeColoringMode == mode,
                                    onClick = { config = config.copy(timeColoringMode = mode) },
                                    label = { Text(mode.title) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            when (config.safeTimeColoringMode) {
                                TimeColoringMode.SOLID_ALL -> {
                                    Text("Единый цвет всех цифр:", fontSize = 12.sp, color = Color.Gray)
                                    ColorSwatchesRow(
                                        selectedHex = config.timeColorHex,
                                        onColorSelect = { hex -> config = config.copy(timeColorHexNullable = hex) }
                                    )
                                }

                                TimeColoringMode.FIRST_DIGIT_ACCENT,
                                TimeColoringMode.HOURS_ACCENT -> {
                                    Text("Основной цвет цифр:", fontSize = 12.sp, color = Color.Gray)
                                    ColorSwatchesRow(
                                        selectedHex = config.timeColorHex,
                                        onColorSelect = { hex -> config = config.copy(timeColorHexNullable = hex) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Акцентная цифра (Авто от темы)", fontSize = 12.sp, color = Color.Gray)
                                        TextButton(
                                            onClick = { config = config.copy(timeAccentColorHexNullable = null) }
                                        ) {
                                            Text("Сбросить на авто", fontSize = 11.sp, color = themeConfig.accentColor)
                                        }
                                    }
                                    ColorSwatchesRow(
                                        selectedHex = config.timeAccentColorHex ?: "",
                                        onColorSelect = { hex -> config = config.copy(timeAccentColorHexNullable = hex) }
                                    )
                                }

                                TimeColoringMode.PER_DIGIT_CUSTOM -> {
                                    val sampleTime = remember(currentTimeMillis) {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTimeMillis))
                                    }
                                    val labels = listOf("Час 1", "Час 2", "Раздел.", "Мин 1", "Мин 2")
                                    val currentDigitsColors = config.safeDigitColors

                                    Text(
                                        text = "Нажмите на цифру ниже, чтобы покрасить её:",
                                        fontSize = 12.sp,
                                        color = themeConfig.accentColor,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (i in 0 until 5) {
                                            val ch = sampleTime.getOrNull(i) ?: '0'
                                            val hex = currentDigitsColors.getOrElse(i) { "#FFFFFF" }
                                            val digitColor = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.White }
                                            val isSelectedSlot = selectedDigitIndex == i

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 3.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        if (isSelectedSlot) themeConfig.accentColor.copy(alpha = 0.18f)
                                                        else Color.Black.copy(alpha = 0.35f)
                                                    )
                                                    .border(
                                                        width = if (isSelectedSlot) 2.dp else 1.dp,
                                                        color = if (isSelectedSlot) themeConfig.accentColor else Color.White.copy(alpha = 0.1f),
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable { selectedDigitIndex = i }
                                                    .padding(vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = ch.toString(),
                                                    fontSize = 26.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = digitColor
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(digitColor)
                                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = labels[i],
                                                    fontSize = 9.sp,
                                                    color = if (isSelectedSlot) themeConfig.accentColor else Color.Gray
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Цвет для выбранной позиции (${labels[selectedDigitIndex]}):",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    ColorSwatchesRow(
                                        selectedHex = currentDigitsColors.getOrElse(selectedDigitIndex) { "#FFFFFF" },
                                        onColorSelect = { newHex ->
                                            val mutable = currentDigitsColors.toMutableList()
                                            mutable[selectedDigitIndex] = newHex
                                            config = config.copy(digitColorsHex = mutable)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                            Text("Масштаб часов: ${config.timeFontSizeSp} sp", fontSize = 13.sp)
                            Slider(
                                value = config.timeFontSizeSp.toFloat(),
                                onValueChange = { config = config.copy(timeFontSizeSp = it.toInt()) },
                                valueRange = 36f..220f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )
                        }
                    }
                }

                // 3. ТЕНИ ДЛЯ ЧИТАЕМОСТИ НА ОБОЯХ
                item {
                    ExpandableSettingBubble(
                        title = "Тени для читаемости текста",
                        subtitle = if (config.enableTextShadow) {
                            "Включены • Радиус ${config.textShadowRadiusDp.toInt()} dp • ${(config.textShadowIntensity * 100).toInt()}%"
                        } else {
                            "Тени отключены"
                        },
                        icon = Icons.Default.Deblur,
                        isExpanded = expandedBubble == "SHADOW",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.enableTextShadow,
                        onCheckChange = { config = config.copy(enableTextShadow = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "SHADOW") null else "SHADOW" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Специальный контрастный ореол позади цифр и даты предотвращает сливание текста со светлыми и пестрыми обоями.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )

                            Text("Плотность тени: ${(config.textShadowIntensity * 100).toInt()}%", fontSize = 13.sp)
                            Slider(
                                value = config.textShadowIntensity,
                                onValueChange = { config = config.copy(textShadowIntensity = it) },
                                valueRange = 0.1f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )

                            Text("Мягкость (радиус размытия): ${config.textShadowRadiusDp.toInt()} dp", fontSize = 13.sp)
                            Slider(
                                value = config.textShadowRadiusDp,
                                onValueChange = { config = config.copy(textShadowRadiusDp = it) },
                                valueRange = 2f..24f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )

                            Text("Оттенок тени (чёрный или цветной неон):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            ColorSwatchesRow(
                                selectedHex = config.textShadowColorHex,
                                onColorSelect = { hex -> config = config.copy(textShadowColorHexNullable = hex) }
                            )
                        }
                    }
                }

                // 4. БЛОК ДАТЫ (ЕДИНАЯ ПОКРАСКА + СТИЛИ)
                item {
                    ExpandableSettingBubble(
                        title = "Блок даты и стили",
                        subtitle = if (config.showDate) "${config.safeDateStyle.title} • ${config.dateFontSizeSp} sp" else "Дата скрыта",
                        icon = Icons.Default.Event,
                        isExpanded = expandedBubble == "DATE",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showDate,
                        onCheckChange = { config = config.copy(showDate = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "DATE") null else "DATE" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Формат и стиль даты", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            DateStyleType.entries.forEach { style ->
                                FilterChip(
                                    selected = config.safeDateStyle == style,
                                    onClick = { config = config.copy(dateStyle = style) },
                                    label = { Text(style.title) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Красить дату в акцент системы", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Единая автоматическая покраска всего блока даты", fontSize = 11.sp, color = Color.Gray)
                                }
                                PureSwitch(
                                    checked = config.useSystemAccentForDate,
                                    onCheckedChange = { config = config.copy(useSystemAccentForDate = it) }
                                )
                            }

                            if (!config.useSystemAccentForDate) {
                                Text("Выберите единый цвет блока даты:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                ColorSwatchesRow(
                                    selectedHex = config.dateColorHex,
                                    onColorSelect = { hex -> config = config.copy(dateColorHexNullable = hex) }
                                )
                            }

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
                                valueRange = 12f..64f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )
                        }
                    }
                }

                // 5. ПОЗИЦИОНИРОВАНИЕ И ПОРЯДОК СЛОЁВ
                item {
                    ExpandableSettingBubble(
                        title = "Расположение и порядок",
                        subtitle = "Сетка 3×3 и чередование Время / Дата",
                        icon = Icons.Default.GridView,
                        isExpanded = expandedBubble == "POSITION",
                        accentColor = themeConfig.accentColor,
                        onToggle = { expandedBubble = if (expandedBubble == "POSITION") null else "POSITION" }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Выравнивание внутри виджета (3×3)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            JoystickGrid3x3(
                                currentPosition = config.safePosition,
                                accentColor = themeConfig.accentColor,
                                onPositionSelected = { config = config.copy(position = it) }
                            )

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                            val currentOrder = config.safeElementOrder
                            val isTimeFirst = currentOrder.firstOrNull() == WidgetElementType.TIME
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Порядок следования", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isTimeFirst) "Сначала Часы, снизу Дата" else "Сначала Дата, снизу Часы",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        config = config.copy(elementOrder = currentOrder.reversed())
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.bounceClick()
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Поменять", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 6. КОНТУРНАЯ РАМКА
                item {
                    ExpandableSettingBubble(
                        title = "Контурная обводка",
                        subtitle = if (config.showBorder) "${config.safeBorderStyle.title} • ${config.borderWidthDp} dp" else "Без рамки",
                        icon = Icons.Default.CropFree,
                        isExpanded = expandedBubble == "BORDER",
                        accentColor = themeConfig.accentColor,
                        hasSwitch = true,
                        isChecked = config.showBorder,
                        onCheckChange = { config = config.copy(showBorder = it) },
                        onToggle = { expandedBubble = if (expandedBubble == "BORDER") null else "BORDER" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Тип линии", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BorderStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = config.safeBorderStyle == style,
                                        onClick = { config = config.copy(borderStyle = style) },
                                        label = { Text(style.title) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Неоновое свечение контура", fontSize = 13.sp)
                                PureSwitch(
                                    checked = config.enableBorderGlow,
                                    onCheckedChange = { config = config.copy(enableBorderGlow = it) }
                                )
                            }

                            Text("Толщина линии: ${config.borderWidthDp} dp", fontSize = 13.sp)
                            Slider(
                                value = config.borderWidthDp.toFloat(),
                                onValueChange = { config = config.copy(borderWidthDp = it.toInt()) },
                                valueRange = 1f..10f,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeConfig.accentColor,
                                    activeTrackColor = themeConfig.accentColor
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSaveConfig(config) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .bounceClick(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.accentColor,
                    contentColor = SystemThemeUtils.getContrastingColor(themeConfig.accentColor)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Применить к виджету", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ============================================================================
// ПРЕМИАЛЬНЫЙ МАКЕТ ЭКРАНА СМАРТФОНА ДЛЯ ПРЕДПРОСМОТРА ВИДЖЕТА
// ============================================================================
@Composable
private fun PremiumHomescreenPreviewCard(
    bitmap: android.graphics.Bitmap,
    selectedRatio: WidgetPreviewRatio,
    selectedWallpaper: PreviewWallpaperPreset,
    accentColor: Color,
    onSelectRatio: (WidgetPreviewRatio) -> Unit,
    onSelectWallpaper: (PreviewWallpaperPreset) -> Unit
) {
    val animatedRatio by animateFloatAsState(
        targetValue = selectedRatio.ratio,
        animationSpec = spring(stiffness = 260f),
        label = "PreviewAspectRatio"
    )

    val isLightWallpaper = selectedWallpaper == PreviewWallpaperPreset.DAYLIGHT
    val chromeTextColor = if (isLightWallpaper) Color(0xFF1B1E28) else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF101218))
            .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
    ) {
        // ВЕРХНЯЯ СЦЕНА: ЭКРАН СМАРТФОНА С ОБОЯМИ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            // 1. Отрисовка выбранных обоев рабочего стола
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (selectedWallpaper) {
                    PreviewWallpaperPreset.CYBER_NIGHT -> {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF081420),
                                    Color(0xFF102534),
                                    Color(0xFF09131C)
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            )
                        )
                        // Декоративные мягкие неоновые огни ночного города (как на обоях)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(size.width * 0.2f, size.height * 0.8f),
                                radius = size.width * 0.45f
                            ),
                            center = Offset(size.width * 0.2f, size.height * 0.8f),
                            radius = size.width * 0.45f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.25f),
                                radius = size.width * 0.4f
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.25f),
                            radius = size.width * 0.4f
                        )
                    }

                    PreviewWallpaperPreset.DAYLIGHT -> {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFDCEEFB),
                                    Color(0xFFF5F7FA),
                                    Color(0xFFE5DEC9)
                                )
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFF59D).copy(alpha = 0.6f), Color.Transparent),
                                center = Offset(size.width * 0.8f, size.height * 0.2f),
                                radius = size.width * 0.4f
                            ),
                            center = Offset(size.width * 0.8f, size.height * 0.2f),
                            radius = size.width * 0.4f
                        )
                    }

                    PreviewWallpaperPreset.VIBRANT_ART -> {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3A1C71),
                                    Color(0xFFD76D77),
                                    Color(0xFFFFAF7B)
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            )
                        )
                    }

                    PreviewWallpaperPreset.TRANSPARENT_GRID -> {
                        val tileSize = 14.dp.toPx()
                        val cols = (size.width / tileSize).toInt() + 1
                        val rows = (size.height / tileSize).toInt() + 1
                        for (r in 0..rows) {
                            for (c in 0..cols) {
                                val isEven = (r + c) % 2 == 0
                                drawRect(
                                    color = if (isEven) Color(0xFF1A1D24) else Color(0xFF242832),
                                    topLeft = Offset(c * tileSize, r * tileSize),
                                    size = Size(tileSize, tileSize)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Имитация статус-бара смартфона сверху
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pure Launcher",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = chromeTextColor.copy(alpha = 0.6f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = chromeTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = chromeTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // 3. Сам виджет в центре рабочего стола с аккуратной пунктирной/тонкой границей слота
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 12.dp, bottom = 6.dp, start = 16.dp, end = 16.dp)
                    .fillMaxHeight(0.82f)
                    .aspectRatio(animatedRatio.coerceIn(1f, 2.5f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Live Widget Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // НИЖНЯЯ ПАНЕЛЬ ИНСТРУМЕНТОВ ПРЕВЬЮ: ОБОИ И СЕТКА ЯЧЕЕК
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151821))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Переключатель фона обоев
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                PreviewWallpaperPreset.entries.forEach { wp ->
                    val isSelected = selectedWallpaper == wp
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelectWallpaper(wp) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = wp.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.Gray
                        )
                    }
                }
            }

            // Переключатель пропорций виджета (5x2, 4x2, 3x3, 2x2)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                WidgetPreviewRatio.entries.forEach { ratio ->
                    val isSelected = selectedRatio == ratio
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor else Color.Transparent)
                            .clickable { onSelectRatio(ratio) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = ratio.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) SystemThemeUtils.getContrastingColor(accentColor) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// ПАЛИТРА БЫСТРОГО ВЫБОРА ЦВЕТА
// ============================================================================
@Composable
private fun ColorSwatchesRow(
    selectedHex: String,
    onColorSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        items(WidgetColorPalette) { (hex, name) ->
            val color = try { Color(hex.toColorInt()) } catch (_: Exception) { Color.White }
            val isSelected = selectedHex.equals(hex, ignoreCase = true)

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelect(hex) }
                    .bounceClick(),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = name,
                        tint = SystemThemeUtils.getContrastingColor(color),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
    val themeConfig = LocalPureClockConfig.current
    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pure3DEffect(
                shape = cardShape,
                accentColor = accentColor,
                depthDp = themeConfig.depthIntensityDp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .padding(16.dp)
    ) {
        Column {
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(21.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, fontSize = 11.sp, color = Color.Gray)
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
                    Spacer(modifier = Modifier.height(14.dp))
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
            .width(174.dp)
            .height(174.dp)
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
                            .size(46.dp)
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