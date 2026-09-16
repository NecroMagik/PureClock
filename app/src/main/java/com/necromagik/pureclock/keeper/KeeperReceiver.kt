package com.necromagik.pureclock.keeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log

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
        try {
            when (action) {
                // Пользователь расшифровал раздел данных (ввел PIN/графический ключ)
                Intent.ACTION_USER_UNLOCKED -> {
                    // Теперь безопасно поднять полный цикл вместе с WorkManager
                    AppKeeper.start(context)
                }

                // До разблокировки (Direct Boot) или обычные триггеры времени
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_FOREGROUND,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    if (isUnlocked) {
                        AppKeeper.start(context)
                    } else {
                        // В заблокированном состоянии восстанавливаем только сигналы AlarmManager
                        AppKeeper.reviveAllProcesses(context)
                    }
                }
            }
        } finally {
            pendingResult.finish()
        }
    }
}