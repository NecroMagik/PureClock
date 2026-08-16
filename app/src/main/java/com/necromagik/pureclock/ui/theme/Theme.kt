package com.necromagik.pureclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class PureClockThemeConfig(
    val accentColor: Color = Color.Unspecified,
    val cardCornerRadius: Dp = 24.dp,
    val isAmoled: Boolean = false,
    val is3dEnabled: Boolean = true,
    val isGlowEnabled: Boolean = true,
    val depthIntensityDp: Dp = 8.dp
)

val LocalPureClockConfig = staticCompositionLocalOf { PureClockThemeConfig() }

@Composable
fun PureClockTheme(
    themeMode: String = "SYSTEM",
    isPureMonocolor: Boolean = false, // По умолчанию выключен чистый монохром для лучшего объема
    accentColor: Color = SystemThemeUtils.rememberSystemAccentColor(),
    cardCornerRadiusDp: Int = 20,
    is3dEnabled: Boolean = true,
    isGlowEnabled: Boolean = true,
    depthIntensityDp: Int = 8,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    // Для AMOLED фон #000000, но карточки (surface) должны быть контрастными (#141417), иначе нет объема
    val darkBackground = if (isPureMonocolor) Color(0xFF000000) else Color(0xFF0C0D11)
    val darkSurface = if (isPureMonocolor) Color(0xFF141417) else Color(0xFF171920)

    val lightBackground = if (isPureMonocolor) Color(0xFFFFFFFF) else Color(0xFFF3F4F8)
    val lightSurface = if (isPureMonocolor) Color(0xFFFFFFFF) else Color(0xFFFEFEFE)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = SystemThemeUtils.getContrastingColor(accentColor),
            primaryContainer = accentColor.copy(alpha = 0.2f),
            onPrimaryContainer = accentColor,
            secondary = accentColor,
            secondaryContainer = accentColor.copy(alpha = 0.15f),
            onSecondaryContainer = accentColor,
            background = darkBackground,
            onBackground = Color.White,
            surface = darkSurface,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF22242D),
            onSurfaceVariant = Color(0xFFB0B3C0),
            outline = Color(0xFF333644),
            outlineVariant = Color(0xFF1E202A)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = SystemThemeUtils.getContrastingColor(accentColor),
            primaryContainer = accentColor.copy(alpha = 0.15f),
            onPrimaryContainer = accentColor,
            secondary = accentColor,
            secondaryContainer = accentColor.copy(alpha = 0.12f),
            onSecondaryContainer = Color.Black,
            background = lightBackground,
            onBackground = Color.Black,
            surface = lightSurface,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE3E5ED),
            onSurfaceVariant = Color(0xFF5B5E6E),
            outline = Color(0xFFD1D5E0),
            outlineVariant = Color(0xFFE2E4EB)
        )
    }

    val customShapes = Shapes(
        small = RoundedCornerShape((cardCornerRadiusDp / 2).dp),
        medium = RoundedCornerShape(cardCornerRadiusDp.dp),
        large = RoundedCornerShape((cardCornerRadiusDp * 1.25).toInt().dp)
    )

    val config = PureClockThemeConfig(
        accentColor = accentColor,
        cardCornerRadius = cardCornerRadiusDp.dp,
        isAmoled = isPureMonocolor,
        is3dEnabled = is3dEnabled,
        isGlowEnabled = isGlowEnabled,
        depthIntensityDp = depthIntensityDp.dp
    )

    CompositionLocalProvider(LocalPureClockConfig provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = customShapes,
            typography = Typography,
            content = content
        )
    }
}