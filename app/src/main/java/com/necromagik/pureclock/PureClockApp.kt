package com.necromagik.pureclock

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.necromagik.pureclock.data.SettingsManager

class PureClockApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        val settingsManager = SettingsManager.getInstance(this)
        val source = settingsManager.getInstallationSource()

        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val isUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            userManager.isUserUnlocked
        } else {
            true
        }

        when (source) {
            SettingsManager.InstallSource.SYSTEM_PREBUILT -> {
                Log.d("PureClockApp", "Running as Pure OS System Prebuilt")
                // Системный режим: инициализируем WorkManager ТОЛЬКО если пользователь уже ввёл пароль
                if (isUnlocked) {
                    initWorkManagerSafe()
                }
            }
            else -> {
                Log.d("PureClockApp", "Running as User / Market app")
                // Обычный режим (Google Play / RuStore): инициализируем безопасно
                if (isUnlocked) {
                    initWorkManagerSafe()
                }
            }
        }
    }

    private fun initWorkManagerSafe() {
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d("PureClockApp", "WorkManager initialized successfully")
        } catch (e: Exception) {
            Log.w("PureClockApp", "WorkManager already initialized or failed: ${e.message}")
        }
    }
}