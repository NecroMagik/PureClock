package com.necromagik.pureclock.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo

object OrientationLockHelper {
    /**
     * Фиксирует вертикальную (портретную) ориентацию ТОЛЬКО на телефонах (sw < 600dp).
     * На планшетах и больших экранах (sw >= 600dp) оставляет свободный поворот по требованиям Android 16.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    fun lockPortraitOnPhones(activity: Activity) {
        val isLargeScreen = activity.resources.configuration.smallestScreenWidthDp >= 600
        if (!isLargeScreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}