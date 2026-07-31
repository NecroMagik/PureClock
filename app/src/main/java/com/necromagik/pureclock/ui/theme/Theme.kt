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
    val accentColor: Color = Color(0xFF00E676),
    val cardCornerRadius: Dp = 24.dp,
    val isAmoled: Boolean = true,
    val is3dEnabled: Boolean = true,
    val isGlowEnabled: Boolean = true,
    val depthIntensityDp: Dp = 8.dp
)

val LocalPureClockConfig = staticCompositionLocalOf { PureClockThemeConfig() }

@Composable
fun PureClockTheme(
    themeMode: String = "SYSTEM",
    isPureMonocolor: Boolean = true,
    accentColor: Color = Color(0xFF00E676),
    cardCornerRadiusDp: Int = 24,
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

    val darkBackground = if (isPureMonocolor) Color(0xFF000000) else Color(0xFF0D0D10)
    val darkSurface = if (isPureMonocolor) Color(0xFF101012) else Color(0xFF18181C)

    val lightBackground = if (isPureMonocolor) Color(0xFFFFFFFF) else Color(0xFFF4F4F6)
    val lightSurface = if (isPureMonocolor) Color(0xFFF8F8FA) else Color(0xFFFFFFFF)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.Black,
            primaryContainer = accentColor.copy(alpha = 0.2f),
            onPrimaryContainer = accentColor,
            secondary = accentColor,
            secondaryContainer = accentColor.copy(alpha = 0.15f),
            onSecondaryContainer = accentColor,
            background = darkBackground,
            onBackground = Color.White,
            surface = darkSurface,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF222226),
            onSurfaceVariant = Color(0xFFC8C8CE),
            outline = Color(0xFF3F3F46),
            outlineVariant = Color(0xFF27272A)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.15f),
            onPrimaryContainer = accentColor,
            secondary = accentColor,
            secondaryContainer = accentColor.copy(alpha = 0.12f),
            onSecondaryContainer = Color.Black,
            background = lightBackground,
            onBackground = Color.Black,
            surface = lightSurface,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE4E4E8),
            onSurfaceVariant = Color(0xFF52525B),
            outline = Color(0xFFD4D4D8),
            outlineVariant = Color(0xFFE4E4E7)
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