package com.necromagik.pureclock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = WidgetConfigRepository(this)
        val initialConfig = repo.getConfig(appWidgetId)
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
                    initialConfig = initialConfig,
                    onBackClick = { finish() },
                    onSaveConfig = { updatedConfig ->
                        repo.saveConfig(appWidgetId, updatedConfig)

                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        val views = WidgetRenderEngine.buildCustomRemoteViews(this, updatedConfig)
                        appWidgetManager.updateAppWidget(appWidgetId, views)

                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                )
            }
        }
    }
}