package com.necromagik.pureclock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.MainScreen
import com.necromagik.pureclock.ui.theme.PureClockTheme
import com.necromagik.pureclock.ui.viewmodel.AlarmViewModel
import com.necromagik.pureclock.util.ClockIconManager

class MainActivity : ComponentActivity() {

    private val alarmViewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.necromagik.pureclock.util.OrientationLockHelper.lockPortraitOnPhones(this)

        requestIgnoreBatteryOptimizationsOnce()
        ClockIconManager(applicationContext).updateDynamicShortcut()

        setShowWhenLocked(false)
        setTurnScreenOn(false)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        setContent {
            val settingsManager = remember { SettingsManager.getInstance(applicationContext) }
            val themeState by settingsManager.themeState.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* Результат получен */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val prefs = getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE)
                    val alreadyAsked = prefs.getBoolean("asked_post_notifications_once", false)
                    val isGranted = ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!isGranted && !alreadyAsked) {
                        prefs.edit { putBoolean("asked_post_notifications_once", true) }
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeState.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            PureClockTheme(
                themeMode = themeState.themeMode,
                isPureMonocolor = themeState.isPureMonocolor,
                accentColor = settingsManager.getAccentColor(themeState.accentColorHex),
                cardCornerRadiusDp = themeState.cardCornerRadiusDp,
                is3dEnabled = themeState.is3DEffectsEnabled,
                isGlowEnabled = themeState.isNeonGlowEnabled,
                depthIntensityDp = themeState.depthIntensityDp
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = alarmViewModel)
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimizationsOnce() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        val prefs = getSharedPreferences("pureclock_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery_opt_once", false)) return

        try {
            prefs.edit { putBoolean("asked_battery_opt_once", true) }
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }
}