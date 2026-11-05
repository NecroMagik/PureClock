package com.necromagik.pureclock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.necromagik.pureclock.data.SettingsManager
import com.necromagik.pureclock.data.repository.WidgetConfigRepository
import com.necromagik.pureclock.ui.screens.WidgetEditorScreen
import com.necromagik.pureclock.ui.theme.PureClockTheme

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.necromagik.pureclock.util.OrientationLockHelper.lockPortraitOnPhones(this)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: intent?.data?.lastPathSegment?.toIntOrNull()
                ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val repo = WidgetConfigRepository(this)
        val activeIds = PureClockWidgetProvider.getActiveWidgetIds(this)
        val targetWidgetId = when {
            appWidgetId > 0 -> appWidgetId
            activeIds.isNotEmpty() -> activeIds.first()
            else -> 0
        }

        Log.d(PureClockWidgetProvider.TAG, "==> [WidgetConfigActivity] Открыт редактор для targetWidgetId=$targetWidgetId (raw=$appWidgetId)")

        val savedConfig = repo.getConfig(targetWidgetId).copy(id = targetWidgetId)

        // Сразу возвращаем RESULT_OK для валидного ID, чтобы лаунчер не создавал зомби-виджеты при закрытии свайпом
        if (targetWidgetId > 0) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
        } else {
            setResult(Activity.RESULT_OK)
        }

        val settings = SettingsManager.getInstance(this)
        val themeState = settings.themeState.value

        setContent {
            PureClockTheme(
                themeMode = themeState.themeMode,
                isPureMonocolor = themeState.isPureMonocolor,
                accentColor = settings.accentColor,
                cardCornerRadiusDp = themeState.cardCornerRadiusDp,
                is3dEnabled = themeState.is3DEffectsEnabled,
                isGlowEnabled = themeState.isNeonGlowEnabled,
                depthIntensityDp = themeState.depthIntensityDp
            ) {
                WidgetEditorScreen(
                    initialConfig = savedConfig,
                    onBackClick = {
                        PureClockWidgetProvider.refreshAllActiveWidgets(this)
                        finish()
                    },
                    onSaveConfig = { updatedConfig ->
                        // 1. Синхронно сохраняем базовый профиль и профиль конкретного виджета
                        repo.saveConfig(0, updatedConfig.copy(id = 0))
                        if (targetWidgetId > 0) {
                            repo.saveConfig(targetWidgetId, updatedConfig.copy(id = targetWidgetId))
                        }

                        val allIds = PureClockWidgetProvider.getActiveWidgetIds(this)
                        for (id in allIds) {
                            repo.saveConfig(id, updatedConfig.copy(id = id))
                        }

                        // 2. Принудительно перерисовываем все активные виджеты и перезапускаем минутный таймер
                        PureClockWidgetProvider.refreshAllActiveWidgets(this)

                        if (targetWidgetId > 0) {
                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                        }
                        finish()
                    }
                )
            }
        }
    }
}