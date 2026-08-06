package com.necromagik.pureclock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

        val repo = WidgetConfigRepository(this)
        val targetWidgetId = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetId else 0
        val savedConfig = repo.getConfig(targetWidgetId)

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
                    onBackClick = { finish() },
                    onSaveConfig = { updatedConfig ->
                        repo.saveConfig(targetWidgetId, updatedConfig)

                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        val views = WidgetRenderEngine.buildCustomRemoteViews(this, updatedConfig)

                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                        } else {
                            val allWidgetIds = appWidgetManager.getAppWidgetIds(
                                ComponentName(this, PureClockWidgetProvider::class.java)
                            )
                            for (id in allWidgetIds) {
                                repo.saveConfig(id, updatedConfig)
                                appWidgetManager.updateAppWidget(id, views)
                            }
                        }
                        finish()
                    }
                )
            }
        }
    }
}