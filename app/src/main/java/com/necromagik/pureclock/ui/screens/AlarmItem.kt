package com.necromagik.pureclock.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val isEnabled: Boolean
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
    onSelectToggle: () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor
    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    // Плавный рост при включении (Spring) и возврат в 1.0f при выключении
    val cardScale by animateFloatAsState(
        targetValue = if (alarm.isEnabled) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "AlarmScaleToggle"
    )

    // Градиент рамки: плавный переход от тускло-серого к насыщенному акцентному
    val borderGradientProgress by animateFloatAsState(
        targetValue = if (alarm.isEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "AlarmBorderColorTransition"
    )

    val currentBorderBrush = remember(borderGradientProgress, accentColor) {
        val startColor = Color.Gray.copy(alpha = 0.2f + 0.3f * borderGradientProgress)
        val endColor = Color(
            red = (Color.Gray.red * (1 - borderGradientProgress) + accentColor.red * borderGradientProgress),
            green = (Color.Gray.green * (1 - borderGradientProgress) + accentColor.green * borderGradientProgress),
            blue = (Color.Gray.blue * (1 - borderGradientProgress) + accentColor.blue * borderGradientProgress),
            alpha = 0.25f + 0.75f * borderGradientProgress
        )
        Brush.linearGradient(
            listOf(startColor, endColor)
        )
    }

    val timeColor by animateColorAsState(
        targetValue = if (alarm.isEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "TimeColor"
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
            .then(if (isCardView) Modifier.height(134.dp) else Modifier.wrapContentHeight())
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .pure3DEffect(
                shape = cardShape,
                accentColor = if (alarm.isEnabled) accentColor else Color.DarkGray,
                depthDp = if (alarm.isEnabled) themeConfig.depthIntensityDp else 2.dp,
                is3dEnabled = themeConfig.is3dEnabled,
                isGlowEnabled = alarm.isEnabled && themeConfig.isGlowEnabled,
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
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = if (alarm.isEnabled) 0.15f else 0.06f))
                                .clickable { onEditClick() }
                                .bounceClick(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = if (alarm.isEnabled) accentColor else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = alarm.days,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
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
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (alarm.label.isNotEmpty()) "${alarm.days} • ${alarm.label}" else alarm.days,
                        fontSize = 13.sp,
                        color = if (alarm.isEnabled) accentColor else Color.Gray,
                        fontWeight = FontWeight.Medium
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