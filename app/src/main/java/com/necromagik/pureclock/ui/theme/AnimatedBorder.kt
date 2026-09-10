package com.necromagik.pureclock.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.pureAnimatedBorder(
    shape: Shape,
    accentColor: Color,
    borderWidth: Dp = 1.5.dp,
    enabled: Boolean = true,
    durationMillis: Int = 3000
): Modifier = composed {
    if (!enabled) {
        return@composed this.border(1.dp, Color.Gray.copy(alpha = 0.15f), shape)
    }

    val transition = rememberInfiniteTransition(label = "BorderShimmer")
    val offsetProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BorderOffset"
    )

    val breathingAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BorderPulse"
    )

    // Градиент скользит вдоль диагонали карточки без вращения canvas
    val gradientBrush = Brush.linearGradient(
        0.0f to accentColor.copy(alpha = 0.15f),
        offsetProgress * 0.5f to accentColor.copy(alpha = breathingAlpha),
        1.0f to accentColor.copy(alpha = 0.2f)
    )

    this.border(
        width = borderWidth,
        brush = gradientBrush,
        shape = shape
    )
}