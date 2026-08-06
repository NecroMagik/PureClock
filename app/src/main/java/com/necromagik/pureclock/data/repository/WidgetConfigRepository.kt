package com.necromagik.pureclock.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.necromagik.pureclock.data.model.BorderStyle
import com.necromagik.pureclock.data.model.WidgetConfig

class WidgetConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pureclock_widgets_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveConfig(widgetId: Int, config: WidgetConfig) {
        val json = gson.toJson(config)
        prefs.edit { putString("widget_config_$widgetId", json) }
    }

    fun getConfig(widgetId: Int): WidgetConfig {
        val json = prefs.getString("widget_config_$widgetId", null)
            ?: prefs.getString("widget_config_0", null)
            ?: return WidgetConfig(id = widgetId)

        return try {
            val parsed = gson.fromJson(json, WidgetConfig::class.java)
            sanitizeConfig(widgetId, parsed)
        } catch (_: Exception) {
            WidgetConfig(id = widgetId)
        }
    }

    private fun sanitizeConfig(widgetId: Int, parsed: WidgetConfig?): WidgetConfig {
        if (parsed == null) return WidgetConfig(id = widgetId)
        return WidgetConfig(
            id = widgetId,
            useAppTheme = parsed.useAppTheme,
            position = parsed.safePosition,
            elementOrder = parsed.safeElementOrder,
            displayMode = parsed.safeDisplayMode,
            analogStyle = parsed.safeAnalogStyle,
            digitalStyle = parsed.safeDigitalStyle,
            timeFontSizeSp = if (parsed.timeFontSizeSp <= 0) 80 else parsed.timeFontSizeSp,
            timeColorHexNullable = parsed.timeColorHex,
            showDate = parsed.showDate,
            dateFontSizeSp = if (parsed.dateFontSizeSp <= 0) 24 else parsed.dateFontSizeSp,
            dateColorHexNullable = parsed.dateColorHex,
            isDateBold = parsed.isDateBold,
            showWeather = parsed.showWeather,
            weatherFontSizeSp = if (parsed.weatherFontSizeSp <= 0) 20 else parsed.weatherFontSizeSp,
            weatherColorHexNullable = parsed.weatherColorHex,
            showBackground = parsed.showBackground,
            backgroundColorHexNullable = parsed.backgroundColorHex,
            backgroundAlpha = parsed.backgroundAlpha,
            showBorder = parsed.showBorder,
            borderStyle = parsed.borderStyle ?: BorderStyle.SOLID,
            borderColorHexNullable = parsed.borderColorHex,
            borderWidthDp = if (parsed.borderWidthDp <= 0) 3 else parsed.borderWidthDp,
            cornerRadiusDp = parsed.cornerRadiusDp,
            enableBorderGlow = parsed.enableBorderGlow
        )
    }

    fun deleteConfig(widgetId: Int) {
        prefs.edit { remove("widget_config_$widgetId") }
    }
}