package com.necromagik.pureclock.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

object SystemThemeUtils {

    const val SYSTEM_MONET_KEY = "SYSTEM_MONET"
    private const val FALLBACK_DEFAULT_HEX = "#00E676" // Pure Green для систем ниже Android 12

    /**
     * Проверяет, поддерживается ли динамический Monet в системе (Android 12+)
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Возвращает чистый HEX-код текущего системного акцента (Dynamic Monet / System Primary)
     */
    fun getSystemAccentHex(context: Context): String {
        return if (isDynamicColorSupported()) {
            try {
                val dynamicScheme = dynamicDarkColorScheme(context)
                val argb = dynamicScheme.primary.toArgb()
                // Форматируем с сохранением верхнего регистра и чистой маской RGB
                String.format("#%06X", 0xFFFFFF and argb)
            } catch (_: Exception) {
                FALLBACK_DEFAULT_HEX
            }
        } else {
            FALLBACK_DEFAULT_HEX
        }
    }

    /**
     * Composable-хелпер для быстрого считывания системного Compose Color в реальном времени
     */
    @Composable
    @ReadOnlyComposable
    fun rememberSystemAccentColor(): Color {
        val context = LocalContext.current
        return Color(getSystemAccentHex(context).toColorInt())
    }

    /**
     * Вычисляет идеальный контрастный цвет (Чёрный или Белый) для иконки/текста поверх заданного фона
     */
    fun getContrastingColor(backgroundColor: Color): Color {
        // Если люминация (яркость) больше 0.5 — фон светлый, берем черный. Иначе — белый.
        return if (backgroundColor.luminance() > 0.45f) Color.Black else Color.White
    }

    /**
     * Проверяет, выбран ли сейчас системный цвет (по маркеру или совпадению с текущим Monet)
     */
    fun isSystemSelected(currentAccentHex: String, context: Context): Boolean {
        if (currentAccentHex.equals(SYSTEM_MONET_KEY, ignoreCase = true)) return true
        val realSystemHex = getSystemAccentHex(context)
        return currentAccentHex.equals(realSystemHex, ignoreCase = true)
    }
}