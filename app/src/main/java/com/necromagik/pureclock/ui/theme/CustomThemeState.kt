package com.necromagik.pureclock.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ThemeState(
    val themeMode: String = "SYSTEM",
    val isPureMonocolor: Boolean = true,
    val accentColorHex: String = "#00E676",
    val cardCornerRadiusDp: Int = 24
)