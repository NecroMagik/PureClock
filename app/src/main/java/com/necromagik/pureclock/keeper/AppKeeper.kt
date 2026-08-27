package com.necromagik.pureclock.keeper

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import androidx.work.WorkManager
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AppDatabase
import com.necromagik.pureclock.widget.PureClockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppKeeper {

    private const val TAG = "PureClock_KEEPER"
    private const val KEEPER_WORK_NAME = "pureclock_process_keeper_work"

    /**
     * Первичная инициализация мониторинга при запуске приложения или приложения в профиле
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        Log.i(TAG, "==> [AppKeeper.start] Инициализация сторожевого модуля AppKeeper...")

        // 1. Немедленная проверка и восстановление процессов
        reviveAllProcesses(appContext)

        // 2. Регистрация периодического фонового страховщика через WorkManager
        schedulePeriodicKeeperWork(appContext)
    }

    /**
     * Полная реанимация всех фоновых сервисов, будильников и виджетов
     */
    fun reviveAllProcesses(context: Context) {
        val appContext = context.applicationContext
        Log.d(TAG, "==> [AppKeeper.reviveAllProcesses] Старт процедуры восстановления компонентов...")

        // Асинхронно восстанавливаем расписание будильников из базы данных
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val scheduler = AlarmScheduler(appContext)
                val enabledAlarms = db.alarmDao().getEnabledAlarmsSync()

                Log.d(TAG, "--> [AppKeeper] Активных будильников в базе: ${enabledAlarms.size}. Перепланирование...")
                enabledAlarms.forEach { alarm ->
                    scheduler.schedule(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "!!!! [AppKeeper ERROR] Сбой восстановления будильников: ${e.message}", e)
            }
        }

        // Восстановление цепочки обновлений виджетов рабочего стола
        try {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val providerComponent = ComponentName(appContext, PureClockWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(providerComponent)

            if (widgetIds != null && widgetIds.isNotEmpty()) {
                Log.d(TAG, "--> [AppKeeper] Найдено активных виджетов: ${widgetIds.size}. Перезапуск планировщика виджета...")
                PureClockWidgetProvider.scheduleWidgetUpdates(appContext)

                // Принудительно отправляем широковещательный интент для мгновенного тика
                val updateIntent = Intent(appContext, PureClockWidgetProvider::class.java).apply {
                    action = PureClockWidgetProvider.ACTION_WIDGET_UPDATE_TICK
                    setPackage(appContext.packageName)
                }
                appContext.sendBroadcast(updateIntent)
            } else {
                Log.v(TAG, "--> [AppKeeper] Активных виджетов на рабочем столе не обнаружено.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "!!!! [AppKeeper ERROR] Сбой восстановления виджета: ${e.message}", e)
        }
    }

    /**
     * Фоновая периодическая проверка WorkManager (защита от гибернации профиля)
     */
    private fun schedulePeriodicKeeperWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val keeperRequest = PeriodicWorkRequestBuilder<KeeperWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                KEEPER_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                keeperRequest
            )
            Log.v(TAG, "--> [AppKeeper] Фоновый PeriodicWork keeper успешно зарегистрирован.")
        } catch (e: Exception) {
            Log.e(TAG, "!!!! [AppKeeper ERROR] Ошибка планирования WorkManager: ${e.message}", e)
        }
    }
}