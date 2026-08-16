package com.necromagik.pureclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.repository.WidgetConfigRepository
import java.util.Calendar

class PureClockWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("PureClockWidget", "onEnabled: инициализация таймера тиков")
        scheduleWidgetUpdates(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWidgetUpdates(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null || intent == null) return

        val action = intent.action
        Log.d("PureClockWidget", "Получен интент обновления виджета: $action")

        if (action == ACTION_WIDGET_UPDATE_TICK ||
            action == Intent.ACTION_TIME_TICK ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerComponent = ComponentName(context, PureClockWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(providerComponent)

            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateSingleWidget(context, appWidgetManager, id)
                }
            }
            scheduleWidgetUpdates(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            repo.deleteConfig(widgetId)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PureClockWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_UPDATE_TICK
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WIDGET_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val repo = WidgetConfigRepository(context)
        val config = repo.getConfig(widgetId)

        val views = WidgetRenderEngine.buildCustomRemoteViews(context, config)
        // Строка views.setOnClickPendingIntent(R.id.widget_root_container, pendingIntent) УДАЛЕНА
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_WIDGET_UPDATE_TICK = "com.necromagik.pureclock.ACTION_WIDGET_UPDATE_TICK"
        private const val WIDGET_ALARM_REQUEST_CODE = 1001

        fun scheduleWidgetUpdates(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, PureClockWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_UPDATE_TICK
                setPackage(context.packageName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }

            var nextTickMillis = calendar.timeInMillis
            if (nextTickMillis <= now) {
                nextTickMillis += 60_000L
            }

            // setAlarmClock игнорирует ограничения Doze mode и агрессивные таск-киллеры
            val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTickMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        }
    }
}