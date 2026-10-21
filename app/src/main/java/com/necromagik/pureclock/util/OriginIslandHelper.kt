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

    private const val FLAG_PROMOTED_ONGOING = 0x00004000
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    fun canPostLiveUpdates(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return try {
            val method = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            method.invoke(nm) as? Boolean ?: true
        } catch (_: Exception) {
            true
        }
    }

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

    fun NotificationCompat.Builder.promoteToOriginIsland(
        chipText: String? = null,
        targetTimeMillis: Long? = null,
        isCountDown: Boolean = true
    ): NotificationCompat.Builder {
        this.setOngoing(true)
        this.setColorized(false)
        this.setGroupSummary(false)
        this.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        this.setShowWhen(false)
        this.setUsesChronometer(false)

        val extras = Bundle().apply {
            putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            putBoolean("vivo.summaryIconRes", true)
            putInt("notification.live.operation", 1)
            if (!chipText.isNullOrBlank()) {
                putString(EXTRA_SHORT_CRITICAL_TEXT, chipText.take(7))
            }
        }
        this.addExtras(extras)

        try {
            val reqMethod = NotificationCompat.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.javaPrimitiveType
            )
            reqMethod.invoke(this, true)
        } catch (_: Exception) {}

        if (!chipText.isNullOrBlank()) {
            try {
                val shortTextMethod = NotificationCompat.Builder::class.java.getMethod(
                    "setShortCriticalText",
                    CharSequence::class.java
                )
                shortTextMethod.invoke(this, chipText.take(7))
            } catch (_: Exception) {}
        }

        return this
    }

    fun Notification.attachOriginIslandFlags(
        chipText: String? = null,
        targetTimeMillis: Long? = null,
        isCountDown: Boolean = true
    ): Notification {
        this.flags = this.flags or Notification.FLAG_ONGOING_EVENT or FLAG_PROMOTED_ONGOING
        this.color = 0

        // Отключаем боковой дублирующий хронометр, так как живое время выводится крупно в заголовке
        this.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, false)
        this.extras.putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

        this.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        this.extras.putBoolean("vivo.summaryIconRes", true)
        this.extras.putInt("notification.live.operation", 1)
        this.extras.putBoolean(Notification.EXTRA_COLORIZED, false)

        if (!chipText.isNullOrBlank()) {
            this.extras.putString(EXTRA_SHORT_CRITICAL_TEXT, chipText.take(7))
        }

        return this
    }
}