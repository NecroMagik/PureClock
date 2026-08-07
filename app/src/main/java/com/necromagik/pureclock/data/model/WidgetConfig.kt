package com.necromagik.pureclock.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
enum class ClockPosition {
    TOP_LEFT,    TOP_CENTER,    TOP_RIGHT,
    CENTER_LEFT, CENTER,        CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Keep
enum class AnalogStyleType {
    OXYGEN_NEVER_SETTLE,
    CLASSIC_INDEXES,
    CHRONO_SPORT,
    BAUHAUS_MINIMAL,
    ZEN_SPACE_DOTS
}

@Keep
enum class DigitalStyleType {
    OXYGEN_BOLD_FLUID,
    STACK_TWO_LINE,
    LED_3D_SEGMENT,
    CYBER_CONSOLE,
    TYPO_LARGE_MINIMAL
}

@Keep
enum class ClockDisplayMode { ANALOG, DIGITAL }

@Keep
enum class WidgetElementType {
    TIME,
    DATE,
    WEATHER
}

@Keep
enum class BorderStyle {
    SOLID,
    DASHED,
    DOUBLE_LINE
}

@Keep
data class WidgetConfig(
    val id: Int,
    val useAppTheme: Boolean = true,
    val position: ClockPosition = ClockPosition.CENTER,

    // Порядок элементов
    val elementOrder: List<WidgetElementType> = listOf(
        WidgetElementType.TIME,
        WidgetElementType.DATE,
        WidgetElementType.WEATHER
    ),

    // 1. Часы
    val displayMode: ClockDisplayMode = ClockDisplayMode.DIGITAL,
    val analogStyle: AnalogStyleType = AnalogStyleType.OXYGEN_NEVER_SETTLE,
    val digitalStyle: DigitalStyleType = DigitalStyleType.OXYGEN_BOLD_FLUID,
    val timeFontSizeSp: Int = 80,
    @SerializedName("timeColorHex") val timeColorHexNullable: String? = "#FFFFFF",

    // 2. Дата
    val showDate: Boolean = true,
    val dateFontSizeSp: Int = 24,
    @SerializedName("dateColorHex") val dateColorHexNullable: String? = null,
    val isDateBold: Boolean = true,

    // 3. Погода
    val showWeather: Boolean = true,
    val weatherFontSizeSp: Int = 20,
    @SerializedName("weatherColorHex") val weatherColorHexNullable: String? = "#CCCCCC",

    // 4. Фон
    val showBackground: Boolean = true,
    @SerializedName("backgroundColorHex") val backgroundColorHexNullable: String? = "#0B0B0B",
    val backgroundAlpha: Float = 0.85f,

    // 5. Контурная рамка
    val showBorder: Boolean = true,
    val borderStyle: BorderStyle = BorderStyle.SOLID,
    @SerializedName("borderColorHex") val borderColorHexNullable: String? = null,
    val borderWidthDp: Int = 3,
    val cornerRadiusDp: Int = 20,
    val enableBorderGlow: Boolean = true
) {
    val timeColorHex: String get() = timeColorHexNullable ?: "#FFFFFF"
    val dateColorHex: String get() = dateColorHexNullable ?: "#EB0029"
    val weatherColorHex: String get() = weatherColorHexNullable ?: "#CCCCCC"
    val backgroundColorHex: String get() = backgroundColorHexNullable ?: "#0B0B0B"
    val borderColorHex: String get() = borderColorHexNullable ?: "#EB0029"

    fun getDateColorOrDefault(defaultAccentHex: String): String = dateColorHexNullable ?: defaultAccentHex
    fun getBorderColorOrDefault(defaultAccentHex: String): String = borderColorHexNullable ?: defaultAccentHex

    val safeDigitalStyle: DigitalStyleType get() = digitalStyle
    val safeAnalogStyle: AnalogStyleType get() = analogStyle
    val safeDisplayMode: ClockDisplayMode get() = displayMode
    val safePosition: ClockPosition get() = position
    val safeElementOrder: List<WidgetElementType>
        get() = elementOrder.ifEmpty { listOf(WidgetElementType.TIME, WidgetElementType.DATE, WidgetElementType.WEATHER) }
}