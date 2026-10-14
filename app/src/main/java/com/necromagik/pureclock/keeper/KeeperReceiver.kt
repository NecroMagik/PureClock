package com.necromagik.pureclock.keeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeeperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i("PureClock_KEEPER", "==> [KeeperReceiver] Перехвачено системное событие: $action")

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val isUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            userManager?.isUserUnlocked ?: false
        } else {
            true
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    // При BOOT_COMPLETED и LOCKED_BOOT_COMPLETED в Android 15+ строго запрещено
                    // запускать Foreground-сервисы и LifecycleService. Восстанавливаем только расписание AlarmManager!
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                        val db = AppDatabase.getDatabase(context)
                        val scheduler = AlarmScheduler(context)
                        val enabledAlarms = db.alarmDao().getEnabledAlarmsSync()
                        enabledAlarms.forEach { alarm ->
                            scheduler.schedule(alarm)
                        }
                    }

                    // Когда пользователь реально взаимодействует с устройством (разблокировал экран),
                    // ограничения BOOT_COMPLETED уже не действуют — можно запускать плановые задачи
                    Intent.ACTION_USER_UNLOCKED,
                    Intent.ACTION_USER_PRESENT,
                    Intent.ACTION_USER_FOREGROUND,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        if (isUnlocked) {
                            AppKeeper.start(context)
                        } else {
                            AppKeeper.reviveAllProcesses(context)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}