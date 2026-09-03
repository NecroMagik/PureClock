package com.necromagik.pureclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
        scheduleWidgetUpdates(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val now = System.currentTimeMillis()
        Log.d(TAG, "==> [onUpdate] Системный вызов onUpdate() в ${formatTime(now)}. Виджетов для обновления: ${appWidgetIds.size}")

        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWidgetUpdates(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null || intent == null) return

        val now = System.currentTimeMillis()
        val action = intent.action ?: "UNKNOWN_ACTION"
        val seconds = (now / 1000) % 60
        val millis = now % 1000

        Log.i(TAG, "==> [onReceive] Получен интент [$action] в ${formatTime(now)} (Секунда: $seconds, мс: $millis)")

        if (action == ACTION_WIDGET_UPDATE_TICK ||
            action == Intent.ACTION_TIME_TICK ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_USER_FOREGROUND ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_SCREEN_ON ||
            action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerComponent = ComponentName(context, PureClockWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(providerComponent)

            if (ids != null && ids.isNotEmpty()) {
                Log.d(TAG, "--> [onReceive] Найдено активных виджетов: ${ids.size}. Запуск перерисовки...")
                for (id in ids) {
                    updateSingleWidget(context, appWidgetManager, id)
                }
            } else {
                Log.w(TAG, "--> [onReceive] Список виджетов пуст (ids is null or empty)!")
            }

            scheduleWidgetUpdates(context)
        } else {
            Log.v(TAG, "--> [onReceive] Пропущен интент $action (не входит в фильтр)")
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.w(TAG, "==> [onDeleted] Удалены виджеты: ${appWidgetIds.joinToString()}")
        val repo = WidgetConfigRepository(context)
        for (widgetId in appWidgetIds) {
            repo.deleteConfig(widgetId)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.w(TAG, "==> [onDisabled] Все виджеты удалены. Остановка планировщика...")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PureClockWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_UPDATE_TICK
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
        try {
            val startTime = System.currentTimeMillis()
            val repo = WidgetConfigRepository(context)
            val config = repo.getConfig(widgetId)

            Log.d(TAG, "----> [Render] Начало сборки RemoteViews для WidgetId: $widgetId")
            val views = WidgetRenderEngine.buildCustomRemoteViews(context, config)

            appWidgetManager.updateAppWidget(widgetId, views)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "----> [Render] WidgetId: $widgetId успешно обновлен за ${duration}мс")
        } catch (e: Exception) {
            Log.e(TAG, "!!!! [Render ERROR] Ошибка при обновлении виджета ID $widgetId: ${e.message}", e)
        }
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

        fun scheduleWidgetUpdates(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "!!!! [Schedule ERROR] AlarmManager недоступен!")
                return
            }

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
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val nextTickMillis = calendar.timeInMillis
            val delayMs = nextTickMillis - now
            val delaySec = delayMs / 1000.0

            Log.i(TAG, "==> [Schedule] Планирование тика на ${formatTime(nextTickMillis)} (через ${String.format(Locale.ROOT, "%.2f", delaySec)} сек / ${delayMs}мс)")

            try {
                // AlarmClockInfo — единственный тип аларма, который Android не сбрасывает в фоновом профиле
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    WIDGET_SHOW_REQUEST_CODE,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTickMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.v(TAG, "--> [Schedule] Вызван AlarmManager.setAlarmClock (иммунитет к смене профиля)")
            } catch (e: Exception) {
                Log.w(TAG, "--> [Schedule WARN] setAlarmClock отклонен (${e.message}), резервный setExactAndAllowWhileIdle()")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTickMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTickMillis, pendingIntent)
                }
            }
        }
    }
}