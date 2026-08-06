package com.necromagik.pureclock.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.toArgb
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.MainActivity
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

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            config.id,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_image_container, pendingIntent)

        return views
    }

    fun renderCustomWidgetBitmap(context: Context, config: WidgetConfig): Bitmap {
        val width = 720
        val height = 480
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val settings = SettingsManager.getInstance(context)
        val themeState = settings.themeState.value

        val effectiveAccentColor = if (config.useAppTheme) {
            settings.getAccentColor(themeState.accentColorHex).toArgb()
        } else {
            try { config.dateColorHex.toColorInt() } catch (_: Exception) { "#EB0029".toColorInt() }
        }

        val primaryTextColor = try { config.timeColorHex.toColorInt() } catch (_: Exception) { Color.WHITE }
        val bgColor = if (config.useAppTheme && themeState.isPureMonocolor) Color.BLACK else {
            try { config.backgroundColorHex.toColorInt() } catch (_: Exception) { "#0B0B0B".toColorInt() }
        }

        val strokeInset = config.borderWidthDp.toFloat() / 2f + 8f
        val rect = RectF(strokeInset, strokeInset, width - strokeInset, height - strokeInset)
        val radius = if (config.useAppTheme) themeState.cardCornerRadiusDp.toFloat() else config.cornerRadiusDp.toFloat()

        // 1. Подложка
        if (config.showBackground) {
            val bgAlphaInt = (config.backgroundAlpha * 255).toInt()
            paint.color = Color.argb(bgAlphaInt, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 2. Рамка
        if (config.showBorder) {
            val borderColor = try { config.borderColorHex.toColorInt() } catch (_: Exception) { effectiveAccentColor }
            val borderStrokeWidth = config.borderWidthDp.toFloat()
            val halfStroke = borderStrokeWidth / 2f
            val borderRect = RectF(
                rect.left + halfStroke,
                rect.top + halfStroke,
                rect.right - halfStroke,
                rect.bottom - halfStroke
            )

            if (config.enableBorderGlow) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderStrokeWidth + 8f
                    color = Color.argb(100, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
                    maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRoundRect(borderRect, radius, radius, glowPaint)
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderStrokeWidth
                color = borderColor
            }

            when (config.borderStyle) {
                BorderStyle.DASHED -> {
                    borderPaint.pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
                    canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
                }
                BorderStyle.DOUBLE_LINE -> {
                    canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
                    val doubleInset = borderStrokeWidth + 4f
                    val innerRect = RectF(
                        borderRect.left + doubleInset,
                        borderRect.top + doubleInset,
                        borderRect.right - doubleInset,
                        borderRect.bottom - doubleInset
                    )
                    borderPaint.strokeWidth = (borderStrokeWidth * 0.6f).coerceAtLeast(1.5f)
                    canvas.drawRoundRect(innerRect, (radius - doubleInset).coerceAtLeast(0f), (radius - doubleInset).coerceAtLeast(0f), borderPaint)
                }
                BorderStyle.SOLID -> {
                    canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
                }
            }
        }

        val cal = Calendar.getInstance()
        val pos = config.safePosition

        val activeElements = config.safeElementOrder.filter { element ->
            when (element) {
                WidgetElementType.TIME -> true
                WidgetElementType.DATE -> config.showDate
                WidgetElementType.WEATHER -> config.showWeather
            }
        }

        val elementHeights = activeElements.map { element ->
            when (element) {
                WidgetElementType.TIME -> {
                    if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                        config.timeFontSizeSp * 1.8f
                    } else {
                        val p = Paint().apply { textSize = config.timeFontSizeSp.toFloat(); typeface = Typeface.DEFAULT_BOLD }
                        val fm = p.fontMetrics
                        (fm.descent - fm.ascent)
                    }
                }
                WidgetElementType.DATE -> {
                    val p = Paint().apply { textSize = config.dateFontSizeSp.toFloat() }
                    val fm = p.fontMetrics
                    (fm.descent - fm.ascent)
                }
                WidgetElementType.WEATHER -> {
                    val p = Paint().apply { textSize = config.weatherFontSizeSp.toFloat() }
                    val fm = p.fontMetrics
                    (fm.descent - fm.ascent)
                }
            }
        }

        val itemSpacing = 12f
        val totalBlockHeight = elementHeights.sum() + (activeElements.size - 1).coerceAtLeast(0) * itemSpacing

        var currentTopY = when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT -> 32f
            ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT -> (height - totalBlockHeight) / 2f
            ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT -> height - totalBlockHeight - 32f
        }

        activeElements.forEachIndexed { index, element ->
            val elementH = elementHeights[index]

            when (element) {
                WidgetElementType.TIME -> {
                    if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                        val analogRadius = elementH / 2f
                        val centerY = currentTopY + analogRadius
                        val clockCenterX = getXForWidth(pos, width, analogRadius * 2f) + analogRadius
                        drawAnalogClock(canvas, clockCenterX, centerY, analogRadius * 0.9f, cal, config.safeAnalogStyle, effectiveAccentColor, primaryTextColor)
                    } else {
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = config.timeFontSizeSp.toFloat()
                            typeface = Typeface.DEFAULT_BOLD
                        }
                        val fm = textPaint.fontMetrics
                        val baselineY = currentTopY - fm.ascent
                        drawDigitalClock(canvas, textPaint, width, baselineY, pos, cal, config, effectiveAccentColor, primaryTextColor)
                    }
                }
                WidgetElementType.DATE -> {
                    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = effectiveAccentColor
                        textSize = config.dateFontSizeSp.toFloat()
                        typeface = if (config.isDateBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    }
                    val fm = datePaint.fontMetrics
                    val baselineY = currentTopY - fm.ascent
                    val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(cal.time).replaceFirstChar { it.uppercase() }
                    val xPos = getXForWidth(pos, width, datePaint.measureText(dateStr))
                    canvas.drawText(dateStr, xPos, baselineY, datePaint)
                }
                WidgetElementType.WEATHER -> {
                    val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = try { config.weatherColorHex.toColorInt() } catch (_: Exception) { Color.LTGRAY }
                        textSize = config.weatherFontSizeSp.toFloat()
                        typeface = Typeface.DEFAULT
                    }
                    val fm = weatherPaint.fontMetrics
                    val baselineY = currentTopY - fm.ascent
                    val weatherStr = "🌤️ +22°C • Ясно"
                    val xPos = getXForWidth(pos, width, weatherPaint.measureText(weatherStr))
                    canvas.drawText(weatherStr, xPos, baselineY, weatherPaint)
                }
            }

            currentTopY += elementH + itemSpacing
        }

        return bitmap
    }

    private fun drawDigitalClock(
        canvas: Canvas,
        paint: Paint,
        width: Int,
        baselineY: Float,
        pos: ClockPosition,
        cal: Calendar,
        config: WidgetConfig,
        accentColor: Int,
        textColor: Int
    ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

        when (config.safeDigitalStyle) {
            DigitalStyleType.OXYGEN_BOLD_FLUID -> {
                if (timeStr.isNotEmpty()) {
                    val firstChar = timeStr.substring(0, 1)
                    val restStr = timeStr.substring(1)
                    val totalWidth = paint.measureText(timeStr)
                    var xPos = getXForWidth(pos, width, totalWidth)

                    paint.color = accentColor
                    canvas.drawText(firstChar, xPos, baselineY, paint)
                    xPos += paint.measureText(firstChar)

                    paint.color = textColor
                    canvas.drawText(restStr, xPos, baselineY, paint)
                }
            }
            DigitalStyleType.STACK_TWO_LINE -> {
                val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
                val minStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)

                paint.color = accentColor
                canvas.drawText(hourStr, getXForWidth(pos, width, paint.measureText(hourStr)), baselineY - config.timeFontSizeSp * 0.3f, paint)
                paint.color = textColor
                canvas.drawText(minStr, getXForWidth(pos, width, paint.measureText(minStr)), baselineY + config.timeFontSizeSp * 0.5f, paint)
            }
            DigitalStyleType.LED_3D_SEGMENT, DigitalStyleType.CYBER_CONSOLE -> {
                paint.typeface = Typeface.MONOSPACE
                paint.color = textColor
                val str = if (config.safeDigitalStyle == DigitalStyleType.CYBER_CONSOLE) "[$timeStr]" else timeStr
                val xPos = getXForWidth(pos, width, paint.measureText(str))
                canvas.drawText(str, xPos, baselineY, paint)
            }
            DigitalStyleType.TYPO_LARGE_MINIMAL -> {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = textColor
                val xPos = getXForWidth(pos, width, paint.measureText(timeStr))
                canvas.drawText(timeStr, xPos, baselineY, paint)
            }
        }
    }

    private fun drawAnalogClock(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        cal: Calendar,
        clockStyle: AnalogStyleType,
        accentColor: Int,
        textColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            style = Paint.Style.STROKE
        }
        val hours = cal.get(Calendar.HOUR)
        val minutes = cal.get(Calendar.MINUTE)

        when (clockStyle) {
            AnalogStyleType.OXYGEN_NEVER_SETTLE -> {
                paint.strokeWidth = radius * 0.06f
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val startX = (cx + (radius * 0.8f) * sin(angle)).toFloat()
                    val startY = (cy - (radius * 0.8f) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()
                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
            else -> {
                paint.strokeWidth = radius * 0.05f
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }

        val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30).toDouble())
        paint.color = textColor
        paint.strokeWidth = radius * 0.09f
        canvas.drawLine(cx, cy, (cx + radius * 0.5f * sin(hourAngle)).toFloat(), (cy - radius * 0.5f * cos(hourAngle)).toFloat(), paint)

        val minuteAngle = Math.toRadians((minutes * 6).toDouble())
        paint.color = accentColor
        paint.strokeWidth = radius * 0.06f
        canvas.drawLine(cx, cy, (cx + radius * 0.78f * sin(minuteAngle)).toFloat(), (cy - radius * 0.78f * cos(minuteAngle)).toFloat(), paint)

        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * 0.08f, paint)
    }

    private fun getXForWidth(pos: ClockPosition, width: Int, totalWidth: Float): Float {
        return when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> 40f
            ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (width - totalWidth) / 2f
            ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> width - totalWidth - 40f
        }
    }
}