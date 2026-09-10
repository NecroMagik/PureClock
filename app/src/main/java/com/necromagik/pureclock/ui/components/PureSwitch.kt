package com.necromagik.pureclock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import kotlinx.coroutines.delay

enum class SwitchIconType {
    NONE,
    MUSIC,
    NOTIFICATIONS,
    SOUNDS
}

@Composable
fun PureSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconType: SwitchIconType = SwitchIconType.NONE,
    trackColorOverride: Color? = null,
    onDisabledClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val config = LocalPureClockConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val trackWidth = 54.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val thumbPadding = 4.dp

    var isPlayingMusicAnim by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (checked && iconType == SwitchIconType.MUSIC) {
            isPlayingMusicAnim = true
            delay(850)
            isPlayingMusicAnim = false
        } else {
            isPlayingMusicAnim = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MusicWave")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveBar1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveBar2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(160, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveBar3"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            trackColorOverride != null -> trackColorOverride
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
        enabled = true
    ) {
        if (!enabled) {
            onDisabledClick?.invoke()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCheckedChange(!checked)
        }
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
        ),
        contentAlignment = Alignment.Center
        ) {
        if (iconType == SwitchIconType.MUSIC && checked) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val barWidth = 2.dp.toPx()
                val corner = CornerRadius(barWidth / 2, barWidth / 2)
                val maxHeight = size.height * 0.85f
                val minHeight = 3.dp.toPx()
                val barColor = config.accentColor

                val h1 = if (isPlayingMusicAnim) (minHeight + (maxHeight - minHeight) * wave1) else 6.dp.toPx()
                val h2 = if (isPlayingMusicAnim) (minHeight + (maxHeight - minHeight) * wave2) else 10.dp.toPx()
                val h3 = if (isPlayingMusicAnim) (minHeight + (maxHeight - minHeight) * wave3) else 4.dp.toPx()

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x = size.width * 0.2f - barWidth / 2, y = (size.height - h1) / 2),
                    size = Size(barWidth, h1),
                    cornerRadius = corner
                )

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x = size.width * 0.5f - barWidth / 2, y = (size.height - h2) / 2),
                    size = Size(barWidth, h2),
                    cornerRadius = corner
                )

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x = size.width * 0.8f - barWidth / 2, y = (size.height - h3) / 2),
                    size = Size(barWidth, h3),
                    cornerRadius = corner
                )
            }
        }

        if (iconType == SwitchIconType.SOUNDS && checked) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = 1.5.dp.toPx()
                val color = config.accentColor
                drawArc(
                    color = color,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.4f, size.height * 0.2f),
                    size = Size(size.width * 0.5f, size.height * 0.6f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = color,
                    radius = 2.5.dp.toPx(),
                    center = Offset(size.width * 0.35f, size.height * 0.5f)
                )
            }
        }
    }
    }
}