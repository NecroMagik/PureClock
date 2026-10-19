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
    OXYGEN_BOLD_FLUID("Bold Fluid (Крупный)"),
    STACK_TWO_LINE("Stack Two-Line (В два этажа)"),
    NOTHING_DOT_MATRIX("Dot Matrix (Точечный)"),
    CYBER_CONSOLE("Terminal Console (> 12:00_)"),
    TYPO_ELEGANT_SLIM("Ultra Slim (Тонкий минимализм)"),
    CLASSIC_ROUNDED("Soft Rounded (Мягкий)")
}

@Keep
enum class AnalogStyleType(val title: String) {
    OXYGEN_NEVER_SETTLE("Oxygen Classic"),
    BAUHAUS_MINIMAL("Bauhaus Minimal"),
    CHRONO_SPORT("Chrono Sport (Штрихи 60м)"),
    ZEN_SPACE_DOTS("Zen Dots (Точечные маркеры)"),
    PILOT_AVIA("Aviation Flieger")
}

@Keep
enum class BorderStyle(val title: String) {
    SOLID("Сплошная"),
    DASHED("Пунктир"),
    DOUBLE_LINE("Двойная")
}

@Keep
enum class WidgetElementType {
    TIME,
    DATE
}

/**
 * Режим окраски фоновой подложки виджета
 */
@Keep
enum class WidgetBackgroundMode(val title: String, val subtitle: String) {
    SYSTEM_CONTRAST("Контраст с системой", "Глубокий оттенок под цвет акцента системы"),
    APP_THEME("Тема приложения", "Синхронизация с настройками PureClock"),
    CUSTOM_COLOR("Свой цвет", "Ручной выбор любого оттенка подложки"),
    PURE_BLACK("AMOLED Чёрный", "Абсолютно чёрный #000000 фон")
}

/**
 * Режим раскраски цифр времени
 */
@Keep
enum class TimeColoringMode(val title: String) {
    FIRST_DIGIT_ACCENT("Акцент 1-й цифры (Oxygen)"),
    SOLID_ALL("Единый цвет всех цифр"),
    HOURS_ACCENT("Часы — акцент, минуты — базовый"),
    PER_DIGIT_CUSTOM("Свой цвет каждой цифры")
}

/**
 * Стили форматирования блока даты
 */
@Keep
enum class DateStyleType(val title: String, val pattern: String, val uppercase: Boolean = false) {
    FULL_RU("Среда, 7 октября", "EEEE, d MMMM", false),
    SHORT_CAPS("СР • 07 ОКТ", "EEE • dd MMM", true),
    COMPACT_SLASH("07 / 10 • СРЕДА", "dd / MM • EEEE", true),
    MINIMAL_DOTS("07.10.2026", "dd.MM.yyyy", false),
    DAY_AND_NUMBER("Среда, 07", "EEEE, dd", false),
    PILL_BADGE("Капсула (Среда • 7 окт)", "EEEE • d MMM", false)
}

