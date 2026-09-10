package com.necromagik.pureclock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.necromagik.pureclock.ui.animation.bounceClick

@Composable
fun SettingsTopBarIcon(
    hasUpdate: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.bounceClick()
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Базовый значок шестеренки
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            // Зеленый индикатор со стрелочкой вверх
            AnimatedVisibility(
                visible = hasUpdate,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(7.dp)) {
                        val stroke = 1.4.dp.toPx()
                        val arrowColor = Color.Black

                        // Прямая линия стрелки вверх
                        drawLine(
                            color = arrowColor,
                            start = Offset(size.width * 0.5f, size.height * 0.85f),
                            end = Offset(size.width * 0.5f, size.height * 0.15f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )

                        // Наконечник стрелки
                        val path = Path().apply {
                            moveTo(size.width * 0.15f, size.height * 0.45f)
                            lineTo(size.width * 0.5f, size.height * 0.15f)
                            lineTo(size.width * 0.85f, size.height * 0.45f)
                        }
                        drawPath(
                            path = path,
                            color = arrowColor,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}