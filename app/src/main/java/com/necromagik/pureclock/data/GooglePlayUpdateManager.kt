package com.necromagik.pureclock.data

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class GooglePlayUpdateManager(private val context: Context) {

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context.applicationContext)
    }

    suspend fun checkUpdateAvailability(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            val isAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ||
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            if (isAvailable && isAllowed) {
                appUpdateInfo
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun startUpdateFlow(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: Int = AppUpdateType.FLEXIBLE
    ) {
        val options = AppUpdateOptions.newBuilder(updateType).build()
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            launcher,
            options
        )
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }
}