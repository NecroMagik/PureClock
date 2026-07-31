package com.necromagik.pureclock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.necromagik.pureclock.ui.theme.AppThemeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AnalogStyle(val title: String, val description: String) {
    OXYGEN("OxygenOS", "NEVER SETTLE"),
    CLASSIC_ARABIC("Классика", "Классические цифры и риски"),
    CLASSIC_ROMAN("Римский стиль", "Italia"),
    CHRONO("Chrono Sport", "Спортивная шкала и секундный противовес"),
    MINIMAL("Bauhaus", "Точечные индексы и мягкая геометрия"),
    ULTRA_MINIMAL("Zen Space", "Максимальный минимализм")
}

enum class DigitalStyle(val title: String, val description: String) {
    OXYGEN_LARGE("Bold Fluid", "Крупные динамические цифры"),
    VERTICAL("Stack OS", "Двухэтажный формат: часы над минутами"),
    SECTIONAL("3D LED Segment", "Объемные полигональные физические сегменты"),
    CYBER_MONO("Matrix Console", "Моноширинный киберпанк с рамкой")
}

data class ThemeState(
    val themeMode: String = "SYSTEM",
    val isPureMonocolor: Boolean = true,
    val accentColorHex: String = "#00E676",
    val cardCornerRadiusDp: Int = 24,
    val is3DEffectsEnabled: Boolean = true,
    val isNeonGlowEnabled: Boolean = true,
    val depthIntensityDp: Int = 8
)

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANALOG_STYLE = "analog_clock_style"
        private const val KEY_DIGITAL_STYLE = "digital_clock_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PURE_MONOCOLOR = "is_pure_monocolor"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val KEY_SAVED_CITY_IDS = "saved_city_ids"

        private const val KEY_3D_EFFECTS = "is_3d_effects_enabled"
        private const val KEY_NEON_GLOW = "is_neon_glow_enabled"
        private const val KEY_DEPTH_INTENSITY = "depth_intensity_dp"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _themeState = MutableStateFlow(
        ThemeState(
            themeMode = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM",
            isPureMonocolor = prefs.getBoolean(KEY_PURE_MONOCOLOR, true),
            accentColorHex = prefs.getString(KEY_ACCENT_COLOR, "#00E676") ?: "#00E676",
            cardCornerRadiusDp = prefs.getInt(KEY_CARD_CORNER_RADIUS, 24),
            is3DEffectsEnabled = prefs.getBoolean(KEY_3D_EFFECTS, true),
            isNeonGlowEnabled = prefs.getBoolean(KEY_NEON_GLOW, true),
            depthIntensityDp = prefs.getInt(KEY_DEPTH_INTENSITY, 8)
        )
    )
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    // --- СТИЛИ ЧАСОВ ---
    var selectedAnalogStyle: AnalogStyle
        get() {
            val name = prefs.getString(KEY_ANALOG_STYLE, AnalogStyle.OXYGEN.name)
            return try { AnalogStyle.valueOf(name!!) } catch (_: Exception) { AnalogStyle.OXYGEN }
        }
        set(value) = prefs.edit().putString(KEY_ANALOG_STYLE, value.name).apply()

    var selectedDigitalStyle: DigitalStyle
        get() {
            val name = prefs.getString(KEY_DIGITAL_STYLE, DigitalStyle.OXYGEN_LARGE.name)
            return try { DigitalStyle.valueOf(name!!) } catch (_: Exception) { DigitalStyle.OXYGEN_LARGE }
        }
        set(value) = prefs.edit().putString(KEY_DIGITAL_STYLE, value.name).apply()

    var savedCityIds: Set<String>
        get() = prefs.getStringSet(KEY_SAVED_CITY_IDS, setOf("UTC", "Europe/Moscow")) ?: setOf("UTC", "Europe/Moscow")
        set(value) = prefs.edit().putStringSet(KEY_SAVED_CITY_IDS, value).apply()

    // --- ОПТИМИЗИРОВАННЫЕ НАСТРОЙКИ (Асинхронная запись через apply) ---
    var is24HourFormat: Boolean
        get() = prefs.getBoolean("world_clock_24h", true)
        set(value) { prefs.edit().putBoolean("world_clock_24h", value).apply() }

    var isClockHapticsEnabled: Boolean
        get() = prefs.getBoolean("world_clock_haptics", true)
        set(value) { prefs.edit().putBoolean("world_clock_haptics", value).apply() }

    var isDigitalClockMode: Boolean
        get() = prefs.getBoolean("world_clock_is_digital", false)
        set(value) { prefs.edit().putBoolean("world_clock_is_digital", value).apply() }

    var isVolumeRampEnabled: Boolean
        get() = prefs.getBoolean("alarm_volume_ramp", true)
        set(value) { prefs.edit().putBoolean("alarm_volume_ramp", value).apply() }

    var upcomingNotificationMinutes: Int
        get() = prefs.getInt("alarm_upcoming_notice_min", 30)
        set(value) { prefs.edit().putInt("alarm_upcoming_notice_min", value).apply() }

    var defaultSnoozeTimeMinutes: Int
        get() = prefs.getInt("alarm_default_snooze_min", 10)
        set(value) { prefs.edit().putInt("alarm_default_snooze_min", value).apply() }

    var autoDismissMinutes: Int
        get() = prefs.getInt("alarm_auto_dismiss_min", 15)
        set(value) { prefs.edit().putInt("alarm_auto_dismiss_min", value).apply() }

    var dismissMethod: String
        get() = prefs.getString("alarm_dismiss_method", "SWIPE") ?: "SWIPE"
        set(value) { prefs.edit().putString("alarm_dismiss_method", value).apply() }

    var isTimerVibrate: Boolean
        get() = prefs.getBoolean("timer_vibrate", true)
        set(value) { prefs.edit().putBoolean("timer_vibrate", value).apply() }

    var isStopwatchLapVibrate: Boolean
        get() = prefs.getBoolean("stopwatch_lap_vibrate", true)
        set(value) { prefs.edit().putBoolean("stopwatch_lap_vibrate", value).apply() }

    // --- ТЕМАТИЗАЦИЯ ВЫСОКОЙ СКОРОСТИ ---
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value).apply()
            _themeState.value = _themeState.value.copy(themeMode = value)
        }

    var appThemeStyle: AppThemeStyle
        get() {
            val name = prefs.getString("APP_THEME_STYLE", AppThemeStyle.SYSTEM_MONET.name)
            return try { AppThemeStyle.valueOf(name!!) } catch (_: Exception) { AppThemeStyle.SYSTEM_MONET }
        }
        set(value) = prefs.edit().putString("APP_THEME_STYLE", value.name).apply()

    var isPureMonocolor: Boolean
        get() = prefs.getBoolean(KEY_PURE_MONOCOLOR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PURE_MONOCOLOR, value).apply()
            _themeState.value = _themeState.value.copy(isPureMonocolor = value)
        }

    var accentColorHex: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "#00E676") ?: "#00E676"
        set(value) {
            prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()
            _themeState.value = _themeState.value.copy(accentColorHex = value)
        }

    var cardCornerRadiusDp: Int
        get() = prefs.getInt(KEY_CARD_CORNER_RADIUS, 24)
        set(value) {
            prefs.edit().putInt(KEY_CARD_CORNER_RADIUS, value).apply()
            _themeState.value = _themeState.value.copy(cardCornerRadiusDp = value)
        }

    var is3DEffectsEnabled: Boolean
        get() = prefs.getBoolean(KEY_3D_EFFECTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_3D_EFFECTS, value).apply()
            _themeState.value = _themeState.value.copy(is3DEffectsEnabled = value)
        }

    var isNeonGlowEnabled: Boolean
        get() = prefs.getBoolean(KEY_NEON_GLOW, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NEON_GLOW, value).apply()
            _themeState.value = _themeState.value.copy(isNeonGlowEnabled = value)
        }

    var depthIntensityDp: Int
        get() = prefs.getInt(KEY_DEPTH_INTENSITY, 8)
        set(value) {
            prefs.edit().putInt(KEY_DEPTH_INTENSITY, value).apply()
            _themeState.value = _themeState.value.copy(depthIntensityDp = value)
        }

    val accentColor: Color
        get() = getAccentColor(accentColorHex)

    fun getAccentColor(hex: String = accentColorHex): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF00E676)
        }
    }
}