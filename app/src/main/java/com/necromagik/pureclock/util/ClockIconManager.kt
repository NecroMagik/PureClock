package com.necromagik.pureclock.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Менеджер генерации и обновления динамической иконки часов.
 */
class ClockIconManager(private val context: Context) {

    private val iconSize = 512

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121214")
        style = Paint.Style.FILL
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    // ============================================================================
// СЕКЦИЯ 1: ПЕРЕКЛЮЧЕНИЕ ДИНАМИЧЕСКИХ ACTIVITY-ALIAS ИКОНОК
// ============================================================================
    fun setAppIconAlias(useAltIcon: Boolean) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        val defaultComponent = ComponentName(packageName, "$packageName.MainActivityAliasDefault")
        val altComponent = ComponentName(packageName, "$packageName.MainActivityAliasAlt")

        val targetToEnable = if (useAltIcon) altComponent else defaultComponent
        val targetToDisable = if (useAltIcon) defaultComponent else altComponent

        val currentState = packageManager.getComponentEnabledSetting(targetToEnable)
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return

        packageManager.setComponentEnabledSetting(
            targetToEnable,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        packageManager.setComponentEnabledSetting(
            targetToDisable,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    // ============================================================================
// СЕКЦИЯ 2: РЕНДЕРИНГ РАСТРОВОЙ ИКОНКИ С ТЕКУЩИМ ВРЕМЕНЕМ (BITMAP CANVAS)
// ============================================================================
    fun createClockIconBitmap(time: LocalTime = LocalTime.now()): Bitmap {
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = iconSize / 2f
        val centerY = iconSize / 2f
        val radius = iconSize / 2f

        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        val hours = time.hour % 12
        val minutes = time.minute

        val hourAngle = (hours * 30f) + (minutes * 0.5f) - 90f
        val minuteAngle = (minutes * 6f) - 90f

        val hourHandLength = radius * 0.45f
        val minuteHandLength = radius * 0.70f

        val hourRad = Math.toRadians(hourAngle.toDouble())
        val hourEndX = centerX + (hourHandLength * cos(hourRad)).toFloat()
        val hourEndY = centerY + (hourHandLength * sin(hourRad)).toFloat()
        canvas.drawLine(centerX, centerY, hourEndX, hourEndY, hourHandPaint)

        val minuteRad = Math.toRadians(minuteAngle.toDouble())
        val minuteEndX = centerX + (minuteHandLength * cos(minuteRad)).toFloat()
        val minuteEndY = centerY + (minuteHandLength * sin(minuteRad)).toFloat()
        canvas.drawLine(centerX, centerY, minuteEndX, minuteEndY, minuteHandPaint)

        canvas.drawCircle(centerX, centerY, 16f, centerDotPaint)

        return bitmap
    }
}