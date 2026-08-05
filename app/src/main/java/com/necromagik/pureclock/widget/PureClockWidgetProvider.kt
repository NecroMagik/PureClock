package com.necromagik.pureclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.repository.WidgetConfigRepository

class PureClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId)
        }
        scheduleWidgetUpdates(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null || intent == null) return

        val action = intent.action
        if (action == ACTION_WIDGET_UPDATE_TICK || action == Intent.ACTION_TIME_TICK) {
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
        val repo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            repo.deleteConfig(widgetId)
        }
    }

    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val repo = WidgetConfigRepository(context)
        val config = repo.getConfig(widgetId)

        // Тап по виджету откроет его повторное редактирование
        val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val views = WidgetRenderEngine.buildCustomRemoteViews(context, config)
        views.setOnClickPendingIntent(R.id.widget_root_container, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun scheduleWidgetUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PureClockWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_UPDATE_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTick = (System.currentTimeMillis() / 60000L + 1) * 60000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, nextTick, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC, nextTick, pendingIntent)
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE_TICK = "com.necromagik.pureclock.ACTION_WIDGET_UPDATE_TICK"
    }
}