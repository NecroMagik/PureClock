package com.necromagik.pureclock.data.repository

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.google.gson.Gson
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.*

class WidgetConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("pureclock_widgets_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val settingsManager = SettingsManager.getInstance(context)

    fun saveConfig(widgetId: Int, config: WidgetConfig) {
        val json = gson.toJson(config)
        prefs.edit { putString("widget_config_$widgetId", json) }
    }

    fun getConfig(widgetId: Int): WidgetConfig {
        val json = prefs.getString("widget_config_$widgetId", null)
            ?: prefs.getString("widget_config_0", null)
            ?: return createDefaultConfig(widgetId)

        return try {
            val parsed = gson.fromJson(json, WidgetConfig::class.java)
            sanitizeConfig(widgetId, parsed)
        } catch (_: Exception) {
            createDefaultConfig(widgetId)
        }
    }

    private fun createDefaultConfig(widgetId: Int): WidgetConfig {
        val accentHex = getAppAccentHex()
        return WidgetConfig(
            id = widgetId,
            dateColorHexNullable = accentHex,
            borderColorHexNullable = accentHex,
            digitColorsHex = listOf(accentHex, "#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFFFFF")
        )
    }

    private fun getAppAccentHex(): String {
        val argb = settingsManager.accentColor.toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }

    private fun sanitizeConfig(widgetId: Int, parsed: WidgetConfig?): WidgetConfig {
        if (parsed == null) return createDefaultConfig(widgetId)
        val accentHex = getAppAccentHex()

        val validElements = parsed.elementOrder?.filterNotNull().orEmpty().ifEmpty {
            listOf(WidgetElementType.TIME, WidgetElementType.DATE)
        }

        val defaultDigitList = listOf(accentHex, "#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFFFFF")
        val rawDigits = parsed.digitColorsHex?.filterNotNull().orEmpty()
        val safeDigits = List(5) { idx -> rawDigits.getOrNull(idx) ?: defaultDigitList[idx] }

        return parsed.copy(
            id = widgetId,
            position = parsed.position ?: ClockPosition.CENTER,
            elementOrder = validElements,
            displayMode = parsed.displayMode ?: ClockDisplayMode.DIGITAL,
            digitalStyle = parsed.digitalStyle ?: DigitalStyleType.OXYGEN_BOLD_FLUID,
            analogStyle = parsed.analogStyle ?: AnalogStyleType.OXYGEN_NEVER_SETTLE,
            timeFontSizeSp = if (parsed.timeFontSizeSp <= 0) 76 else parsed.timeFontSizeSp,
            timeColoringMode = parsed.timeColoringMode ?: TimeColoringMode.FIRST_DIGIT_ACCENT,
            timeColorHexNullable = parsed.timeColorHexNullable ?: "#FFFFFF",
            digitColorsHex = safeDigits,
            dateFontSizeSp = if (parsed.dateFontSizeSp <= 0) 22 else parsed.dateFontSizeSp,
            dateStyle = parsed.dateStyle ?: DateStyleType.FULL_RU,
            dateColorHexNullable = parsed.dateColorHexNullable ?: accentHex,
            backgroundMode = parsed.backgroundMode ?: WidgetBackgroundMode.SYSTEM_CONTRAST,
            backgroundColorHexNullable = parsed.backgroundColorHexNullable ?: "#0B0B0B",
            textShadowRadiusDp = if (parsed.textShadowRadiusDp <= 0f) 8f else parsed.textShadowRadiusDp,
            textShadowIntensity = parsed.textShadowIntensity.coerceIn(0f, 1f),
            textShadowColorHexNullable = parsed.textShadowColorHexNullable ?: "#000000",
            borderStyle = parsed.borderStyle ?: BorderStyle.SOLID,
            borderColorHexNullable = parsed.borderColorHexNullable ?: accentHex,
            borderWidthDp = if (parsed.borderWidthDp <= 0) 2 else parsed.borderWidthDp,
            cornerRadiusDp = if (parsed.cornerRadiusDp < 0) 28 else parsed.cornerRadiusDp
        )
    }

    fun deleteConfig(widgetId: Int) {
        prefs.edit { remove("widget_config_$widgetId") }
    }

    fun cleanupOrphanedConfigs(activeIds: IntArray) {
        val activeSet = activeIds.map { "widget_config_$it" }.toSet()
        val allKeys = prefs.all.keys.filter { it.startsWith("widget_config_") && it != "widget_config_0" }

        prefs.edit {
            allKeys.forEach { key ->
                if (!activeSet.contains(key)) {
                    remove(key)
                }
            }
        }
    }
}