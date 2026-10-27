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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

// ============================================================================
// СЕКЦИЯ 6: ОДНОКРАТНАЯ ПРЕЗЕНТАЦИОННАЯ ХОРЕОГРАФИЯ ОБНОВЛЕНИЯ ДОК-БАРА
// ============================================================================
data class ScenaryDockChoreographyState(
    val fabOffsetY: Dp,
    val fabScale: Float,
    val notchRadius: Dp,
    val centerSpacerWidth: Dp,
    val isScenarySlotVisible: Boolean,
    val iconRevealProgress: Float,
    val iconWiggleDegrees: Float,
    val dockHighlightAlpha: Float,
    val spotlightRingProgress: Float,
    val showNewBadge: Boolean
)

@Composable
fun rememberScenaryDockChoreography(
    isFiveTabMode: Boolean,
    hasPlayedIntro: Boolean,
    replayTrigger: Int = 0,
    onIntroFinished: () -> Unit = {}
): ScenaryDockChoreographyState {
    val shouldSitImmediatelyInFiveTab = isFiveTabMode && hasPlayedIntro && replayTrigger == 0

    val fabOffsetAnim = remember { Animatable(if (shouldSitImmediatelyInFiveTab) -86f else -40f) }
    val fabScaleAnim = remember { Animatable(1f) }
    val notchRadiusAnim = remember { Animatable(if (shouldSitImmediatelyInFiveTab) 0f else 34f) }
    val centerSpacerAnim = remember { Animatable(if (shouldSitImmediatelyInFiveTab) 0f else 56f) }
    val iconRevealAnim = remember { Animatable(if (shouldSitImmediatelyInFiveTab) 1f else 0f) }
    val iconWiggleAnim = remember { Animatable(0f) }
    val dockHighlightAnim = remember { Animatable(0f) }
    val spotlightRingAnim = remember { Animatable(0f) }

    var isScenarySlotVisible by remember { mutableStateOf(shouldSitImmediatelyInFiveTab) }
    var showNewBadge by remember { mutableStateOf(false) }

    LaunchedEffect(isFiveTabMode, hasPlayedIntro, replayTrigger) {
        if (!isFiveTabMode) {
            // Возврат в 4-иконочный режим
            showNewBadge = false
            isScenarySlotVisible = false
            launch { iconRevealAnim.animateTo(0f, tween(250)) }
            launch { dockHighlightAnim.snapTo(0f) }
            launch { spotlightRingAnim.snapTo(0f) }
            launch { centerSpacerAnim.animateTo(56f, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { notchRadiusAnim.animateTo(34f, tween(380, easing = FastOutSlowInEasing)) }
            launch { fabOffsetAnim.animateTo(-40f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        } else if (!hasPlayedIntro || replayTrigger > 0) {
            // ПОКАЗАТЕЛЬНЫЙ СПЕКТАКЛЬ ОБНОВЛЕНИЯ ДОК-ПАНЕЛИ (Проигрывается 1 раз)
            showNewBadge = false
            isScenarySlotVisible = false
            fabOffsetAnim.snapTo(-40f)
            fabScaleAnim.snapTo(1f)
            notchRadiusAnim.snapTo(34f)
            centerSpacerAnim.snapTo(56f)
            iconRevealAnim.snapTo(0f)
            iconWiggleAnim.snapTo(0f)
            dockHighlightAnim.snapTo(0f)
            spotlightRingAnim.snapTo(0f)

            delay(280)

            // ФАЗА 1: Подготовка и пружинный замах FAB вниз перед взлётом + вспышка контура док-бара
            launch {
                dockHighlightAnim.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
            }
            fabScaleAnim.animateTo(0.88f, tween(160, easing = FastOutSlowInEasing))
            fabOffsetAnim.animateTo(-33f, tween(160, easing = FastOutSlowInEasing))

            // ФАЗА 2: Мощный взлёт FAB вверх над док-панелью и синхронное затягивание выемки док-бара
            launch {
                fabScaleAnim.animateTo(1.14f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                fabScaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
            }
            launch {
                fabOffsetAnim.animateTo(
                    targetValue = -94f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                fabOffsetAnim.animateTo(
                    targetValue = -86f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
            }
            launch {
                notchRadiusAnim.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            }
            launch {
                centerSpacerAnim.animateTo(0f, tween(540, easing = FastOutSlowInEasing))
            }

            // Ждём, пока FAB освободит место и иконки начнут смыкаться в центре
            delay(360)

            // ФАЗА 3: Выезд новой иконки "Сценарии" СПРАВА НАЛЕВО, сдвигающий остальные 4 вкладки влево
            isScenarySlotVisible = true
            showNewBadge = true

            launch {
                iconRevealAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            // Одновременно запускаем расходящееся световое кольцо вокруг новой иконки и её приветственное покачивание
            launch {
                delay(140)
                spotlightRingAnim.snapTo(0.05f)
                spotlightRingAnim.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
                spotlightRingAnim.snapTo(0f)
            }

            launch {
                delay(180)
                iconWiggleAnim.animateTo(14f, tween(130, easing = FastOutSlowInEasing))
                iconWiggleAnim.animateTo(-11f, tween(140, easing = FastOutSlowInEasing))
                iconWiggleAnim.animateTo(7f, tween(120, easing = FastOutSlowInEasing))
                iconWiggleAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            // ФАЗА 4: Мягкое затухание презентационной подсветки док-бара и фиксация флага
            delay(1100)
            dockHighlightAnim.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
            delay(1400)
            showNewBadge = false
            onIntroFinished()
        } else {
            // Постоянное состояние 5 вкладок (когда интро уже было показано ранее)
            isScenarySlotVisible = true
            showNewBadge = false
            fabOffsetAnim.snapTo(-86f)
            fabScaleAnim.snapTo(1f)
            notchRadiusAnim.snapTo(0f)
            centerSpacerAnim.snapTo(0f)
            iconRevealAnim.snapTo(1f)
            dockHighlightAnim.snapTo(0f)
            spotlightRingAnim.snapTo(0f)
        }
    }

    return ScenaryDockChoreographyState(
        fabOffsetY = fabOffsetAnim.value.dp,
        fabScale = fabScaleAnim.value,
        notchRadius = notchRadiusAnim.value.dp,
        centerSpacerWidth = centerSpacerAnim.value.dp,
        isScenarySlotVisible = isScenarySlotVisible,
        iconRevealProgress = iconRevealAnim.value,
        iconWiggleDegrees = iconWiggleAnim.value,
        dockHighlightAlpha = dockHighlightAnim.value,
        spotlightRingProgress = spotlightRingAnim.value,
        showNewBadge = showNewBadge
    )
}