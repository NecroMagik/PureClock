package com.necromagik.pureclock.data.model

import com.google.gson.annotations.SerializedName

enum class ClockPosition {
    TOP_LEFT,    TOP_CENTER,    TOP_RIGHT,
    CENTER_LEFT, CENTER,        CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

enum class AnalogStyleType {
    OXYGEN_NEVER_SETTLE,
    CLASSIC_INDEXES,
    CHRONO_SPORT,
    BAUHAUS_MINIMAL,
    ZEN_SPACE_DOTS
}

enum class DigitalStyleType {
    OXYGEN_BOLD_FLUID,
    STACK_TWO_LINE,
    LED_3D_SEGMENT,
    CYBER_CONSOLE,
    TYPO_LARGE_MINIMAL
}

enum class ClockDisplayMode { ANALOG, DIGITAL }

data class WidgetConfig(
    val id: Int,
    val useAppTheme: Boolean = true,
    val position: ClockPosition = ClockPosition.CENTER,

    // 1. Часы
    val displayMode: ClockDisplayMode = ClockDisplayMode.DIGITAL,
    val analogStyle: AnalogStyleType = AnalogStyleType.OXYGEN_NEVER_SETTLE,
    val digitalStyle: DigitalStyleType = DigitalStyleType.OXYGEN_BOLD_FLUID,
    val timeFontSizeSp: Int = 90,
    @SerializedName("timeColorHex") val timeColorHexNullable: String? = "#FFFFFF",

    // 2. Дата
    val showDate: Boolean = true,
    val dateFontSizeSp: Int = 26,
    @SerializedName("dateColorHex") val dateColorHexNullable: String? = "#EB0029",
    val isDateBold: Boolean = true,

    // 3. Погода
    val showWeather: Boolean = true,
    val weatherFontSizeSp: Int = 22,
    @SerializedName("weatherColorHex") val weatherColorHexNullable: String? = "#CCCCCC",

    // 4. Фон
    val showBackground: Boolean = true,
    @SerializedName("backgroundColorHex") val backgroundColorHexNullable: String? = "#0B0B0B",
    val backgroundAlpha: Float = 0.85f,

    // 5. Независимая граница
    val showBorder: Boolean = true,
    @SerializedName("borderColorHex") val borderColorHexNullable: String? = "#EB0029",
    val borderWidthDp: Int = 3,
    val cornerRadiusDp: Int = 24
) {
    val timeColorHex: String get() = timeColorHexNullable ?: "#FFFFFF"
    val dateColorHex: String get() = dateColorHexNullable ?: "#EB0029"
    val weatherColorHex: String get() = weatherColorHexNullable ?: "#CCCCCC"
    val backgroundColorHex: String get() = backgroundColorHexNullable ?: "#0B0B0B"
    val borderColorHex: String get() = borderColorHexNullable ?: "#EB0029"

    val safeDigitalStyle: DigitalStyleType get() = digitalStyle ?: DigitalStyleType.OXYGEN_BOLD_FLUID
    val safeAnalogStyle: AnalogStyleType get() = analogStyle ?: AnalogStyleType.OXYGEN_NEVER_SETTLE
    val safeDisplayMode: ClockDisplayMode get() = displayMode ?: ClockDisplayMode.DIGITAL
    val safePosition: ClockPosition get() = position ?: ClockPosition.CENTER
}