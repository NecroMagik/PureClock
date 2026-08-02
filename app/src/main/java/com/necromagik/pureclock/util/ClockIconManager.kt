package com.necromagik.pureclock.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.alarm.AlarmReceiver
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

class ClockIconManager(private val context: Context) {

    private val iconSize = 512

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#121214".toColorInt()
        style = Paint.Style.FILL
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00E676".toColorInt()
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00E676".toColorInt()
        style = Paint.Style.FILL
    }

    /**
     * Рендеринг Bitmap циферблата под текущее время.
     */
    fun createClockIconBitmap(time: LocalTime = LocalTime.now()): Bitmap {
        val bitmap = createBitmap(iconSize, iconSize)
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

        // Часовая стрелка
        val hourRad = Math.toRadians(hourAngle.toDouble())
        val hourEndX = centerX + (hourHandLength * cos(hourRad)).toFloat()
        val hourEndY = centerY + (hourHandLength * sin(hourRad)).toFloat()
        canvas.drawLine(centerX, centerY, hourEndX, hourEndY, hourHandPaint)

        // Минутная стрелка
        val minuteRad = Math.toRadians(minuteAngle.toDouble())
        val minuteEndX = centerX + (minuteHandLength * cos(minuteRad)).toFloat()
        val minuteEndY = centerY + (minuteHandLength * sin(minuteRad)).toFloat()
        canvas.drawLine(centerX, centerY, minuteEndX, minuteEndY, minuteHandPaint)

        // Центральная точка
        canvas.drawCircle(centerX, centerY, 16f, centerDotPaint)

        return bitmap
    }

    /**
     * Обновление Dynamic Shortcut со свежим Bitmap часов.
     */
    fun updateDynamicShortcut() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val bitmap = createClockIconBitmap()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        }

        val shortcut = ShortcutInfo.Builder(context, "live_clock_shortcut")
            .setShortLabel("PureClock")
            .setLongLabel("PureClock Live")
            .setIcon(Icon.createWithBitmap(bitmap))
            .setIntent(intent)
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)

        // Рекурсивно взводим следующую минуту
        scheduleNextMinuteUpdate()
    }

    /**
     * Запланировать фоновое обновление Shortcut на начало следующей минуты.
     */
    fun scheduleNextMinuteUpdate() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_UPDATE_LIVE_ICON
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        val nextMinuteMillis = now + (60_000 - (now % 60_000))

        // Используем неточный вызов set(), который НЕ требует разрешения SCHEDULE_EXACT_ALARM
        // и не приводит к принудительному завершению процесса (SIG 9)
        alarmManager.set(
            AlarmManager.RTC,
            nextMinuteMillis,
            pendingIntent
        )
    }
}