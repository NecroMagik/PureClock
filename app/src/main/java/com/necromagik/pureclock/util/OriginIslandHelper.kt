package com.necromagik.pureclock.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

object OriginIslandHelper {

    private const val TAG = "PureClock_ISLAND"

    // Системный флаг Android 15/16 и OriginOS 5+ для активных живых уведомлений (Promoted Ongoing)
    const val FLAG_PROMOTED_ONGOING = 0x00004000
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /**
     * Проверяет, разрешено ли приложению отправлять живые уведомления в Остров.
     */
    fun canPostLiveUpdates(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return try {
            val method = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            method.invoke(nm) as? Boolean ?: true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Открывает системную страницу настроек живых уведомлений (Live Updates) приложения.
     */
    fun openLiveUpdatesSettings(context: Context) {
        try {
            val intent = Intent("android.settings.APP_PROMOTED_NOTIFICATIONS_SETTINGS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось открыть настройки уведомлений: ${e.message}")
            }
        }
    }

    /**
     * Элегантно пробрасывает уведомление в капсулу Динамического Острова OriginOS.
     *
     * @param chipText Короткий текст внутри «таблетки» острова (максимум 7 символов: "04:59", "07:30").
     * @param isAlert Если true — сигнал тревоги/будильника максимального приоритета; если false — фоновый таймер/секундомер.
     */
    fun NotificationCompat.Builder.applyOriginIsland(
        chipText: String? = null,
        isAlert: Boolean = false
    ): NotificationCompat.Builder {
        val cleanChip = chipText?.trim()?.take(7)

        this.setOngoing(true)
        this.setColorized(false)
        this.setGroupSummary(false)
        this.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        this.setShowWhen(false)
        this.setUsesChronometer(false)

        if (isAlert) {
            this.setPriority(NotificationCompat.PRIORITY_MAX)
            this.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        val islandExtras = Bundle().apply {
            putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            putBoolean("vivo.summaryIconRes", true)
            putInt("notification.live.operation", 1)
            putBoolean(Notification.EXTRA_COLORIZED, false)
            putBoolean(Notification.EXTRA_SHOW_WHEN, false)
            putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

            if (!cleanChip.isNullOrEmpty()) {
                putString(EXTRA_SHORT_CRITICAL_TEXT, cleanChip)
                putString("vivo.live.chipText", cleanChip)
            }
        }
        this.addExtras(islandExtras)

        // Поддержка стандартного API Android 15/16 через рефлексию
        try {
            val reqMethod = NotificationCompat.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.javaPrimitiveType
            )
            reqMethod.invoke(this, true)
        } catch (_: Exception) {}

        if (!cleanChip.isNullOrEmpty()) {
            try {
                val shortTextMethod = NotificationCompat.Builder::class.java.getMethod(
                    "setShortCriticalText",
                    CharSequence::class.java
                )
                shortTextMethod.invoke(this, cleanChip)
            } catch (_: Exception) {}
        }

        return this
    }

    /**
     * Дополнительный хэлпер для постобработки уже скомпилированного Notification-объекта.
     */
    fun Notification.attachOriginIslandFlags(
        chipText: String? = null
    ): Notification {
        this.flags = this.flags or Notification.FLAG_ONGOING_EVENT or FLAG_PROMOTED_ONGOING
        this.color = 0

        val cleanChip = chipText?.trim()?.take(7)

        this.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, false)
        this.extras.putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        this.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        this.extras.putBoolean("vivo.summaryIconRes", true)
        this.extras.putInt("notification.live.operation", 1)
        this.extras.putBoolean(Notification.EXTRA_COLORIZED, false)

        if (!cleanChip.isNullOrEmpty()) {
            this.extras.putString(EXTRA_SHORT_CRITICAL_TEXT, cleanChip)
            this.extras.putString("vivo.live.chipText", cleanChip)
        }

        return this
    }
}