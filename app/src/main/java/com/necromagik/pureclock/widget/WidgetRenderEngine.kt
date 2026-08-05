package com.necromagik.pureclock.widget

import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

object WidgetRenderEngine {

    fun buildCustomRemoteViews(
        context: Context,
        config: WidgetConfig
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_pure_clock)
        val bitmap = renderCustomWidgetBitmap(context, config)
        views.setImageViewBitmap(R.id.widget_image_container, bitmap)
        return views
    }

    fun renderCustomWidgetBitmap(context: Context, config: WidgetConfig): Bitmap {
        val width = 1000
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val settings = SettingsManager.getInstance(context)
        val themeState = settings.themeState.value

        val effectiveAccentColor = if (config.useAppTheme) {
            settings.getAccentColor(themeState.accentColorHex).toArgb()
        } else {
            try { Color.parseColor(config.dateColorHex) } catch (_: Exception) { Color.parseColor("#EB0029") }
        }

        val primaryTextColor = try { Color.parseColor(config.timeColorHex) } catch (_: Exception) { Color.WHITE }
        val bgColor = if (config.useAppTheme && themeState.isPureMonocolor) Color.BLACK else {
            try { Color.parseColor(config.backgroundColorHex) } catch (_: Exception) { Color.parseColor("#0B0B0B") }
        }

        val rect = RectF(20f, 20f, width - 20f, height - 20f)
        val radius = if (config.useAppTheme) themeState.cardCornerRadiusDp.toFloat() else config.cornerRadiusDp.toFloat()

        // 1. Отрисовка фона (если включен)
        if (config.showBackground) {
            val bgAlphaInt = (config.backgroundAlpha * 255).toInt()
            paint.color = Color.argb(bgAlphaInt, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 2. Отрисовка независимой границы (если включена)
        if (config.showBorder) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = config.borderWidthDp.toFloat()
            val borderColor = try { Color.parseColor(config.borderColorHex) } catch (_: Exception) { effectiveAccentColor }
            paint.color = borderColor
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        val cal = Calendar.getInstance()
        val pos = config.safePosition

        var startY = when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT -> config.timeFontSizeSp * 1.1f + 20f
            ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT -> height * 0.45f
            ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT -> height * 0.62f
        }

        // 3. Часы
        if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
            val analogRadius = config.timeFontSizeSp * 1.1f
            val clockCenterX = when (pos) {
                ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> analogRadius + 48f
                ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> width / 2f
                ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> width - analogRadius - 48f
            }
            drawAnalogClock(canvas, clockCenterX, startY, analogRadius, cal, config.safeAnalogStyle, effectiveAccentColor, primaryTextColor)
            startY += analogRadius + 30f
        } else {
            drawDigitalClock(canvas, width, startY, pos, cal, config, effectiveAccentColor, primaryTextColor)
            startY += config.timeFontSizeSp * 0.75f + 16f
        }

        // 4. Дата и Погода
        paint.style = Paint.Style.FILL

        if (config.showDate) {
            paint.color = effectiveAccentColor
            paint.textSize = config.dateFontSizeSp.toFloat()
            paint.typeface = if (config.isDateBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(cal.time).replaceFirstChar { it.uppercase() }
            val xPos = getXForPosition(pos, width, paint.measureText(dateStr))
            canvas.drawText(dateStr, xPos, startY, paint)
            startY += config.dateFontSizeSp + 14f
        }

        if (config.showWeather) {
            paint.color = try { Color.parseColor(config.weatherColorHex) } catch (_: Exception) { Color.LTGRAY }
            paint.textSize = config.weatherFontSizeSp.toFloat()
            paint.typeface = Typeface.DEFAULT
            val weatherStr = "🌤️ +22°C • Ясно"
            val xPos = getXForPosition(pos, width, paint.measureText(weatherStr))
            canvas.drawText(weatherStr, xPos, startY, paint)
        }

        return bitmap
    }

    private fun drawDigitalClock(
        canvas: Canvas,
        width: Int,
        startY: Float,
        pos: ClockPosition,
        cal: Calendar,
        config: WidgetConfig,
        accentColor: Int,
        textColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.timeFontSizeSp.toFloat()
            style = Paint.Style.FILL
        }

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

        when (config.safeDigitalStyle) {
            DigitalStyleType.OXYGEN_BOLD_FLUID -> {
                if (timeStr.isNotEmpty()) {
                    val firstChar = timeStr.substring(0, 1)
                    val restStr = timeStr.substring(1)
                    paint.typeface = Typeface.DEFAULT_BOLD
                    val totalWidth = paint.measureText(timeStr)
                    var xPos = getXForPosition(pos, width, totalWidth)

                    paint.color = accentColor
                    canvas.drawText(firstChar, xPos, startY, paint)
                    xPos += paint.measureText(firstChar)

                    paint.color = textColor
                    canvas.drawText(restStr, xPos, startY, paint)
                }
            }
            DigitalStyleType.STACK_TWO_LINE -> {
                paint.typeface = Typeface.DEFAULT_BOLD
                val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
                val minStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)

                paint.color = accentColor
                canvas.drawText(hourStr, getXForPosition(pos, width, paint.measureText(hourStr)), startY - config.timeFontSizeSp * 0.4f, paint)
                paint.color = textColor
                canvas.drawText(minStr, getXForPosition(pos, width, paint.measureText(minStr)), startY + config.timeFontSizeSp * 0.5f, paint)
            }
            DigitalStyleType.LED_3D_SEGMENT -> {
                paint.typeface = Typeface.MONOSPACE
                paint.color = accentColor
                val xPos = getXForPosition(pos, width, paint.measureText(timeStr))
                canvas.drawText(timeStr, xPos, startY, paint)
            }
            DigitalStyleType.CYBER_CONSOLE -> {
                paint.typeface = Typeface.MONOSPACE
                paint.color = textColor
                val cyberStr = "[$timeStr]"
                val xPos = getXForPosition(pos, width, paint.measureText(cyberStr))
                canvas.drawText(cyberStr, xPos, startY, paint)
            }
            DigitalStyleType.TYPO_LARGE_MINIMAL -> {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = textColor
                val xPos = getXForPosition(pos, width, paint.measureText(timeStr))
                canvas.drawText(timeStr, xPos, startY, paint)
            }
        }
    }

    private fun drawAnalogClock(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        cal: Calendar,
        style: AnalogStyleType,
        accentColor: Int,
        textColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hours = cal.get(Calendar.HOUR)
        val minutes = cal.get(Calendar.MINUTE)

        paint.color = textColor
        paint.style = Paint.Style.STROKE

        when (style) {
            AnalogStyleType.OXYGEN_NEVER_SETTLE -> {
                paint.strokeWidth = radius * 0.05f
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val startX = (cx + (radius * 0.82f) * sin(angle)).toFloat()
                    val startY = (cy - (radius * 0.82f) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()
                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
            AnalogStyleType.CLASSIC_INDEXES -> {
                paint.strokeWidth = radius * 0.04f
                canvas.drawCircle(cx, cy, radius, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = radius * 0.3f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("12", cx - radius * 0.12f, cy - radius * 0.65f, paint)
                canvas.drawText("6", cx - radius * 0.05f, cy + radius * 0.85f, paint)
                canvas.drawText("3", cx + radius * 0.7f, cy + radius * 0.1f, paint)
                canvas.drawText("9", cx - radius * 0.85f, cy + radius * 0.1f, paint)
                paint.style = Paint.Style.STROKE
            }
            AnalogStyleType.CHRONO_SPORT -> {
                paint.strokeWidth = radius * 0.03f
                for (i in 0 until 60) {
                    val angle = Math.toRadians((i * 6).toDouble())
                    val len = if (i % 5 == 0) radius * 0.2f else radius * 0.08f
                    val startX = (cx + (radius - len) * sin(angle)).toFloat()
                    val startY = (cy - (radius - len) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()
                    paint.color = if (i % 15 == 0) accentColor else textColor
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
            AnalogStyleType.BAUHAUS_MINIMAL -> {
                paint.strokeWidth = radius * 0.06f
                paint.color = textColor
                canvas.drawCircle(cx, cy, radius, paint)
            }
            AnalogStyleType.ZEN_SPACE_DOTS -> {
                paint.style = Paint.Style.FILL
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val dotX = (cx + radius * sin(angle)).toFloat()
                    val dotY = (cy - radius * cos(angle)).toFloat()
                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawCircle(dotX, dotY, radius * 0.06f, paint)
                }
                paint.style = Paint.Style.STROKE
            }
        }

        // Стрелки
        val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30).toDouble())
        paint.color = textColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.08f
        canvas.drawLine(cx, cy, (cx + radius * 0.5f * sin(hourAngle)).toFloat(), (cy - radius * 0.5f * cos(hourAngle)).toFloat(), paint)

        val minuteAngle = Math.toRadians((minutes * 6).toDouble())
        paint.color = accentColor
        paint.strokeWidth = radius * 0.05f
        canvas.drawLine(cx, cy, (cx + radius * 0.78f * sin(minuteAngle)).toFloat(), (cy - radius * 0.78f * cos(minuteAngle)).toFloat(), paint)

        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * 0.08f, paint)
    }

    private fun getXForPosition(pos: ClockPosition, width: Int, textWidth: Float): Float {
        return when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> 48f
            ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (width - textWidth) / 2f
            ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> width - textWidth - 48f
        }
    }
}