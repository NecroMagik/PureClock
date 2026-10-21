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
import kotlin.apply
import com.necromagik.pureclock.ui.theme.SystemThemeUtils

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

enum class AlarmPickerStyle(val title: String, val description: String) {
    DIAL("Циферблат", "Классический 24-часовой круговой селектор"),
    WHEEL("Барабанный селектор", "Вертикальные барабаны с инерционной прокруткой"),
    TIMELINE("Временная шкала", "Две горизонтальные шкалы со скольжением")
}

data class ThemeState(
    val themeMode: String = "SYSTEM",
    val isPureMonocolor: Boolean = true,
    val accentColorHex: String = SystemThemeUtils.SYSTEM_MONET_KEY,
    val cardCornerRadiusDp: Int = 20,
    val is3DEffectsEnabled: Boolean = true,
    val isNeonGlowEnabled: Boolean = true,
    val depthIntensityDp: Int = 8
)

class SettingsManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pure_clock_settings", Context.MODE_PRIVATE)

    val appVersionName: String by lazy {
        try {
            val pkgInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pkgInfo.versionName?.removePrefix("v")?.removePrefix("V") ?: "1.35"
        } catch (_: Exception) {
            "1.35"
        }
    }

    val appVersionDisplay: String by lazy {
        "v$appVersionName"
    }

    private val _ifRelease = MutableStateFlow(prefs.getInt(KEY_IF_RELEASE, 0))
    val ifReleaseFlow: StateFlow<Int> = _ifRelease.asStateFlow()

    var ifRelease: Int
        get() = _ifRelease.value
        set(value) {
            _ifRelease.value = value
            prefs.edit().putInt(KEY_IF_RELEASE, value).apply()
        }

    val isScenaryPublic: Boolean
        get() = ifRelease == 1

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
        private const val KEY_IF_RELEASE = "if_release_scenary_flag"
        private const val KEY_START_MAIN_TAB = "start_main_tab_index"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _alarmPickerStyle = MutableStateFlow(
        try {
            AlarmPickerStyle.valueOf(
                prefs.getString("alarm_time_picker_style", AlarmPickerStyle.DIAL.name) ?: AlarmPickerStyle.DIAL.name
            )
        } catch (_: Exception) {
            AlarmPickerStyle.DIAL
        }
    )
    val alarmPickerStyleFlow: StateFlow<AlarmPickerStyle> = _alarmPickerStyle.asStateFlow()

    var selectedAlarmPickerStyle: AlarmPickerStyle
        get() = _alarmPickerStyle.value
        set(value) {
            _alarmPickerStyle.value = value
            prefs.edit().putString("alarm_time_picker_style", value.name).apply()
        }

    private val _themeState = MutableStateFlow(
        ThemeState(
            themeMode = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM",
            isPureMonocolor = prefs.getBoolean(KEY_PURE_MONOCOLOR, true),
            accentColorHex = prefs.getString(KEY_ACCENT_COLOR, SystemThemeUtils.SYSTEM_MONET_KEY)
                ?: SystemThemeUtils.SYSTEM_MONET_KEY,
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

    private val _startMainTab = MutableStateFlow(prefs.getInt(KEY_START_MAIN_TAB, 0))
    val startMainTabFlow: StateFlow<Int> = _startMainTab.asStateFlow()

    var startMainTab: Int
        get() = _startMainTab.value
        set(value) {
            _startMainTab.value = value
            prefs.edit().putInt(KEY_START_MAIN_TAB, value).apply()
        }

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
        android.util.Log.d("PureClock_UPDATER", "Проверка Google Play In-App Updates. Источник: $source, текущая версия: $appVersionName")
        if (source != InstallSource.GOOGLE_PLAY && source != InstallSource.SYSTEM_PREBUILT) return null
        return _googlePlayUpdateManager.checkUpdateAvailability()
    }

    suspend fun checkRuStoreInAppUpdates(): RuStoreUpdateManager.RuStoreAppInfo? {
        val source = getInstallationSource()
        android.util.Log.d("PureClock_UPDATER", "Проверка RuStore Updates. Источник: $source, текущая версия: $appVersionName")
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
        get() = prefs.getString(KEY_ACCENT_COLOR, SystemThemeUtils.SYSTEM_MONET_KEY)
            ?: SystemThemeUtils.SYSTEM_MONET_KEY
        set(value) {
            prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()
            _themeState.value = _themeState.value.copy(accentColorHex = value)
        }

    val accentColor: Color
        get() = getAccentColor(accentColorHex)

    fun getAccentColor(hex: String = accentColorHex): Color {
        return try {
            val resolvedHex = if (hex.equals(SystemThemeUtils.SYSTEM_MONET_KEY, ignoreCase = true)) {
                SystemThemeUtils.getSystemAccentHex(appContext)
            } else {
                hex
            }
            Color(android.graphics.Color.parseColor(resolvedHex))
        } catch (_: Exception) {
            Color(android.graphics.Color.parseColor(SystemThemeUtils.getSystemAccentHex(appContext)))
        }
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

    object UpdateChecker {
        private const val TAG = "PureClock_UPDATER"
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
                    // Если номера равны (например 1.35 и 1.35a), версия с буквой считается более свежим патчем/бетой
                    this.letterSuffix != null && other.letterSuffix == null -> 1
                    this.letterSuffix == null && other.letterSuffix != null -> -1
                    else -> this.letterSuffix!!.compareTo(other.letterSuffix!!)
                }
            }

            override fun toString(): String {
                return "$major.$iteration${String.format("%01d", minor)}${letterSuffix ?: ""}"
            }
        }

        fun parseVersion(raw: String): ParsedVersion? {
            val cleaned = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore(" ")
                .substringBefore("-")

            // Поддержка форматов: "1.35", "1.32c", "1.5"
            val regex = Regex("""^(\d+)\.(\d)(\d*)([a-zA-Z])?$""")
            val match = regex.find(cleaned)
            if (match == null) {
                android.util.Log.w(TAG, "Не удалось распарсить версию из строки: '$raw' (cleaned='$cleaned')")
                return null
            }

            val major = match.groupValues[1].toIntOrNull() ?: 1
            val iteration = match.groupValues[2].toIntOrNull() ?: 0
            val minorStr = match.groupValues[3]
            val minor = if (minorStr.isEmpty()) 0 else (minorStr.toIntOrNull() ?: 0)
            val suffix = match.groupValues.getOrNull(4)?.firstOrNull()?.lowercaseChar()

            return ParsedVersion(major, iteration, minor, suffix)
        }

        /**
         * Универсальная проверка обновлений на GitHub:
         * Ищет самый свежий релиз (и стабильный, и бета, если allowBeta = true), который выше currentVersion.
         */
        suspend fun checkForBetaUpdates(
            currentVersion: String,
            allowBeta: Boolean = true
        ): ReleaseInfo? = withContext(Dispatchers.IO) {
            android.util.Log.i(TAG, "==> Старт проверки обновлений GitHub. Текущая версия: '$currentVersion', allowBeta=$allowBeta")

            try {
                val currentParsed = parseVersion(currentVersion)
                if (currentParsed == null) {
                    android.util.Log.e(TAG, "!!!! Ошибка: не удалось распарсить текущую версию '$currentVersion'")
                    return@withContext null
                }
                android.util.Log.d(TAG, "Текущая версия распознана как: major=${currentParsed.major}, iter=${currentParsed.iteration}, minor=${currentParsed.minor}, suffix=${currentParsed.letterSuffix}")

                val url = URL(GITHUB_RELEASES_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "PureClock-Android-Updater")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                android.util.Log.d(TAG, "Ответ GitHub API: HTTP $responseCode")

                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    android.util.Log.d(TAG, "Найдено релизов в репозитории: ${jsonArray.length()}")

                    var bestCandidate: Pair<ParsedVersion, ReleaseInfo>? = null

                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val isDraft = json.optBoolean("draft", false)
                        if (isDraft) continue

                        val tagName = json.optString("tag_name", "")
                        val isPrerelease = json.optBoolean("prerelease", false)
                        val remoteParsed = parseVersion(tagName) ?: continue

                        val isBetaRelease = isPrerelease || remoteParsed.letterSuffix != null
                        android.util.Log.d(TAG, "Релиз [$i]: tag='$tagName', isBeta=$isBetaRelease, compare=${remoteParsed.compareTo(currentParsed)}")

                        // Если бета-канал выключен, пропускаем бета-сборки, но пропускаем вперёд стабильные релизы!
                        if (!allowBeta && isBetaRelease) {
                            continue
                        }

                        if (remoteParsed > currentParsed) {
                            val assets = json.optJSONArray("assets")
                            var downloadUrl: String? = null

                            if (assets != null && assets.length() > 0) {
                                // Ищем .apk среди ассетов или берём первый ассет
                                for (a in 0 until assets.length()) {
                                    val assetObj = assets.getJSONObject(a)
                                    val assetUrl = assetObj.optString("browser_download_url", "")
                                    if (assetUrl.endsWith(".apk", ignoreCase = true)) {
                                        downloadUrl = assetUrl
                                        break
                                    }
                                }
                                if (downloadUrl == null) {
                                    downloadUrl = assets.getJSONObject(0).optString("browser_download_url")
                                }
                            }

                            // Если APK в assets не прикреплён, даём ссылку на страницу самого релиза
                            if (downloadUrl.isNullOrEmpty()) {
                                downloadUrl = json.optString("html_url", BETA_BRANCH_URL)
                            }

                            val changelog = json.optString("body", "Доступна новая версия $tagName!")
                            val info = ReleaseInfo(
                                tagName = tagName,
                                downloadUrl = downloadUrl!!,
                                body = changelog,
                                isBeta = isBetaRelease
                            )

                            if (bestCandidate == null || remoteParsed > bestCandidate.first) {
                                bestCandidate = remoteParsed to info
                            }
                        }
                    }

                    if (bestCandidate != null) {
                        android.util.Log.i(TAG, "==> НАЙДЕНО ОБНОВЛЕНИЕ: ${bestCandidate.second.tagName} (${bestCandidate.second.downloadUrl})")
                        return@withContext bestCandidate.second
                    } else {
                        android.util.Log.i(TAG, "==> Обновлений новее '$currentVersion' не найдено.")
                    }
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    android.util.Log.e(TAG, "!!!! Ошибка GitHub API ($responseCode): $errText")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "!!!! Исключение при проверке обновлений: ${e.message}", e)
            }
            return@withContext null
        }
    }
}