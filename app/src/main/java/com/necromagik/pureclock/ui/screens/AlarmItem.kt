package com.necromagik.pureclock.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.animation.rememberAlarmCardAnimation
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig

data class AlarmUiModel(
    val id: Long,
    val time: String,
    val days: String,
    val label: String = "",
    val timeRemaining: String = "",
    val isEnabled: Boolean
)

// ============================================================================
// СЕКЦИЯ 1: ИНТЕРАКТИВНАЯ КАРТОЧКА БУДИЛЬНИКА С HAPTIC FEEDBACK И АНИМАЦИЕЙ
// ============================================================================
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
    onSelectToggle: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val cardCornerRadius = LocalPureClockConfig.current.cardCornerRadius

    fun triggerCustomHaptic(isTurningOn: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val timings = if (isTurningOn) longArrayOf(0, 30, 50, 30) else longArrayOf(0, 120)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    val animState = rememberAlarmCardAnimation(alarm.isEnabled)

    val borderWidth by animateDpAsState(
        targetValue = if (alarm.isEnabled && animState.progress > 0f && animState.progress < 1f) 3.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BorderWidth"
    )

    val rainbowColors = listOf(
        MaterialTheme.colorScheme.outline,
        Color(0xFFFF5252),
        Color(0xFFFFEB3B),
        Color(0xFF00E676),
        accentColor
    )

    val currentRainbowColor = rainbowColors[(animState.progress * (rainbowColors.size - 1)).toInt()]

    val animatedBorderColor by animateColorAsState(
        targetValue = if (alarm.isEnabled) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        animationSpec = tween(500),
        label = "BorderColor"
    )

    val baseSurfaceColor = MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = borderWidth,
            brush = if (animState.progress > 0f && animState.progress < 1f) {
                Brush.horizontalGradient(listOf(currentRainbowColor, accentColor, currentRainbowColor))
            } else {
                SolidColor(animatedBorderColor)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCardView) Modifier.height(115.dp) else Modifier.wrapContentHeight())
            .graphicsLayer {
                scaleX = animState.scale
                scaleY = animState.scale
            }
            .background(
                brush = if (alarm.isEnabled) {
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.10f),
                            baseSurfaceColor
                        )
                    )
                } else {
                    SolidColor(baseSurfaceColor)
                },
                shape = RoundedCornerShape(cardCornerRadius)
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
    ) {
// ============================================================================
// СЕКЦИЯ 2: ОТОБРАЖЕНИЕ В РЕЖИМЕ СЕТКИ (GRID CARD VIEW)
// ============================================================================
        if (isCardView) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = alarm.time,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.alpha(animState.contentAlpha)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    triggerCustomHaptic(!isSelected)
                                    onSelectToggle()
                                },
                                colors = CheckboxDefaults.colors(checkedColor = accentColor),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            IconButton(
                                onClick = onEditClick,
                                modifier = Modifier
                                    .size(24.dp)
                                    .bounceClick()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Редактировать",
                                    tint = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = if (alarm.isEnabled) "Вкл" else "Выкл",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Column(modifier = Modifier.alpha(animState.contentAlpha)) {
                    Text(
                        text = alarm.days,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (alarm.label.isNotEmpty()) {
                        Text(
                            text = alarm.label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
// ============================================================================
// СЕКЦИЯ 3: ОТОБРАЖЕНИЕ В РЕЖИМЕ СТРОЧНОГО СПИСКА (LIST ITEM VIEW)
// ============================================================================
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(animState.contentAlpha)
                ) {
                    Text(
                        text = alarm.time,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (alarm.label.isNotEmpty()) "${alarm.days} • ${alarm.label}" else alarm.days,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            triggerCustomHaptic(!isSelected)
                            onSelectToggle()
                        },
                        colors = CheckboxDefaults.colors(checkedColor = accentColor)
                    )
                } else {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = {
                            val nextState = !alarm.isEnabled
                            triggerCustomHaptic(nextState)
                            onToggle()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = accentColor,
                            checkedThumbColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}