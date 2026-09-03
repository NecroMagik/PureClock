package com.necromagik.pureclock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig

@Composable
fun PureSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val config = LocalPureClockConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val thumbSize = 22.dp
    val thumbPadding = 4.dp

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            checked -> config.accentColor
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SwitchTrackColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding else thumbPadding,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SwitchThumbOffset"
    )

    val thumbStretchWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) thumbSize + 6.dp else thumbSize,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SwitchThumbStretch"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (checked && enabled && config.isGlowEnabled) 0.45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SwitchGlowAlpha"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .then(
                if (checked && enabled && config.is3dEnabled) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(trackHeight / 2),
                        ambientColor = config.accentColor.copy(alpha = glowAlpha),
                        spotColor = config.accentColor.copy(alpha = glowAlpha * 0.7f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(trackColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (checked) 0.35f else 0.12f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(trackHeight / 2)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = thumbStretchWidth, height = thumbSize)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    if (checked && enabled) MaterialTheme.colorScheme.surface else Color.White
                )
        )
    }
}