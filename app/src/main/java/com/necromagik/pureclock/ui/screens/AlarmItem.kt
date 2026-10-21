package com.necromagik.pureclock.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.components.PureSwitch
import com.necromagik.pureclock.ui.components.SwitchIconType
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect

data class AlarmUiModel(
    val id: Long,
    val time: String,
    val days: String,
    val label: String = "",
    val timeRemaining: String = "",
    val isEnabled: Boolean,
    val isSkippedToday: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmItem(
    alarm: AlarmUiModel,
    isCardView: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onDeleteSingleClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val skippedColor = remember(accentColor) {
        // Тёплый полутон между системным акцентом и янтарным для состояния "Пропущен сегодня"
        Color(
            red = (accentColor.red * 0.55f + 1.0f * 0.45f).coerceIn(0f, 1f),
            green = (accentColor.green * 0.55f + 0.72f * 0.45f).coerceIn(0f, 1f),
            blue = (accentColor.blue * 0.55f + 0.30f * 0.45f).coerceIn(0f, 1f),
            alpha = 0.85f
        )
    }

    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    // Отложенный на сегодня будильник остаётся приподнятым (1.01f), но чуть спокойнее обычного (1.02f)
    val targetScale = when {
        !alarm.isEnabled -> 1.0f
        alarm.isSkippedToday -> 1.01f
        else -> 1.02f
    }

    val cardScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "AlarmScaleToggle"
    )

    val borderGradientProgress by animateFloatAsState(
        targetValue = when {
            !alarm.isEnabled -> 0f
            alarm.isSkippedToday -> 0.65f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 350),
        label = "AlarmBorderColorTransition"
    )

    val effectiveStateColor by animateColorAsState(
        targetValue = when {
            !alarm.isEnabled -> Color.DarkGray
            alarm.isSkippedToday -> skippedColor
            else -> accentColor
        },
        animationSpec = tween(durationMillis = 320),
        label = "EffectiveStateColor"
    )

    val currentBorderBrush = remember(borderGradientProgress, effectiveStateColor) {
        val startColor = Color.Gray.copy(alpha = 0.2f + 0.3f * borderGradientProgress)
        val endColor = Color(
            red = (Color.Gray.red * (1 - borderGradientProgress) + effectiveStateColor.red * borderGradientProgress),
            green = (Color.Gray.green * (1 - borderGradientProgress) + effectiveStateColor.green * borderGradientProgress),
            blue = (Color.Gray.blue * (1 - borderGradientProgress) + effectiveStateColor.blue * borderGradientProgress),
            alpha = 0.25f + 0.75f * borderGradientProgress
        )
        Brush.linearGradient(listOf(startColor, endColor))
    }

    val timeColor by animateColorAsState(
        targetValue = when {
            !alarm.isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            alarm.isSkippedToday -> skippedColor
            isCardView -> accentColor
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "TimeColor"
    )

    val subtitleColor by animateColorAsState(
        targetValue = when {
            !alarm.isEnabled -> Color.Gray
            alarm.isSkippedToday -> skippedColor
            else -> accentColor
        },
        animationSpec = tween(280),
        label = "SubtitleColor"
    )

    fun triggerCustomHaptic(isTurningOn: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val timings = if (isTurningOn) longArrayOf(0, 25, 35, 25) else longArrayOf(0, 80)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCardView) Modifier.height(138.dp) else Modifier.wrapContentHeight())
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .pure3DEffect(
                shape = cardShape,
                accentColor = effectiveStateColor,
                depthDp = if (alarm.isEnabled) themeConfig.depthIntensityDp else 2.dp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = alarm.isEnabled && !alarm.isSkippedToday && themeConfig.isGlowEnabled,
                surfaceColor = MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (alarm.isEnabled) 1.8.dp else 1.dp,
                brush = currentBorderBrush,
                shape = cardShape
            )
            .combinedClickable(
                onClick = {
                    when {
                        isSelectionMode -> {
                            triggerCustomHaptic(true)
                            onSelectToggle()
                        }
                        isCardView -> {
                            val nextState = !alarm.isEnabled
                            triggerCustomHaptic(nextState)
                            onToggle()
                        }
                        else -> onEditClick()
                    }
                },
                onLongClick = {
                    triggerCustomHaptic(false)
                    onLongClick()
                }
            )
            .padding(16.dp)
    ) {
        if (isCardView) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = alarm.time,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = timeColor
                    )

                    if (isSelectionMode) {
                        // В режиме редактирования: сверху чекбокс (на месте карандаша), а прямо под ним — урна в стиле карандаша
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    triggerCustomHaptic(!isSelected)
                                    onSelectToggle()
                                },
                                colors = CheckboxDefaults.colors(checkedColor = accentColor),
                                modifier = Modifier.size(28.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .clickable {
                                        triggerCustomHaptic(false)
                                        onDeleteSingleClick()
                                    }
                                    .bounceClick(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(effectiveStateColor.copy(alpha = if (alarm.isEnabled) 0.15f else 0.06f))
                                .clickable { onEditClick() }
                                .bounceClick(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = if (alarm.isEnabled) effectiveStateColor else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = alarm.days,
                        fontSize = 12.sp,
                        fontWeight = if (alarm.isSkippedToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (alarm.isSkippedToday) skippedColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (alarm.label.isNotEmpty()) {
                        Text(
                            text = alarm.label,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarm.time,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = timeColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (alarm.label.isNotEmpty()) "${alarm.days} • ${alarm.label}" else alarm.days,
                        fontSize = 13.sp,
                        color = subtitleColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f)
                ) {
                    // В списке урна появляется рядом с чекбоксом, который заменяет переключатель
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .clickable {
                                    triggerCustomHaptic(false)
                                    onDeleteSingleClick()
                                }
                                .bounceClick(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                triggerCustomHaptic(!isSelected)
                                onSelectToggle()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = accentColor)
                        )
                    }
                }

                if (!isSelectionMode) {
                    PureSwitch(
                        checked = alarm.isEnabled,
                        iconType = SwitchIconType.SOUNDS,
                        onCheckedChange = {
                            val nextState = !alarm.isEnabled
                            triggerCustomHaptic(nextState)
                            onToggle()
                        }
                    )
                }
            }
        }
    }
}