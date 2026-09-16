package com.necromagik.pureclock.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.necromagik.pureclock.ui.theme.AppThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

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
    val cardCornerRadiusDp: Int = 20,
    val is3DEffectsEnabled: Boolean = true,
    val isNeonGlowEnabled: Boolean = true,
    val depthIntensityDp: Int = 8
)

class SettingsManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANALOG_STYLE = "analog_clock_style"
        private const val KEY_DIGITAL_STYLE = "digital_clock_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PURE_MONOCOLOR = "is_pure_monocolor"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val KEY_SAVED_CITY_IDS = "saved_city_ids"
        private const val KEY_ALLOW_BETA_UPDATES = "allow_beta_updates"

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
            cardCornerRadiusDp = prefs.getInt(KEY_CARD_CORNER_RADIUS, 20),
            is3DEffectsEnabled = prefs.getBoolean(KEY_3D_EFFECTS, true),
            isNeonGlowEnabled = prefs.getBoolean(KEY_NEON_GLOW, true),
            depthIntensityDp = prefs.getInt(KEY_DEPTH_INTENSITY, 8)
        )
    )
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    private val _isVolumeRampEnabled = MutableStateFlow(prefs.getBoolean("alarm_volume_ramp", true))
    val isVolumeRampEnabledFlow: StateFlow<Boolean> = _isVolumeRampEnabled.asStateFlow()

    private val _defaultSnoozeTimeMinutes = MutableStateFlow(prefs.getInt("alarm_default_snooze_min", 10))
    val defaultSnoozeTimeMinutesFlow: StateFlow<Int> = _defaultSnoozeTimeMinutes.asStateFlow()

    private val _autoDismissMinutes = MutableStateFlow(prefs.getInt("alarm_auto_dismiss_min", 15))
    val autoDismissMinutesFlow: StateFlow<Int> = _autoDismissMinutes.asStateFlow()

    private val _upcomingNotificationMinutes = MutableStateFlow(prefs.getInt("alarm_upcoming_notice_min", 30))
    val upcomingNotificationMinutesFlow: StateFlow<Int> = _upcomingNotificationMinutes.asStateFlow()

    private val _dismissMethod = MutableStateFlow(prefs.getString("alarm_dismiss_method", "SWIPE") ?: "SWIPE")
    val dismissMethodFlow: StateFlow<String> = _dismissMethod.asStateFlow()

    private val _is24HourFormat = MutableStateFlow(prefs.getBoolean("world_clock_24h", true))
    val is24HourFormatFlow: StateFlow<Boolean> = _is24HourFormat.asStateFlow()

    private val _isClockHapticsEnabled = MutableStateFlow(prefs.getBoolean("world_clock_haptics", true))
    val isClockHapticsEnabledFlow: StateFlow<Boolean> = _isClockHapticsEnabled.asStateFlow()

    private val _isTimerVibrate = MutableStateFlow(prefs.getBoolean("timer_vibrate", true))
    val isTimerVibrateFlow: StateFlow<Boolean> = _isTimerVibrate.asStateFlow()

    private val _isStopwatchLapVibrate = MutableStateFlow(prefs.getBoolean("stopwatch_lap_vibrate", true))
    val isStopwatchLapVibrateFlow: StateFlow<Boolean> = _isStopwatchLapVibrate.asStateFlow()

    private val _allowBetaUpdates = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_BETA_UPDATES, false))
    val allowBetaUpdatesFlow: StateFlow<Boolean> = _allowBetaUpdates.asStateFlow()

    private val _isBetaUpdateAvailable = MutableStateFlow(false)
    val isBetaUpdateAvailableFlow: StateFlow<Boolean> = _isBetaUpdateAvailable.asStateFlow()

    private val _latestBetaRelease = MutableStateFlow<UpdateChecker.ReleaseInfo?>(null)
    val latestBetaReleaseFlow: StateFlow<UpdateChecker.ReleaseInfo?> = _latestBetaRelease.asStateFlow()

    // Приватные поля исключают коллизию сигнатур в байт-коде
    private val _googlePlayUpdateManager by lazy {
        GooglePlayUpdateManager(appContext)
    }

    private val _ruStoreUpdateManager by lazy {
        RuStoreUpdateManager(appContext)
    }

    fun getGooglePlayUpdateManager(): GooglePlayUpdateManager = _googlePlayUpdateManager
    fun getRuStoreUpdateManager(): RuStoreUpdateManager = _ruStoreUpdateManager

    suspend fun checkGooglePlayInAppUpdates(): AppUpdateInfo? {
        val source = getInstallationSource()
        if (source != InstallSource.GOOGLE_PLAY && source != InstallSource.SYSTEM_PREBUILT) return null
        return _googlePlayUpdateManager.checkUpdateAvailability()
    }

    suspend fun checkRuStoreInAppUpdates(): RuStoreUpdateManager.RuStoreAppInfo? {
        if (getInstallationSource() != InstallSource.RUSTORE) return null
        return _ruStoreUpdateManager.checkUpdateAvailability()
    }

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

    var is24HourFormat: Boolean
        get() = _is24HourFormat.value
        set(value) {
            _is24HourFormat.value = value
            prefs.edit().putBoolean("world_clock_24h", value).apply()
        }

    var isClockHapticsEnabled: Boolean
        get() = _isClockHapticsEnabled.value
        set(value) {
            _isClockHapticsEnabled.value = value
            prefs.edit().putBoolean("world_clock_haptics", value).apply()
        }

    var isDigitalClockMode: Boolean
        get() = prefs.getBoolean("world_clock_is_digital", false)
        set(value) { prefs.edit().putBoolean("world_clock_is_digital", value).apply() }

    var isVolumeRampEnabled: Boolean
        get() = _isVolumeRampEnabled.value
        set(value) {
            _isVolumeRampEnabled.value = value
            prefs.edit().putBoolean("alarm_volume_ramp", value).apply()
        }

    var upcomingNotificationMinutes: Int
        get() = _upcomingNotificationMinutes.value
        set(value) {
            _upcomingNotificationMinutes.value = value
            prefs.edit().putInt("alarm_upcoming_notice_min", value).apply()
        }

    var defaultSnoozeTimeMinutes: Int
        get() = _defaultSnoozeTimeMinutes.value
        set(value) {
            _defaultSnoozeTimeMinutes.value = value
            prefs.edit().putInt("alarm_default_snooze_min", value).apply()
        }

    var autoDismissMinutes: Int
        get() = _autoDismissMinutes.value
        set(value) {
            _autoDismissMinutes.value = value
            prefs.edit().putInt("alarm_auto_dismiss_min", value).apply()
        }

    var dismissMethod: String
        get() = _dismissMethod.value
        set(value) {
            _dismissMethod.value = value
            prefs.edit().putString("alarm_dismiss_method", value).apply()
        }

    var isTimerVibrate: Boolean
        get() = _isTimerVibrate.value
        set(value) {
            _isTimerVibrate.value = value
            prefs.edit().putBoolean("timer_vibrate", value).apply()
        }

    var isStopwatchLapVibrate: Boolean
        get() = _isStopwatchLapVibrate.value
        set(value) {
            _isStopwatchLapVibrate.value = value
            prefs.edit().putBoolean("stopwatch_lap_vibrate", value).apply()
        }

    var allowBetaUpdates: Boolean
        get() = _allowBetaUpdates.value
        set(value) {
            _allowBetaUpdates.value = value
            prefs.edit().putBoolean(KEY_ALLOW_BETA_UPDATES, value).apply()
        }

    fun setBetaUpdateInfo(release: UpdateChecker.ReleaseInfo?) {
        _latestBetaRelease.value = release
        _isBetaUpdateAvailable.value = release != null
    }

    fun getInstallationSource(): InstallSource {
        return try {
            val packageName = appContext.packageName
            val appInfo = appContext.applicationInfo

            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val (installingPkg, initiatingPkg) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sourceInfo = appContext.packageManager.getInstallSourceInfo(packageName)
                Pair(sourceInfo.installingPackageName, sourceInfo.initiatingPackageName)
            } else {
                @Suppress("DEPRECATION")
                val installer = appContext.packageManager.getInstallerPackageName(packageName)
                Pair(installer, installer)
            }

            fun matches(vararg targets: String): Boolean {
                return targets.any { target ->
                    installingPkg?.contains(target, ignoreCase = true) == true ||
                            initiatingPkg?.contains(target, ignoreCase = true) == true
                }
            }

            when {
                matches("com.android.vending") -> InstallSource.GOOGLE_PLAY
                matches("ru.vk.store", "ru.rustore") -> InstallSource.RUSTORE
                isSystemApp -> InstallSource.SYSTEM_PREBUILT
                else -> InstallSource.UNKNOWN_OR_GITHUB
            }
        } catch (_: Exception) {
            InstallSource.UNKNOWN_OR_GITHUB
        }
    }

    enum class InstallSource(val title: String, val isOfficial: Boolean) {
        GOOGLE_PLAY("Маркет (Google Play)", true),
        SYSTEM_PREBUILT("Система", true),
        RUSTORE("Маркет (RuStore)", true),
        UNKNOWN_OR_GITHUB("Неизвестный источник / GitHub", false)
    }

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
        get() = prefs.getInt(KEY_CARD_CORNER_RADIUS, 20)
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

    object UpdateChecker {
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/NecroMagik/PureClock/releases"
        const val BETA_BRANCH_URL = "https://github.com/NecroMagik/PureClock/tree/beta"

        data class ReleaseInfo(
            val tagName: String,
            val downloadUrl: String,
            val body: String,
            val isBeta: Boolean
        )

        data class ParsedVersion(
            val major: Int,
            val iteration: Int,
            val minor: Int,
            val letterSuffix: Char? = null
        ) : Comparable<ParsedVersion> {

            override fun compareTo(other: ParsedVersion): Int {
                if (this.major != other.major) return this.major.compareTo(other.major)
                if (this.iteration != other.iteration) return this.iteration.compareTo(other.iteration)
                if (this.minor != other.minor) return this.minor.compareTo(other.minor)

                return when {
                    this.letterSuffix == null && other.letterSuffix == null -> 0
                    this.letterSuffix != null && other.letterSuffix == null -> 1
                    this.letterSuffix == null && other.letterSuffix != null -> -1
                    else -> this.letterSuffix!!.compareTo(other.letterSuffix!!)
                }
            }
        }

        fun parseVersion(raw: String): ParsedVersion? {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            val regex = Regex("""^(\d+)\.(\d)(\d+)([a-zA-Z])?$""")
            val match = regex.find(cleaned) ?: return null

            val major = match.groupValues[1].toIntOrNull() ?: 1
            val iteration = match.groupValues[2].toIntOrNull() ?: 0
            val minor = match.groupValues[3].toIntOrNull() ?: 0
            val suffix = match.groupValues.getOrNull(4)?.firstOrNull()?.lowercaseChar()

            return ParsedVersion(major, iteration, minor, suffix)
        }

        suspend fun checkForBetaUpdates(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
            try {
                val currentParsed = parseVersion(currentVersion) ?: return@withContext null

                val url = URL(GITHUB_RELEASES_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)

                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val tagName = json.getString("tag_name")
                        val remoteParsed = parseVersion(tagName) ?: continue

                        val isBeta = remoteParsed.letterSuffix != null
                        if (isBeta && remoteParsed > currentParsed) {
                            val assets = json.getJSONArray("assets")
                            if (assets.length() > 0) {
                                val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                                val changelog = json.optString("body", "Новая бета-версия доступна!")
                                return@withContext ReleaseInfo(tagName, downloadUrl, changelog, isBeta = true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}