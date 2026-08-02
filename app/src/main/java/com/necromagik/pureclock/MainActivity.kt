package com.necromagik.pureclock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.ui.MainScreen
import com.necromagik.pureclock.ui.theme.PureClockTheme
import com.necromagik.pureclock.ui.viewmodel.AlarmViewModel
import com.necromagik.pureclock.util.ClockIconManager

class MainActivity : ComponentActivity() {

    private val alarmViewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Обновляем Dynamic Shortcut со свежими стрелками
        ClockIconManager(applicationContext).updateDynamicShortcut()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        setContent {
            val settingsManager = remember { SettingsManager.getInstance(applicationContext) }
            val themeState by settingsManager.themeState.collectAsState()

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
}