@Keep
data class WidgetConfig(
    val id: Int = 0,
    val useAppTheme: Boolean = true,
    val position: ClockPosition? = ClockPosition.CENTER,

    // Стек элементов (время и дата)
    val elementOrder: List<WidgetElementType>? = listOf(
        WidgetElementType.TIME,
        WidgetElementType.DATE
    ),

    // 1. Часы и режимы покраски цифр
    val displayMode: ClockDisplayMode? = ClockDisplayMode.DIGITAL,
    val digitalStyle: DigitalStyleType? = DigitalStyleType.OXYGEN_BOLD_FLUID,
    val analogStyle: AnalogStyleType? = AnalogStyleType.OXYGEN_NEVER_SETTLE,
    val timeFontSizeSp: Int = 76,
    val timeColoringMode: TimeColoringMode? = TimeColoringMode.FIRST_DIGIT_ACCENT,
    @SerializedName("timeColorHex") val timeColorHexNullable: String? = "#FFFFFF",
    @SerializedName("timeAccentColorHex") val timeAccentColorHexNullable: String? = null,

    // Индивидуальные цвета для 5 позиций времени: [Ч1, Ч2, Двоеточие, М1, М2]
    val digitColorsHex: List<String>? = listOf("#00E676", "#FFFFFF", "#80FFFFFF", "#FFFFFF", "#FFFFFF"),

    // 2. Дата (единый блок для покраски + стили)
    val showDate: Boolean = true,
    val isDateBold: Boolean = true,
    val dateFontSizeSp: Int = 22,
    val dateStyle: DateStyleType? = DateStyleType.FULL_RU,
    val useSystemAccentForDate: Boolean = true,
    @SerializedName("dateColorHex") val dateColorHexNullable: String? = null,

    // 3. Подложка и фон (с поддержкой системного контраста Monet)
    val showBackground: Boolean = true,
    val backgroundMode: WidgetBackgroundMode? = WidgetBackgroundMode.SYSTEM_CONTRAST,
    val backgroundAlpha: Float = 0.90f,
    @SerializedName("backgroundColorHex") val backgroundColorHexNullable: String? = "#0B0B0B",

    // 4. Специальные тени для читаемости текста поверх любых обоев
    val enableTextShadow: Boolean = true,
    val textShadowRadiusDp: Float = 8f,
    val textShadowIntensity: Float = 0.75f,
    @SerializedName("textShadowColorHex") val textShadowColorHexNullable: String? = "#000000",

    // 5. Контурная рамка
    val showBorder: Boolean = false,
    val borderStyle: BorderStyle? = BorderStyle.SOLID,
    val borderWidthDp: Int = 2,
    val cornerRadiusDp: Int = 28,
    val enableBorderGlow: Boolean = false,
    @SerializedName("borderColorHex") val borderColorHexNullable: String? = null
) {
    val timeColorHex: String get() = timeColorHexNullable ?: "#FFFFFF"
    val timeAccentColorHex: String? get() = timeAccentColorHexNullable
    val dateColorHex: String get() = dateColorHexNullable ?: "#00E676"
    val backgroundColorHex: String get() = backgroundColorHexNullable ?: "#0B0B0B"
    val borderColorHex: String get() = borderColorHexNullable ?: "#00E676"
    val textShadowColorHex: String get() = textShadowColorHexNullable ?: "#000000"

    // Безопасные геттеры (защита от null после чтения старых JSON через Gson)
    val safePosition: ClockPosition get() = position ?: ClockPosition.CENTER
    val safeDisplayMode: ClockDisplayMode get() = displayMode ?: ClockDisplayMode.DIGITAL
    val safeDigitalStyle: DigitalStyleType get() = digitalStyle ?: DigitalStyleType.OXYGEN_BOLD_FLUID
    val safeAnalogStyle: AnalogStyleType get() = analogStyle ?: AnalogStyleType.OXYGEN_NEVER_SETTLE
    val safeBorderStyle: BorderStyle get() = borderStyle ?: BorderStyle.SOLID
    val safeBackgroundMode: WidgetBackgroundMode get() = backgroundMode ?: WidgetBackgroundMode.SYSTEM_CONTRAST
    val safeTimeColoringMode: TimeColoringMode get() = timeColoringMode ?: TimeColoringMode.FIRST_DIGIT_ACCENT
    val safeDateStyle: DateStyleType get() = dateStyle ?: DateStyleType.FULL_RU

    val safeElementOrder: List<WidgetElementType>
        get() = elementOrder?.filterNotNull()?.ifEmpty {
            listOf(WidgetElementType.TIME, WidgetElementType.DATE)
        } ?: listOf(WidgetElementType.TIME, WidgetElementType.DATE)

    val safeDigitColors: List<String>
        get() {
            val base = digitColorsHex?.filterNotNull() ?: emptyList()
            if (base.size >= 5) return base.take(5)
            val defaults = listOf("#00E676", "#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFFFFF")
            return List(5) { index -> base.getOrNull(index) ?: defaults[index] }
        }
}