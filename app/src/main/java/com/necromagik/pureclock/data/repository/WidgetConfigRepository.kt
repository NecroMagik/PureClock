package com.necromagik.pureclock.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.BorderStyle
import com.necromagik.pureclock.data.model.WidgetConfig
import com.necromagik.pureclock.data.model.WidgetElementType

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
        } catch (e: Exception) {
            createDefaultConfig(widgetId)
        }
    }

    private fun createDefaultConfig(widgetId: Int): WidgetConfig {
        val accentHex = getAppAccentHex()
        return WidgetConfig(
            id = widgetId,
            dateColorHexNullable = accentHex,
            borderColorHexNullable = accentHex
        )
    }

    private fun getAppAccentHex(): String {
        val colorInt = settingsManager.accentColor.value.toInt()
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }

    private fun sanitizeConfig(widgetId: Int, parsed: WidgetConfig?): WidgetConfig {
        if (parsed == null) return createDefaultConfig(widgetId)
        val accentHex = getAppAccentHex()

        val validElements = parsed.elementOrder.filterNotNull().ifEmpty {
            listOf(WidgetElementType.TIME, WidgetElementType.DATE)
        }

        return parsed.copy(
            id = widgetId,
            elementOrder = validElements,
            timeFontSizeSp = if (parsed.timeFontSizeSp <= 0) 76 else parsed.timeFontSizeSp,
        timeColorHexNullable = parsed.timeColorHexNullable ?: "#FFFFFF",
        dateFontSizeSp = if (parsed.dateFontSizeSp <= 0) 22 else parsed.dateFontSizeSp,
        dateColorHexNullable = parsed.dateColorHexNullable ?: accentHex,
        backgroundColorHexNullable = parsed.backgroundColorHexNullable ?: "#0B0B0B",
        borderStyle = parsed.borderStyle ?: BorderStyle.SOLID,
        borderColorHexNullable = parsed.borderColorHexNullable ?: accentHex,
        borderWidthDp = if (parsed.borderWidthDp <= 0) 3 else parsed.borderWidthDp,
        cornerRadiusDp = if (parsed.cornerRadiusDp <= 0) 24 else parsed.cornerRadiusDp
        )
    }

    fun deleteConfig(widgetId: Int) {
        prefs.edit { remove("widget_config_$widgetId") }
    }
}