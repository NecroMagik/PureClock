package com.necromagik.pureclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.util.Log
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object WidgetRenderEngine {

    private const val TAG = "PureClock_WIDGET_DEBUG"

    fun buildCustomRemoteViews(
        context: Context,
        config: WidgetConfig,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        appWidgetManager: AppWidgetManager? = null
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_pure_clock)

        val options: Bundle? = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && appWidgetManager != null) {
            appWidgetManager.getAppWidgetOptions(appWidgetId)
        } else null

        val density = context.resources.displayMetrics.density
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: 320
        val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)?.takeIf { it > 0 } ?: 160

        val targetWidthPx = max(400, (minWidthDp * density * 1.25f).toInt())
        val targetHeightPx = max(200, (minHeightDp * density * 1.25f).toInt())

        val bitmap = renderCustomWidgetBitmap(context, config, targetWidthPx, targetHeightPx)
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

    fun renderCustomWidgetBitmap(
        context: Context,
        config: WidgetConfig,
        canvasWidth: Int = 1080,
        canvasHeight: Int = 480
    ): Bitmap {
        val t0 = System.currentTimeMillis()
        val bitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
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

        val borderWidth = config.borderWidthDp.toFloat()
        val halfStroke = borderWidth / 2f
        val strokeInset = halfStroke + 4f

        // Полноразмерный прямоугольник на весь холст виджета
        val fullRect = RectF(strokeInset, strokeInset, canvasWidth - strokeInset, canvasHeight - strokeInset)
        val radius = min(config.cornerRadiusDp.toFloat(), min(fullRect.width(), fullRect.height()) / 2f)

        // 1. Отрисовка фона на ВСЁ пространство виджета
        if (config.showBackground) {
            val bgAlphaInt = (config.backgroundAlpha * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(bgAlphaInt, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(fullRect, radius, radius, paint)
        }

        // 2. Отрисовка рамки по всему периметру виджета
        if (config.showBorder) {
            val borderRect = RectF(fullRect)
            val borderCornerRadius = (radius - halfStroke).coerceAtLeast(0f)

            if (config.enableBorderGlow) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth + 8f
                    color = Color.argb(90, Color.red(effectiveAccentColor), Color.green(effectiveAccentColor), Color.blue(effectiveAccentColor))
                    maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, glowPaint)
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                color = effectiveAccentColor
            }

            when (config.borderStyle) {
                BorderStyle.DASHED -> {
                    borderPaint.pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
                    canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, borderPaint)
                }
                BorderStyle.DOUBLE_LINE -> {
                    canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, borderPaint)
                    val doubleInset = borderWidth + 4f
                    val innerRect = RectF(
                        borderRect.left + doubleInset,
                    borderRect.top + doubleInset,
                    borderRect.right - doubleInset,
                    borderRect.bottom - doubleInset
                    )
                    borderPaint.strokeWidth = (borderWidth * 0.6f).coerceAtLeast(1.5f)
                    canvas.drawRoundRect(
                        innerRect,
                        (borderCornerRadius - doubleInset).coerceAtLeast(0f),
                    (borderCornerRadius - doubleInset).coerceAtLeast(0f),
                    borderPaint
                    )
                }
                BorderStyle.SOLID -> {
                    canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, borderPaint)
                }
            }
        }

        // 3. Вычисление размеров контента
        val cal = Calendar.getInstance()
        val pos = config.safePosition

        val hoursStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
        val minsStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)
        val fullTimeStr = "$hoursStr:$minsStr"
        val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(cal.time).replaceFirstChar { it.uppercase() }

        var timeSize = config.timeFontSizeSp.toFloat()
        var dateSize = config.dateFontSizeSp.toFloat()

        val paddingInside = max(24f, borderWidth + 16f)
        val maxAvailableW = canvasWidth - (paddingInside * 2f)
        val maxAvailableH = canvasHeight - (paddingInside * 2f)

        // Мягкая автоподгонка, если текст превышает физический размер всего холста
        val testTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = timeSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val testDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dateSize
            typeface = if (config.isDateBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val estimatedW = max(
            testTimePaint.measureText(fullTimeStr),
            if (config.showDate) testDatePaint.measureText(dateStr) else 0f
        )
        val estimatedH = timeSize * (if (config.safeDisplayMode == ClockDisplayMode.ANALOG) 2.0f else 1.0f) +
                (if (config.showDate) dateSize * 1.2f + 12f else 0f)

        if (estimatedW > maxAvailableW || estimatedH > maxAvailableH) {
            val scale = min(maxAvailableW / estimatedW, maxAvailableH / estimatedH)
            timeSize *= scale
            dateSize *= scale
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = timeSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dateSize
            typeface = if (config.isDateBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        var timeContentWidth: Float
        var timeContentHeight: Float

        if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
            timeContentHeight = timeSize * 2.0f
            timeContentWidth = timeContentHeight
        } else when (config.safeDigitalStyle) {
            DigitalStyleType.STACK_TWO_LINE -> {
                timeContentWidth = max(timePaint.measureText(hoursStr), timePaint.measureText(minsStr))
                timeContentHeight = timeSize * 1.65f
            }
            DigitalStyleType.NOTHING_DOT_MATRIX -> {
                val dotPitch = timeSize * 0.16f
                val charSpacing = dotPitch * 1.4f
                var w = 0f
                fullTimeStr.forEachIndexed { i, c ->
                    val matrix = DIGIT_MATRICES[c]
                    val cols = matrix?.get(0)?.length ?: 5
                    w += cols * dotPitch
                    if (i < fullTimeStr.length - 1) w += charSpacing
                }
                timeContentWidth = w
                timeContentHeight = 7 * dotPitch
            }
            else -> {
                timeContentWidth = timePaint.measureText(fullTimeStr)
                val fm = timePaint.fontMetrics
                timeContentHeight = fm.descent - fm.ascent
            }
        }

        val dateContentWidth = if (config.showDate) datePaint.measureText(dateStr) else 0f
        val dateFm = datePaint.fontMetrics
        val dateContentHeight = if (config.showDate) (dateFm.descent - dateFm.ascent) else 0f

        val itemSpacing = if (config.showDate) max(6f, 12f * (timeSize / 100f)) else 0f
        val totalBlockHeight = if (config.showDate) timeContentHeight + itemSpacing + dateContentHeight else timeContentHeight

        // Позиционирование 3×3 внутри полноразмерного виджета
        var currentTopY = when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT -> paddingInside
            ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT -> (canvasHeight - totalBlockHeight) / 2f
            ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT -> canvasHeight - totalBlockHeight - paddingInside
        }

        val activeElements = config.safeElementOrder.filter {
            when (it) {
                WidgetElementType.TIME -> true
                WidgetElementType.DATE -> config.showDate
            }
        }

        activeElements.forEach { element ->
            when (element) {
                WidgetElementType.TIME -> {
                    val drawX = when (pos) {
                        ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> paddingInside
                        ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (canvasWidth - timeContentWidth) / 2f
                        ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> canvasWidth - timeContentWidth - paddingInside
                    }

                    if (config.safeDisplayMode == ClockDisplayMode.ANALOG) {
                        val analogRadius = timeContentHeight / 2f
                        val centerX = drawX + analogRadius
                        val centerY = currentTopY + analogRadius
                        drawAnalogClock(canvas, centerX, centerY, analogRadius * 0.92f, cal, config.safeAnalogStyle, effectiveAccentColor, primaryTextColor)
                    } else {
                        val fm = timePaint.fontMetrics
                        val baselineY = currentTopY - fm.ascent

                        drawDynamicDigitalClock(
                            canvas,
                            fullTimeStr,
                            hoursStr,
                            minsStr,
                            drawX,
                            baselineY,
                            currentTopY,
                            timeSize,
                            timePaint,
                            config,
                            effectiveAccentColor,
                            primaryTextColor
                        )
                    }
                    currentTopY += timeContentHeight + itemSpacing
                }
                WidgetElementType.DATE -> {
                    datePaint.color = effectiveAccentColor
                    val dateFmMetrics = datePaint.fontMetrics
                    val dateBaselineY = currentTopY - dateFmMetrics.ascent

                    val dateDrawX = when (pos) {
                        ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> paddingInside
                        ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (canvasWidth - dateContentWidth) / 2f
                        ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> canvasWidth - dateContentWidth - paddingInside
                    }

                    canvas.drawText(dateStr, dateDrawX, dateBaselineY, datePaint)
                    currentTopY += dateContentHeight + itemSpacing
                }
            }
        }

        val totalMs = System.currentTimeMillis() - t0
        Log.v(TAG, "------> [Bitmap] Полное заполнение виджета (${canvasWidth}x${canvasHeight}) за ${totalMs}мс")
        return bitmap
    }

    private fun drawDynamicDigitalClock(
        canvas: Canvas,
        fullTimeStr: String,
        hoursStr: String,
        minsStr: String,
        startX: Float,
        baselineY: Float,
        topY: Float,
        fontSizeSp: Float,
        paint: Paint,
        config: WidgetConfig,
        accentColor: Int,
        textColor: Int
    ) {
        when (config.safeDigitalStyle) {
            DigitalStyleType.OXYGEN_BOLD_FLUID -> {
                val firstChar = fullTimeStr.substring(0, 1)
                val restChars = fullTimeStr.substring(1)
                var currentX = startX

                paint.color = accentColor
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText(firstChar, currentX, baselineY, paint)

                currentX += paint.measureText(firstChar)
                paint.color = textColor
                canvas.drawText(restChars, currentX, baselineY, paint)
            }

            DigitalStyleType.STACK_TWO_LINE -> {
                val lineH = fontSizeSp * 0.85f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

                val fm = paint.fontMetrics
                val firstBaseY = topY - fm.ascent
                val secondBaseY = firstBaseY + lineH

                paint.color = accentColor
                canvas.drawText(hoursStr, startX, firstBaseY, paint)

                paint.color = textColor
                canvas.drawText(minsStr, startX, secondBaseY, paint)
            }

            DigitalStyleType.NOTHING_DOT_MATRIX -> {
                drawDotMatrixTimeDirect(canvas, fullTimeStr, startX, topY, fontSizeSp, accentColor, textColor)
            }

            DigitalStyleType.CYBER_CONSOLE -> {
                paint.typeface = Typeface.MONOSPACE
                val prefix = "> "
                val suffix = "_"
                var currentX = startX

                paint.color = accentColor
                canvas.drawText(prefix, currentX, baselineY, paint)
                currentX += paint.measureText(prefix)

                paint.color = textColor
                canvas.drawText(fullTimeStr, currentX, baselineY, paint)
                currentX += paint.measureText(fullTimeStr)

                paint.color = accentColor
                canvas.drawText(suffix, currentX, baselineY, paint)
            }

            DigitalStyleType.TYPO_ELEGANT_SLIM -> {
                paint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                paint.color = textColor
                paint.letterSpacing = 0.05f
                canvas.drawText(fullTimeStr, startX, baselineY, paint)
            }
        }
    }

    private val DIGIT_MATRICES = mapOf(
        '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to arrayOf("00", "10", "10", "00", "10", "10", "00")
    )

    private fun drawDotMatrixTimeDirect(
        canvas: Canvas,
        timeStr: String,
        startX: Float,
        topY: Float,
        fontSizeSp: Float,
        accentColor: Int,
        textColor: Int
    ) {
        val dotPitch = fontSizeSp * 0.16f
        val dotRadius = dotPitch * 0.40f
        val charSpacing = dotPitch * 1.4f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        var currentX = startX
        timeStr.forEachIndexed { charIndex, char ->
            val matrix = DIGIT_MATRICES[char] ?: return@forEachIndexed
            val cols = matrix[0].length

            for (row in 0 until 7) {
                for (col in 0 until cols) {
                    val isActive = matrix[row][col] == '1'
                    val dotCenterX = currentX + col * dotPitch + dotRadius
                    val dotCenterY = topY + row * dotPitch + dotRadius

                    if (isActive) {
                        paint.color = if (charIndex == 0) accentColor else textColor
                        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, paint)
                    } else {
                        paint.color = Color.argb(20, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
                        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius * 0.5f, paint)
                    }
                }
            }
            currentX += cols * dotPitch + charSpacing
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
}