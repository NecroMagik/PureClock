package com.necromagik.pureclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.necromagik.pureclock.MainActivity
import com.necromagik.pureclock.data.repository.WidgetConfigRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PureClockWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.i(TAG, "==> [onEnabled] Первый виджет добавлен. Запуск планировщика тиков...")
        refreshAllActiveWidgets(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val now = System.currentTimeMillis()
        Log.d(TAG, "==> [onUpdate] Вызов onUpdate() в ${formatTime(now)}. IDs: ${appWidgetIds.joinToString()}")

        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWidgetUpdates(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "==> [onAppWidgetOptionsChanged] Изменён размер виджета ID=$appWidgetId")
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i(TAG, "==> [onReceive] Получен интент [$action] в ${formatTime(System.currentTimeMillis())}")

        when (action) {
            ACTION_WIDGET_UPDATE_TICK,
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_FOREGROUND,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                refreshAllActiveWidgets(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.w(TAG, "==> [onDeleted] Удалены виджеты: ${appWidgetIds.joinToString()}")
        val repo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            repo.deleteConfig(widgetId)
        }
        val activeIds = getActiveWidgetIds(context)
        if (activeIds.isEmpty()) {
            cancelWidgetUpdates(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.w(TAG, "==> [onDisabled] Все виджеты удалены. Остановка планировщика...")
        cancelWidgetUpdates(context)
    }

    companion object {
        const val TAG = "PureClock_WIDGET_DEBUG"
        const val ACTION_WIDGET_UPDATE_TICK = "com.necromagik.pureclock.ACTION_WIDGET_UPDATE_TICK"
        private const val WIDGET_ALARM_REQUEST_CODE = 4004
        private const val WIDGET_SHOW_REQUEST_CODE = 4005

        private fun formatTime(millis: Long): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
            return sdf.format(Date(millis))
        }

        /**
         * Возвращает только РЕАЛЬНЫЕ виджеты, размещённые на сетке рабочего стола.
         * Невидимые фантомы лаунчера (у которых не заданы габариты ячейки minWidth/minHeight) отсекаются.
         */
        fun getActiveWidgetIds(context: Context, includeUnboundGhosts: Boolean = false): IntArray {
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val providerComponent = ComponentName(context, PureClockWidgetProvider::class.java)
                val rawIds = appWidgetManager.getAppWidgetIds(providerComponent) ?: IntArray(0)

                rawIds.filter { id ->
                    if (id <= AppWidgetManager.INVALID_APPWIDGET_ID) return@filter false
                    if (includeUnboundGhosts) return@filter true

                    val options = try {
                        appWidgetManager.getAppWidgetOptions(id)
                    } catch (_: Exception) {
                        null
                    }
                    val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
                    val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

                    // Реальный виджет на рабочем столе всегда имеет габариты ячейки от лаунчера (> 0)
                    minW > 0 && minH > 0
                }.distinct().toIntArray()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения списка виджетов: ${e.message}")
                IntArray(0)
            }
        }

        /**
         * Освобождает память RemoteViews у зависших фантомных виджетов лаунчера (views_bitmap_memory -> 0)
         */
        fun purgeGhostWidgetsMemory(context: Context): Int {
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val allRawIds = getActiveWidgetIds(context, includeUnboundGhosts = true)
                val realActiveIds = getActiveWidgetIds(context, includeUnboundGhosts = false).toSet()
                val ghostIds = allRawIds.filterNot { realActiveIds.contains(it) }

                if (ghostIds.isNotEmpty()) {
                    val emptyViews = android.widget.RemoteViews(
                        context.packageName,
                        com.necromagik.pureclock.R.layout.widget_pure_clock
                    ).apply {
                        setImageViewBitmap(com.necromagik.pureclock.R.id.widget_image_container, null)
                    }
                    for (ghostId in ghostIds) {
                        appWidgetManager.updateAppWidget(ghostId, emptyViews)
                        WidgetConfigRepository(context).deleteConfig(ghostId)
                    }
                    Log.w(TAG, "--> [Purge] Очищена память у ${ghostIds.size} фантомных виджетов: ${ghostIds.joinToString()}")
                }
                ghostIds.size
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка очистки фантомных виджетов: ${e.message}")
                0
            }
        }

        fun refreshAllActiveWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)

            // Сначала сбрасываем bitmap-память у невидимых фантомов лаунчера (views_bitmap_memory = 0)
            purgeGhostWidgetsMemory(appContext)

            val ids = getActiveWidgetIds(appContext, includeUnboundGhosts = false)

            if (ids.isNotEmpty()) {
                Log.d(TAG, "--> [refreshAllActiveWidgets] Обновление живых виджетов (${ids.size} шт.): ${ids.joinToString()}")
                for (id in ids) {
                    updateSingleWidget(appContext, appWidgetManager, id)
                }
                scheduleWidgetUpdates(appContext)
            } else {
                Log.d(TAG, "--> [refreshAllActiveWidgets] Живых виджетов на экране нет. Остановка тиков.")
                cancelWidgetUpdates(appContext)
            }
        }

        fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            try {
                val startTime = System.currentTimeMillis()
                val repo = WidgetConfigRepository(context)
                val config = repo.getConfig(widgetId).copy(id = widgetId)

                val views = WidgetRenderEngine.buildCustomRemoteViews(
                    context = context,
                    config = config,
                    appWidgetId = widgetId,
                    appWidgetManager = appWidgetManager
                )

                appWidgetManager.updateAppWidget(widgetId, views)
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "----> [Render] WidgetId=$widgetId успешно обновлён за ${duration}мс")
            } catch (e: Exception) {
                Log.e(TAG, "!!!! [Render ERROR] Ошибка при обновлении виджета ID=$widgetId: ${e.message}", e)
            }
        }

        fun scheduleWidgetUpdates(context: Context) {
            val appContext = context.applicationContext
            val ids = getActiveWidgetIds(appContext)
            if (ids.isEmpty()) {
                cancelWidgetUpdates(appContext)
                return
            }

            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            // Явный ComponentName + FLAG_RECEIVER_FOREGROUND не даёт OriginOS заморозить доставку (Reason=frozen)
            val intent = Intent(appContext, PureClockWidgetProvider::class.java).apply {
                component = ComponentName(appContext, PureClockWidgetProvider::class.java)
                action = ACTION_WIDGET_UPDATE_TICK
                setPackage(appContext.packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                WIDGET_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val nextTickMillis = calendar.timeInMillis

            try {
                val showIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    appContext,
                    WIDGET_SHOW_REQUEST_CODE,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTickMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "==> [Schedule] setAlarmClock назначен на ${formatTime(nextTickMillis)}")
            } catch (e: Exception) {
                Log.w(TAG, "--> [Schedule WARN] setAlarmClock отклонён (${e.message}), резервный setExactAndAllowWhileIdle")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTickMillis,
                    pendingIntent
                )
            }
        }

        fun cancelWidgetUpdates(context: Context) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(appContext, PureClockWidgetProvider::class.java).apply {
                component = ComponentName(appContext, PureClockWidgetProvider::class.java)
                action = ACTION_WIDGET_UPDATE_TICK
                setPackage(appContext.packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                WIDGET_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}