package com.necromagik.pureclock.keeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class KeeperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i("PureClock_KEEPER", "==> [KeeperReceiver] Перехвачено системное событие: $action")

        when (action) {
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_FOREGROUND,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                try {
                    AppKeeper.reviveAllProcesses(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}