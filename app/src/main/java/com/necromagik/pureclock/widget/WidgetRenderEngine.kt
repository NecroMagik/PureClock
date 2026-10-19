package com.necromagik.pureclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
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

    /**
     * Генерирует глубокий контрастный оттенок фона из акцентного цвета (как в виджетах Яндекс / Material You)
     */
    fun deriveDeepContrastSurfaceColor(baseAccentColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseAccentColor, hsv)
        // Если цвет почти серый/белый/чёрный (низкая насыщенность) — возвращаем благородный графитовый
        if (hsv[1] < 0.12f) {
            return Color.rgb(18, 20, 24)
        }
        // Сохраняем тон (Hue), делаем глубокую насыщенность (0.82) и тёмную яркость (0.23)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.65f, 0.88f)
        hsv[2] = 0.22f
        return Color.HSVToColor(hsv)
    }

    /**
     * Делает акцентный цвет чуть светлее и сочнее, чтобы он идеально читался на тёмном контрастном фоне
     */
    private fun deriveReadableAccentOnContrast(baseAccentColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseAccentColor, hsv)
        if (hsv[1] < 0.10f) return Color.WHITE
        hsv[1] = hsv[1].coerceIn(0.45f, 0.78f)
        hsv[2] = hsv[2].coerceAtLeast(0.88f)
        return Color.HSVToColor(hsv)
    }

    private fun parseColorSafe(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            hex.trim().toColorInt()
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Накладывает тень для высокой читаемости текста на любых обоях
     */
    private fun applyTextShadowIfNeeded(paint: Paint, config: WidgetConfig, density: Float) {
        if (config.enableTextShadow && config.textShadowIntensity > 0.02f) {
            val baseShadowColor = parseColorSafe(config.textShadowColorHex, Color.BLACK)
            val shadowAlpha = (config.textShadowIntensity.coerceIn(0f, 1f) * 235).toInt()
            val shadowColorWithAlpha = Color.argb(
                shadowAlpha,
                Color.red(baseShadowColor),
                Color.green(baseShadowColor),
                Color.blue(baseShadowColor)
            )
            val radiusPx = (config.textShadowRadiusDp * density).coerceAtLeast(1f)
            val dyPx = max(1.5f, radiusPx * 0.22f)
            paint.setShadowLayer(radiusPx, 0f, dyPx, shadowColorWithAlpha)
        } else {
            paint.clearShadowLayer()
        }
    }

    /**
     * Вычисляет массив из 5 цветов для символов времени: [Ч1, Ч2, ':', М1, М2]
     */
    private fun resolveTimeCharColors(
        config: WidgetConfig,
        effectiveAccentColor: Int,
        primaryTextColor: Int
    ): IntArray {
        val customAccent = config.timeAccentColorHex?.let { parseColorSafe(it, effectiveAccentColor) }
            ?: effectiveAccentColor

        return when (config.safeTimeColoringMode) {
            TimeColoringMode.FIRST_DIGIT_ACCENT -> intArrayOf(
                customAccent,
                primaryTextColor,
                primaryTextColor,
                primaryTextColor,
                primaryTextColor
            )
            TimeColoringMode.SOLID_ALL -> intArrayOf(
                primaryTextColor,
                primaryTextColor,
                primaryTextColor,
                primaryTextColor,
                primaryTextColor
            )
            TimeColoringMode.HOURS_ACCENT -> intArrayOf(
                customAccent,
                customAccent,
                Color.argb(180, Color.red(primaryTextColor), Color.green(primaryTextColor), Color.blue(primaryTextColor)),
                primaryTextColor,
                primaryTextColor
            )
            TimeColoringMode.PER_DIGIT_CUSTOM -> {
                val list = config.safeDigitColors
                IntArray(5) { idx ->
                    parseColorSafe(list.getOrNull(idx), primaryTextColor)
                }
            }
        }
    }

    fun renderCustomWidgetBitmap(
        context: Context,
        config: WidgetConfig,
        canvasWidth: Int = 1080,
        canvasHeight: Int = 480
    ): Bitmap {
        val t0 = System.currentTimeMillis()
        val density = context.resources.displayMetrics.density
        val bitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val settings = SettingsManager.getInstance(context)
        val themeState = settings.themeState.value

        // Системный цвет Monet или цвет акцента приложения
        val systemAccentInt = parseColorSafe(SystemThemeUtils.getSystemAccentHex(context), Color.parseColor("#00E676"))
        val appAccentInt = settings.getAccentColor(themeState.accentColorHex).toArgb()

        val rawBaseAccent = if (config.useAppTheme) appAccentInt else systemAccentInt
        val effectiveAccentColor = if (config.safeBackgroundMode == WidgetBackgroundMode.SYSTEM_CONTRAST && config.showBackground) {
            deriveReadableAccentOnContrast(rawBaseAccent)
        } else {
            rawBaseAccent
        }

        val primaryTextColor = parseColorSafe(config.timeColorHex, Color.WHITE)

        // Вычисляем цвет фона в зависимости от выбранного режима подложки
        val bgColor = when (config.safeBackgroundMode) {
            WidgetBackgroundMode.SYSTEM_CONTRAST -> deriveDeepContrastSurfaceColor(rawBaseAccent)
            WidgetBackgroundMode.APP_THEME -> {
                if (themeState.isPureMonocolor) Color.BLACK else Color.parseColor("#14161D")
            }
            WidgetBackgroundMode.PURE_BLACK -> Color.BLACK
            WidgetBackgroundMode.CUSTOM_COLOR -> parseColorSafe(config.backgroundColorHex, Color.parseColor("#0B0B0B"))
        }

        val borderWidth = if (config.showBorder) config.borderWidthDp.toFloat() * (canvasWidth / 400f).coerceIn(1f, 2.2f) else 0f
        val halfStroke = borderWidth / 2f
        val strokeInset = halfStroke + 4f

        val fullRect = RectF(strokeInset, strokeInset, canvasWidth - strokeInset, canvasHeight - strokeInset)
        val scaledRadius = config.cornerRadiusDp.toFloat() * (min(canvasWidth, canvasHeight) / 220f).coerceIn(1f, 2.5f)
        val radius = min(scaledRadius, min(fullRect.width(), fullRect.height()) / 2f)

        // 1. Отрисовка фона на ВСЁ пространство виджета
        if (config.showBackground && config.backgroundAlpha > 0.01f) {
            val bgAlphaInt = (config.backgroundAlpha * 255).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.FILL

            if (config.safeBackgroundMode == WidgetBackgroundMode.SYSTEM_CONTRAST) {
                // Мягкий объёмный градиент для контрастной системной подложки (как в фирменных виджетах)
                val topColor = Color.argb(
                    bgAlphaInt,
                    (Color.red(bgColor) * 1.15f).toInt().coerceAtMost(255),
                    (Color.green(bgColor) * 1.15f).toInt().coerceAtMost(255),
                    (Color.blue(bgColor) * 1.15f).toInt().coerceAtMost(255)
                )
                val bottomColor = Color.argb(
                    bgAlphaInt,
                    (Color.red(bgColor) * 0.85f).toInt(),
                    (Color.green(bgColor) * 0.85f).toInt(),
                    (Color.blue(bgColor) * 0.85f).toInt()
                )
                paint.shader = LinearGradient(
                    0f, fullRect.top, 0f, fullRect.bottom,
                    topColor, bottomColor, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(fullRect, radius, radius, paint)
                paint.shader = null
            } else {
                paint.color = Color.argb(bgAlphaInt, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
                canvas.drawRoundRect(fullRect, radius, radius, paint)
            }
        }

        // 2. Отрисовка рамки по периметру виджета
        if (config.showBorder && borderWidth > 0f) {
            val borderRect = RectF(fullRect)
            val borderCornerRadius = (radius - halfStroke).coerceAtLeast(0f)
            val resolvedBorderColor = if (config.useAppTheme) {
                effectiveAccentColor
            } else {
                parseColorSafe(config.borderColorHex, effectiveAccentColor)
            }

            if (config.enableBorderGlow) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth + 8f
                    color = Color.argb(
                        95,
                        Color.red(resolvedBorderColor),
                        Color.green(resolvedBorderColor),
                        Color.blue(resolvedBorderColor)
                    )
                    maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, glowPaint)
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                color = resolvedBorderColor
            }

            when (config.safeBorderStyle) {
                BorderStyle.DASHED -> {
                    borderPaint.pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
                    canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, borderPaint)
                }
                BorderStyle.DOUBLE_LINE -> {
                    canvas.drawRoundRect(borderRect, borderCornerRadius, borderCornerRadius, borderPaint)
                    val doubleInset = borderWidth + 5f
                    val innerRect = RectF(
                        borderRect.left + doubleInset,
                        borderRect.top + doubleInset,
                        borderRect.right - doubleInset,
                        borderRect.bottom - doubleInset
                    )
                    borderPaint.strokeWidth = (borderWidth * 0.55f).coerceAtLeast(1.5f)
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

        // 3. Подготовка строк времени и даты
        val cal = Calendar.getInstance()
        val pos = config.safePosition

        val hoursStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
        val minsStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)
        val fullTimeStr = "$hoursStr:$minsStr"

        val dateStyle = config.safeDateStyle
        val rawDateStr = try {
            SimpleDateFormat(dateStyle.pattern, Locale.forLanguageTag("ru")).format(cal.time)
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(cal.time)
        }
        val dateStr = if (dateStyle.uppercase) {
            rawDateStr.uppercase(Locale.forLanguageTag("ru"))
        } else {
            rawDateStr.replaceFirstChar { it.uppercase() }
        }

        var timeSize = config.timeFontSizeSp.toFloat() * 1.35f
        var dateSize = config.dateFontSizeSp.toFloat() * 1.25f

        val paddingInside = max(28f, borderWidth + 22f)
        val maxAvailableW = canvasWidth - (paddingInside * 2f)
        val maxAvailableH = canvasHeight - (paddingInside * 2f)

        val testTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = timeSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val testDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dateSize
            typeface = if (config.isDateBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val timeMeasureCandidate = if (config.safeDigitalStyle == DigitalStyleType.CYBER_CONSOLE) "> $fullTimeStr _" else fullTimeStr
        val dateBadgeExtraW = if (dateStyle == DateStyleType.PILL_BADGE) dateSize * 1.4f else 0f

        val estimatedW = max(
            testTimePaint.measureText(timeMeasureCandidate),
            if (config.showDate) testDatePaint.measureText(dateStr) + dateBadgeExtraW else 0f
        )
        val estimatedH = timeSize * (if (config.safeDisplayMode == ClockDisplayMode.ANALOG) 2.0f else if (config.safeDigitalStyle == DigitalStyleType.STACK_TWO_LINE) 1.75f else 1.1f) +
                (if (config.showDate) dateSize * 1.45f + 16f else 0f)

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
                timeContentHeight = timeSize * 1.68f
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
            DigitalStyleType.CYBER_CONSOLE -> {
                timePaint.typeface = Typeface.MONOSPACE
                timeContentWidth = timePaint.measureText("> ${fullTimeStr}_")
                val fm = timePaint.fontMetrics
                timeContentHeight = fm.descent - fm.ascent
            }
            else -> {
                timeContentWidth = timePaint.measureText(fullTimeStr)
                val fm = timePaint.fontMetrics
                timeContentHeight = fm.descent - fm.ascent
            }
        }

        val dateTextW = if (config.showDate) datePaint.measureText(dateStr) else 0f
        val dateFm = datePaint.fontMetrics
        val dateTextH = if (config.showDate) (dateFm.descent - dateFm.ascent) else 0f
        val isPillDate = config.showDate && dateStyle == DateStyleType.PILL_BADGE
        val pillPadX = if (isPillDate) dateSize * 0.65f else 0f
        val pillPadY = if (isPillDate) dateSize * 0.28f else 0f

        val dateContentWidth = dateTextW + pillPadX * 2f
        val dateContentHeight = dateTextH + pillPadY * 2f

        val itemSpacing = if (config.showDate) max(8f, 14f * (timeSize / 100f)) else 0f
        val totalBlockHeight = if (config.showDate) timeContentHeight + itemSpacing + dateContentHeight else timeContentHeight

        var currentTopY = when (pos) {
            ClockPosition.TOP_LEFT, ClockPosition.TOP_CENTER, ClockPosition.TOP_RIGHT -> paddingInside
            ClockPosition.CENTER_LEFT, ClockPosition.CENTER, ClockPosition.CENTER_RIGHT -> (canvasHeight - totalBlockHeight) / 2f
            ClockPosition.BOTTOM_LEFT, ClockPosition.BOTTOM_CENTER, ClockPosition.BOTTOM_RIGHT -> canvasHeight - totalBlockHeight - paddingInside
        }

        val charColors = resolveTimeCharColors(config, effectiveAccentColor, primaryTextColor)
        val resolvedDateColor = if (config.useSystemAccentForDate) {
            effectiveAccentColor
        } else {
            parseColorSafe(config.dateColorHex, effectiveAccentColor)
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
                        drawAnalogClock(
                            canvas = canvas,
                            cx = centerX,
                            cy = centerY,
                            radius = analogRadius * 0.92f,
                            cal = cal,
                            clockStyle = config.safeAnalogStyle,
                            accentColor = charColors[0],
                            textColor = primaryTextColor,
                            config = config,
                            density = density
                        )
                    } else {
                        val fm = timePaint.fontMetrics
                        val baselineY = currentTopY - fm.ascent

                        drawDynamicDigitalClock(
                            canvas = canvas,
                            fullTimeStr = fullTimeStr,
                            hoursStr = hoursStr,
                            minsStr = minsStr,
                            startX = drawX,
                            baselineY = baselineY,
                            topY = currentTopY,
                            fontSizeSp = timeSize,
                            paint = timePaint,
                            config = config,
                            charColors = charColors,
                            accentColor = effectiveAccentColor,
                            density = density
                        )
                    }
                    currentTopY += timeContentHeight + itemSpacing
                }

                WidgetElementType.DATE -> {
                    val dateDrawX = when (pos) {
                        ClockPosition.TOP_LEFT, ClockPosition.CENTER_LEFT, ClockPosition.BOTTOM_LEFT -> paddingInside
                        ClockPosition.TOP_CENTER, ClockPosition.CENTER, ClockPosition.BOTTOM_CENTER -> (canvasWidth - dateContentWidth) / 2f
                        ClockPosition.TOP_RIGHT, ClockPosition.CENTER_RIGHT, ClockPosition.BOTTOM_RIGHT -> canvasWidth - dateContentWidth - paddingInside
                    }

                    if (isPillDate) {
                        val pillRect = RectF(
                            dateDrawX,
                            currentTopY,
                            dateDrawX + dateContentWidth,
                            currentTopY + dateContentHeight
                        )
                        val pillRadius = dateContentHeight / 2f
                        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = Color.argb(
                                42,
                                Color.red(resolvedDateColor),
                                Color.green(resolvedDateColor),
                                Color.blue(resolvedDateColor)
                            )
                        }
                        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)

                        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = max(1.5f, dateSize * 0.06f)
                            color = Color.argb(
                                110,
                                Color.red(resolvedDateColor),
                                Color.green(resolvedDateColor),
                                Color.blue(resolvedDateColor)
                            )
                        }
                        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillStrokePaint)
                    }

                    applyTextShadowIfNeeded(datePaint, config, density)
                    datePaint.color = resolvedDateColor
                    val dateFmMetrics = datePaint.fontMetrics
                    val dateBaselineY = currentTopY + pillPadY - dateFmMetrics.ascent

                    canvas.drawText(dateStr, dateDrawX + pillPadX, dateBaselineY, datePaint)
                    datePaint.clearShadowLayer()

                    currentTopY += dateContentHeight + itemSpacing
                }
            }
        }

        val totalMs = System.currentTimeMillis() - t0
        Log.v(TAG, "------> [Bitmap] Рендер виджета (${canvasWidth}x${canvasHeight}) за ${totalMs}мс")
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
        charColors: IntArray,
        accentColor: Int,
        density: Float
    ) {
        applyTextShadowIfNeeded(paint, config, density)

        when (config.safeDigitalStyle) {
            DigitalStyleType.OXYGEN_BOLD_FLUID -> {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.letterSpacing = 0f
                drawPerCharacterLine(canvas, fullTimeStr, startX, baselineY, paint, charColors)
            }

            DigitalStyleType.CLASSIC_ROUNDED -> {
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                paint.letterSpacing = 0.02f
                drawPerCharacterLine(canvas, fullTimeStr, startX, baselineY, paint, charColors)
            }

            DigitalStyleType.TYPO_ELEGANT_SLIM -> {
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                paint.letterSpacing = 0.05f
                drawPerCharacterLine(canvas, fullTimeStr, startX, baselineY, paint, charColors)
            }

            DigitalStyleType.STACK_TWO_LINE -> {
                val lineH = fontSizeSp * 0.85f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.letterSpacing = 0f

                val fm = paint.fontMetrics
                val firstBaseY = topY - fm.ascent
                val secondBaseY = firstBaseY + lineH

                // Первая строка: часы [Ч1, Ч2]
                var hx = startX
                hoursStr.forEachIndexed { idx, ch ->
                    val s = ch.toString()
                    paint.color = charColors.getOrElse(idx) { Color.WHITE }
                    canvas.drawText(s, hx, firstBaseY, paint)
                    hx += paint.measureText(s)
                }

                // Вторая строка: минуты [М1, М2] (индексы 3 и 4 в массиве charColors)
                var mx = startX
                minsStr.forEachIndexed { idx, ch ->
                    val s = ch.toString()
                    paint.color = charColors.getOrElse(idx + 3) { Color.WHITE }
                    canvas.drawText(s, mx, secondBaseY, paint)
                    mx += paint.measureText(s)
                }
            }

            DigitalStyleType.NOTHING_DOT_MATRIX -> {
                paint.clearShadowLayer()
                drawDotMatrixTimeDirect(
                    canvas = canvas,
                    timeStr = fullTimeStr,
                    startX = startX,
                    topY = topY,
                    fontSizeSp = fontSizeSp,
                    charColors = charColors,
                    config = config,
                    density = density
                )
            }

            DigitalStyleType.CYBER_CONSOLE -> {
                paint.typeface = Typeface.MONOSPACE
                paint.letterSpacing = 0f
                val prefix = "> "
                val suffix = "_"
                var currentX = startX

                paint.color = accentColor
                canvas.drawText(prefix, currentX, baselineY, paint)
                currentX += paint.measureText(prefix)

                fullTimeStr.forEachIndexed { idx, ch ->
                    val s = ch.toString()
                    paint.color = charColors.getOrElse(idx) { Color.WHITE }
                    canvas.drawText(s, currentX, baselineY, paint)
                    currentX += paint.measureText(s)
                }

                paint.color = accentColor
                canvas.drawText(suffix, currentX, baselineY, paint)
            }
        }

        paint.clearShadowLayer()
    }

    /**
     * Рисует строку времени посимвольно, окрашивая каждую цифру в её персональный цвет из charColors
     */
    private fun drawPerCharacterLine(
        canvas: Canvas,
        fullTimeStr: String,
        startX: Float,
        baselineY: Float,
        paint: Paint,
        charColors: IntArray
    ) {
        var currentX = startX
        fullTimeStr.forEachIndexed { idx, ch ->
            val charStr = ch.toString()
            paint.color = charColors.getOrElse(idx) { Color.WHITE }
            canvas.drawText(charStr, currentX, baselineY, paint)
            currentX += paint.measureText(charStr)
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
        charColors: IntArray,
        config: WidgetConfig,
        density: Float
    ) {
        val dotPitch = fontSizeSp * 0.16f
        val dotRadius = dotPitch * 0.40f
        val charSpacing = dotPitch * 1.4f
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        applyTextShadowIfNeeded(activePaint, config, density)

        var currentX = startX
        timeStr.forEachIndexed { charIndex, char ->
            val matrix = DIGIT_MATRICES[char] ?: return@forEachIndexed
            val cols = matrix[0].length
            val charColor = charColors.getOrElse(charIndex) { Color.WHITE }

            for (row in 0 until 7) {
                for (col in 0 until cols) {
                    val isActive = matrix[row][col] == '1'
                    val dotCenterX = currentX + col * dotPitch + dotRadius
                    val dotCenterY = topY + row * dotPitch + dotRadius

                    if (isActive) {
                        activePaint.color = charColor
                        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, activePaint)
                    } else {
                        inactivePaint.color = Color.argb(24, Color.red(charColor), Color.green(charColor), Color.blue(charColor))
                        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius * 0.5f, inactivePaint)
                    }
                }
            }
            currentX += cols * dotPitch + charSpacing
        }
        activePaint.clearShadowLayer()
    }

    private fun drawAnalogClock(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        cal: Calendar,
        clockStyle: AnalogStyleType,
        accentColor: Int,
        textColor: Int,
        config: WidgetConfig,
        density: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        applyTextShadowIfNeeded(paint, config, density)

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
        paint.clearShadowLayer()
    }
}