package com.necromagik.pureclock.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.necromagik.pureclock.ui.animation.bounceClick
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.SystemThemeUtils
import com.necromagik.pureclock.ui.theme.pure3DEffect

@Composable
fun ScenaryScreen(
    onRegisterFabAction: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    // Регистрируем действие для центральной кнопки (FAB) в док-панели MainScreen
    LaunchedEffect(Unit) {
        onRegisterFabAction {
            Toast.makeText(
                context,
                "Конструктор нового сценария скоро будет доступен!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ScenaryPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val cardShape = remember(themeConfig.cardCornerRadius) {
        RoundedCornerShape(themeConfig.cardCornerRadius)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 84.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.45f)
            val radius = size.minDimension * 0.45f * glowScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pure3DEffect(
                    shape = cardShape,
                    accentColor = accentColor,
                    depthDp = themeConfig.depthIntensityDp + 4.dp,
                    is3dEnabled = themeConfig.is3dEnabled,
                    isGlowEnabled = themeConfig.isGlowEnabled,
                    surfaceColor = MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 24.dp, vertical = 34.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = 0.95f + (glowScale - 0.9f) * 0.25f
                            scaleY = 0.95f + (glowScale - 0.9f) * 0.25f
                        }
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "СЦЕНАРИИ • LABS",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "В ожидании ВАШИХ идей",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Text(
                    text = "Поздравляю с переходом по секретному коду +10 -5 +3!",
                    fontSize = 15.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Text(
                    text = "Здесь будет конструктор пользовательских сценариев для будильников и таймеров. Разработка идёт полным ходом!",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(1.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://t.me/NecroThemik".toUri())
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = SystemThemeUtils.getContrastingColor(accentColor)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .bounceClick()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Следить за новостями", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}