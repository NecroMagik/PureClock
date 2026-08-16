package com.necromagik.pureclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
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

    fun buildCustomRemoteViews(context: Context, config: WidgetConfig): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_pure_clock)
        val bitmap = renderCustomWidgetBitmap(context, config)
        views.setImageViewBitmap(R.id.widget_image_container, bitmap)

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
        context,
        config.id * 10 + 1,
        appIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.click_target_app, appPendingIntent)

        val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, config.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val configPendingIntent = PendingIntent.getActivity(
        context,
        config.id * 10 + 2,
        configIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.click_target_config, configPendingIntent)

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
            val bgAlphaInt = (config.backgroundAlpha * 255).toInt().coerceIn(0, 255)
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
                    color = Color.argb(90, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
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
            }
        }

        val elementHeights: List<Float> = activeElements.map { element ->
            when (element) {
                WidgetElementType.TIME -> {
                    if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                        config.timeFontSizeSp * 2.2f
                    } else if (config.safeDigitalStyle == DigitalStyleType.STACK_TWO_LINE) {
                        config.timeFontSizeSp * 1.7f
                    } else {
                        val p = Paint().apply { textSize = config.timeFontSizeSp.toFloat(); typeface = Typeface.DEFAULT_BOLD }
                        val fm = p.fontMetrics
                        fm.descent - fm.ascent
                    }
                }
                WidgetElementType.DATE -> {
                    val p = Paint().apply { textSize = config.dateFontSizeSp.toFloat() }
                    val fm = p.fontMetrics
                    fm.descent - fm.ascent
                }
            }
        }

        val itemSpacing = 16f
        val totalBlockHeight = elementHeights.sum() + (activeElements.size - 1).coerceAtLeast(0) * itemSpacing

        var currentTopY = when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT -> 36f
                ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT -> (height - totalBlockHeight) / 2f
                ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT -> height - totalBlockHeight - 36f
        }

        activeElements.forEachIndexed { index, element ->
            val elementH = elementHeights[index]

            when (element) {
                WidgetElementType.TIME -> {
                    if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                        val analogRadius = elementH / 2f
                        val centerY = currentTopY + analogRadius
                        val clockCenterX = getXForWidth(pos, width, analogRadius * 2f) + analogRadius
                        drawAnalogClock(canvas, clockCenterX, centerY, analogRadius * 0.92f, cal, config.safeAnalogStyle, effectiveAccentColor, primaryTextColor)
                    } else {
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = config.timeFontSizeSp.toFloat()
                            typeface = Typeface.DEFAULT_BOLD
                        }
                        val fm = textPaint.fontMetrics
                        val baselineY = currentTopY - fm.ascent
                        drawDigitalClock(canvas, textPaint, width, baselineY, currentTopY, pos, cal, config, effectiveAccentColor, primaryTextColor)
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
        topY: Float,
        pos: ClockPosition,
        cal: Calendar,
        config: WidgetConfig,
        accentColor: Int,
        textColor: Int
    ) {
        val hoursStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
        val minsStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)
        val fullTimeStr = "$hoursStr:$minsStr"

        when (config.safeDigitalStyle) {
            DigitalStyleType.OXYGEN_BOLD_FLUID -> {
                val firstChar = fullTimeStr.substring(0, 1)
                val restChars = fullTimeStr.substring(1)
                val totalWidth = paint.measureText(fullTimeStr)
                var xPos = getXForWidth(pos, width, totalWidth)

                paint.color = accentColor
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText(firstChar, xPos, baselineY, paint)

                xPos += paint.measureText(firstChar)
                paint.color = textColor
                canvas.drawText(restChars, xPos, baselineY, paint)
            }

            DigitalStyleType.STACK_TWO_LINE -> {
                val lineH = config.timeFontSizeSp.toFloat() * 0.85f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

                val hWidth = paint.measureText(hoursStr)
                val mWidth = paint.measureText(minsStr)

                val hX = getXForWidth(pos, width, hWidth)
                val mX = getXForWidth(pos, width, mWidth)

                val fm = paint.fontMetrics
                val firstBaseY = topY - fm.ascent
                val secondBaseY = firstBaseY + lineH

                paint.color = accentColor
                canvas.drawText(hoursStr, hX, firstBaseY, paint)

                paint.color = textColor
                canvas.drawText(minsStr, mX, secondBaseY, paint)
            }

            DigitalStyleType.NOTHING_DOT_MATRIX -> {
                paint.typeface = Typeface.MONOSPACE
                paint.color = textColor
                paint.letterSpacing = 0.08f
                val xPos = getXForWidth(pos, width, paint.measureText(fullTimeStr))
                canvas.drawText(fullTimeStr, xPos, baselineY, paint)
            }

            DigitalStyleType.CYBER_CONSOLE -> {
                paint.typeface = Typeface.MONOSPACE
                val prefix = "> "
                val suffix = "_"
                val termStr = "$prefix$fullTimeStr$suffix"
                val totalWidth = paint.measureText(termStr)
                var xPos = getXForWidth(pos, width, totalWidth)

                paint.color = accentColor
                canvas.drawText(prefix, xPos, baselineY, paint)
                xPos += paint.measureText(prefix)

                paint.color = textColor
                canvas.drawText(fullTimeStr, xPos, baselineY, paint)
                xPos += paint.measureText(fullTimeStr)

                paint.color = accentColor
                canvas.drawText(suffix, xPos, baselineY, paint)
            }

            DigitalStyleType.TYPO_ELEGANT_SLIM -> {
                paint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                paint.color = textColor
                paint.letterSpacing = 0.05f
                val xPos = getXForWidth(pos, width, paint.measureText(fullTimeStr))
                canvas.drawText(fullTimeStr, xPos, baselineY, paint)
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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hours = cal.get(Calendar.HOUR)
        val minutes = cal.get(Calendar.MINUTE)

        when (clockStyle) {
            AnalogStyleType.OXYGEN_NEVER_SETTLE -> {
                paint.style = Paint.Style.STROKE
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val isMain = (i % 3 == 0)
                    val inset = if (isMain) 0.78f else 0.85f
                    val startX = (cx + (radius * inset) * sin(angle)).toFloat()
                    val startY = (cy - (radius * inset) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()

                    paint.strokeWidth = if (isMain) radius * 0.055f else radius * 0.03f
                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }

            AnalogStyleType.BAUHAUS_MINIMAL -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = radius * 0.025f
                paint.color = Color.argb(120, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
                canvas.drawCircle(cx, cy, radius, paint)

                for (i in 0 until 4) {
                    val angle = Math.toRadians((i * 90).toDouble())
                    val startX = (cx + (radius * 0.82f) * sin(angle)).toFloat()
                    val startY = (cy - (radius * 0.82f) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()

                    paint.strokeWidth = radius * 0.045f
                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }

            AnalogStyleType.CHRONO_SPORT -> {
                paint.style = Paint.Style.STROKE
                for (i in 0 until 60) {
                    val angle = Math.toRadians((i * 6).toDouble())
                    val isHour = (i % 5 == 0)
                    val inset = if (isHour) 0.80f else 0.90f
                    val startX = (cx + (radius * inset) * sin(angle)).toFloat()
                    val startY = (cy - (radius * inset) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()

                    paint.strokeWidth = if (isHour) radius * 0.04f else radius * 0.018f
                    paint.color = when {
                        i == 0 -> accentColor
                            isHour -> textColor
                        else -> Color.argb(90, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
                    }
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }

            AnalogStyleType.ZEN_SPACE_DOTS -> {
                paint.style = Paint.Style.FILL
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val dotX = (cx + (radius * 0.88f) * sin(angle)).toFloat()
                    val dotY = (cy - (radius * 0.88f) * cos(angle)).toFloat()
                    val dotRadius = if (i % 3 == 0) radius * 0.045f else radius * 0.025f

                    paint.color = if (i == 0) accentColor else textColor
                    canvas.drawCircle(dotX, dotY, dotRadius, paint)
                }
            }

            AnalogStyleType.PILOT_AVIA -> {
                paint.style = Paint.Style.FILL
                paint.color = accentColor

                val triPath = Path().apply {
                    moveTo(cx, cy - radius)
                    lineTo(cx - radius * 0.12f, cy - radius * 0.82f)
                    lineTo(cx + radius * 0.12f, cy - radius * 0.82f)
                    close()
                }
                canvas.drawPath(triPath, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = radius * 0.035f
                paint.color = textColor
                for (i in listOf(3, 6, 9)) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val startX = (cx + (radius * 0.78f) * sin(angle)).toFloat()
                    val startY = (cy - (radius * 0.78f) * cos(angle)).toFloat()
                    val endX = (cx + radius * sin(angle)).toFloat()
                    val endY = (cy - radius * cos(angle)).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }
        }

        val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30).toDouble())
        val minuteAngle = Math.toRadians((minutes * 6).toDouble())

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = textColor
        paint.strokeWidth = radius * 0.085f
        canvas.drawLine(cx, cy, (cx + radius * 0.52f * sin(hourAngle)).toFloat(), (cy - radius * 0.52f * cos(hourAngle)).toFloat(), paint)

        paint.color = accentColor
        paint.strokeWidth = radius * 0.055f
        canvas.drawLine(cx, cy, (cx + radius * 0.80f * sin(minuteAngle)).toFloat(), (cy - radius * 0.80f * cos(minuteAngle)).toFloat(), paint)

        paint.style = Paint.Style.FILL
        paint.color = textColor
        canvas.drawCircle(cx, cy, radius * 0.09f, paint)

        paint.color = accentColor
        canvas.drawCircle(cx, cy, radius * 0.04f, paint)
    }

    private fun getXForWidth(pos: ClockPosition, width: Int, totalWidth: Float): Float {
        return when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> 40f
                ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (width - totalWidth) / 2f
                ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> width - totalWidth - 40f
        }
    }
}