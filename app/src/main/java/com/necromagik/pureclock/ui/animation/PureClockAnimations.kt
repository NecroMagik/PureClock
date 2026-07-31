package com.necromagik.pureclock.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// СЕКЦИЯ 1: ГЛОБАЛЬНЫЕ СПЕЦИФИКАЦИИ АНИМАЦИИ (SPRING & TWEEN SPECS)
// ============================================================================
object PureClockAnimationSpecs {
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val SpringFast = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    val SmoothTween = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)
}

// ============================================================================
// СЕКЦИЯ 2: МОДИФИКАТОР ТАКТИЛЬНОГО СЖАТИЯ ПРИ НАЖАТИИ (BOUNCE CLICK)
// ============================================================================

fun Modifier.bounceClick(
    minScale: Float = 0.95f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) minScale else 1f,
        animationSpec = PureClockAnimationSpecs.SpringBouncy,
        label = "BounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onClick?.invoke()
                    }
                }
            }
        }
}

// ============================================================================
// СЕКЦИЯ 3: ЭФФЕКТ ПУЛЬСИРУЮЩЕГО СВЕЧЕНИЯ (BREATHING GLOW)
// ============================================================================
fun Modifier.breathingGlow(
    color: Color,
    minAlpha: Float = 0.10f,
    maxAlpha: Float = 0.35f,
    durationMillis: Int = 2000
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "BreathingTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingAlpha"
    )

    this.drawWithContent {
        drawContent()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.75f
            )
        )
    }
}

// ============================================================================
// СЕКЦИЯ 4: СОСТОЯНИЕ И РЕАКТИВНАЯ АНИМАЦИЯ КАРТОЧКИ БУДИЛЬНИКА
// ============================================================================
@Composable
fun rememberAlarmCardAnimation(isEnabled: Boolean): AlarmCardAnimState {
    val transitionProgress by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "AlarmProgress"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.45f,
        animationSpec = tween(durationMillis = 300),
        label = "ContentAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.02f else 1.0f,
        animationSpec = PureClockAnimationSpecs.SpringBouncy,
        label = "CardScale"
    )

    return remember(transitionProgress, contentAlpha, scale) {
        AlarmCardAnimState(
            progress = transitionProgress,
            contentAlpha = contentAlpha,
            scale = scale
        )
    }
}

data class AlarmCardAnimState(
    val progress: Float,
    val contentAlpha: Float,
    val scale: Float
)

// ============================================================================
// СЕКЦИЯ 5: КАСКАДНАЯ ХОРЕОГРАФИЯ ПОЯВЛЕНИЯ ЭЛЕМЕНТОВ (STAGGERED ENTRANCE)
// ============================================================================
fun Modifier.staggeredEntrance(
    index: Int = 0,
    initialOffsetY: Dp = 40.dp,
    delayPerItemMs: Int = 50,
    baseDurationMs: Int = 400
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = baseDurationMs,
            delayMillis = index * delayPerItemMs,
            easing = FastOutSlowInEasing
        ),
        label = "EntranceAlpha"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else initialOffsetY.value,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "EntranceOffsetY"
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "EntranceScale"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY * density
        this.scaleX = scale
        this.scaleY = scale
    }
}