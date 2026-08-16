package com.necromagik.pureclock.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
enum class ClockPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Keep
enum class ClockDisplayMode {
    DIGITAL,
    ANALOG
}

@Keep
enum class DigitalStyleType(val title: String) {
    OXYGEN_BOLD_FLUID("Oxygen One-Red"),
    STACK_TWO_LINE("Stack Two-Line"),
    NOTHING_DOT_MATRIX("Dot Matrix / Nothing"),
    CYBER_CONSOLE("Terminal Console"),
    TYPO_ELEGANT_SLIM("Ultra Slim Minimal")
}

@Keep
enum class AnalogStyleType(val title: String) {
    OXYGEN_NEVER_SETTLE("Oxygen Classic (Красная 12)"),
    BAUHAUS_MINIMAL("Bauhaus Minimal"),
    CHRONO_SPORT("Chrono Sport (Штрихи 60м)"),
    ZEN_SPACE_DOTS("Zen Dots (Точечные маркеры)"),
    PILOT_AVIA("Aviation Flieger")
}

@Keep
enum class BorderStyle {
    SOLID,
    DASHED,
    DOUBLE_LINE
}

@Keep
enum class WidgetElementType {
    TIME,
    DATE
}

@Keep
data class WidgetConfig(
    val id: Int = 0,
    val useAppTheme: Boolean = true,
    val position: ClockPosition = ClockPosition.CENTER,

    // Стек элементов (только время и дата)
    val elementOrder: List<WidgetElementType> = listOf(
        WidgetElementType.TIME,
        WidgetElementType.DATE
    ),

    // 1. Часы
    val displayMode: ClockDisplayMode = ClockDisplayMode.DIGITAL,
    val digitalStyle: DigitalStyleType = DigitalStyleType.OXYGEN_BOLD_FLUID,
    val analogStyle: AnalogStyleType = AnalogStyleType.OXYGEN_NEVER_SETTLE,
    val timeFontSizeSp: Int = 76,
    @SerializedName("timeColorHex") val timeColorHexNullable: String? = "#FFFFFF",

    // 2. Дата
    val showDate: Boolean = true,
    val isDateBold: Boolean = true,
    val dateFontSizeSp: Int = 22,
    @SerializedName("dateColorHex") val dateColorHexNullable: String? = null,

    // 3. Подложка и фон
    val showBackground: Boolean = true,
    val backgroundAlpha: Float = 0.85f,
    @SerializedName("backgroundColorHex") val backgroundColorHexNullable: String? = "#0B0B0B",

    // 4. Контурная рамка
    val showBorder: Boolean = true,
    val borderStyle: BorderStyle = BorderStyle.SOLID,
    val borderWidthDp: Int = 3,
    val cornerRadiusDp: Int = 24,
    val enableBorderGlow: Boolean = true,
    @SerializedName("borderColorHex") val borderColorHexNullable: String? = null
) {
    val timeColorHex: String get() = timeColorHexNullable ?: "#FFFFFF"
    val dateColorHex: String get() = dateColorHexNullable ?: "#EB0029"
    val backgroundColorHex: String get() = backgroundColorHexNullable ?: "#0B0B0B"
    val borderColorHex: String get() = borderColorHexNullable ?: "#EB0029"

    val safePosition: ClockPosition get() = position
    val safeDisplayMode: ClockDisplayMode get() = displayMode
    val safeDigitalStyle: DigitalStyleType get() = digitalStyle
    val safeAnalogStyle: AnalogStyleType get() = analogStyle
    val safeElementOrder: List<WidgetElementType>
        get() = elementOrder.ifEmpty { listOf(WidgetElementType.TIME, WidgetElementType.DATE) }
}