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
 * Превращает любой контейнер Compose в объёмный 3D-элемент с неоновой фаской и слоистой тенью
 */
fun Modifier.pure3DEffect(
    shape: Shape = RoundedCornerShape(24.dp),
    accentColor: Color = Color(0xFF00E676),
    depthDp: Dp = 8.dp,
    is3dEnabled: Boolean = true,
    isGlowEnabled: Boolean = true,
    surfaceColor: Color = Color(0xFF121212) // <-- Добавили дефолтное значение
): Modifier {
    if (!is3dEnabled) {
        return this
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.15f),
                shape = shape
            )
    }

    val glowBorderAlpha = if (isGlowEnabled) 0.45f else 0.15f
    val depthElevation = if (depthDp > 0.dp) depthDp else 4.dp

    return this
        // 1. Падающая объемная тень
        .shadow(
            elevation = depthElevation,
            shape = shape,
            clip = false,
            ambientColor = accentColor.copy(alpha = if (isGlowEnabled) 0.35f else 0.12f),
            spotColor = Color.Black.copy(alpha = 0.8f)
        )
        .clip(shape)
        // 2. Объёмная фоновая подложка
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    surfaceColor,
                    surfaceColor.copy(alpha = 0.9f)
                )
            )
        )
        // 3. Неоновая или классическая контрастная фаска (Border Gradient)
        .border(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = glowBorderAlpha),
                    accentColor.copy(alpha = glowBorderAlpha * 0.2f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = shape
        )
}