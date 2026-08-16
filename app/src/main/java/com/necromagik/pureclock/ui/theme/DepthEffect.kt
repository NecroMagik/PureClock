package com.necromagik.pureclock.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Превращает контейнер в объёмный 3D-элемент с эффектом глубины даже на AMOLED-экранах
 */
fun Modifier.pure3DEffect(
    shape: Shape = RoundedCornerShape(20.dp),
    accentColor: Color = Color(0xFF00E676),
    depthDp: Dp = 8.dp,
    is3dEnabled: Boolean = true,
    isGlowEnabled: Boolean = true,
    surfaceColor: Color = Color(0xFF141417)
): Modifier {
    if (!is3dEnabled) {
        return this
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = shape
            )
    }

    val elevation = if (depthDp > 0.dp) depthDp else 4.dp

    // На черном фоне видимость тени дает рассеянное свечение (ambient), а не spot-чернота
    val ambientAlpha = if (isGlowEnabled) 0.55f else 0.25f
    val borderAlpha = if (isGlowEnabled) 0.50f else 0.20f

    return this
        // 1. Светорассеяние и приподнятость над фоном
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = accentColor.copy(alpha = ambientAlpha),
            spotColor = accentColor.copy(alpha = if (isGlowEnabled) 0.35f else 0.15f)
        )
        .clip(shape)
        // 2. Имитация падения света сверху вниз (Top-to-Bottom Light)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    surfaceColor.copy(alpha = 1f),
                    surfaceColor.copy(alpha = 0.82f)
                )
            )
        )
        // 3. Фаска: яркий верхний кант (отражение) + акцентное свечение
        .border(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = 0.15f),
                    accentColor.copy(alpha = borderAlpha * 0.2f),
                    Color.Transparent
                )
            ),
            shape = shape
        )
}