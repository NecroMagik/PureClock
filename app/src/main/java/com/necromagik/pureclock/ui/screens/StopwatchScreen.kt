package com.necromagik.pureclock.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.breathingGlow
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.LapRecord
import com.necromagik.pureclock.ui.viewmodel.LapType
import com.necromagik.pureclock.ui.viewmodel.StopwatchViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StopwatchScreen(
    stopwatchViewModel: StopwatchViewModel
) {
    val themeConfig = LocalPureClockConfig.current
    val accentColor = themeConfig.accentColor

    val elapsedMillis by stopwatchViewModel.elapsedMillis.collectAsState()
    val currentLapMillis by stopwatchViewModel.currentLapMillis.collectAsState()
    val isRunning by stopwatchViewModel.isRunning.collectAsState()
    val laps by stopwatchViewModel.laps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
    .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // УЛЬТРАПЛАВНЫЙ ДВОЙНОЙ ХРОНОГРАФ
        DualChronographDial(
            elapsedMillis = elapsedMillis,
        currentLapMillis = currentLapMillis,
        isRunning = isRunning,
        accentColor = accentColor,
        themeConfig = themeConfig
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ШАПКА ТАБЛИЦЫ КРУГОВ (ФИКСИРОВАНА СВЕРХУ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
        ) {
        Text("КРУГ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("ВРЕМЯ КРУГА", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("ОБЩЕЕ ВРЕМЯ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // СПИСОК КРУГОВ: ПРИВЯЗАН К ВЕРХУ, НОВЫЕ ПАДАЮТ СВЕРХУ, СТАРЫЕ СМЕЩАЮТСЯ ВНИЗ
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
        .weight(1f)
        ) {
        items(
            items = laps,
            key = { it.lapNumber }
        ) { lap ->
        Box(
            modifier = Modifier.animateItem(
                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                placementSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            )
        ) {
            LapItemCard(
                lap = lap,
            isLatest = laps.firstOrNull()?.lapNumber == lap.lapNumber,
            accentColor = accentColor
            )
        }
    }
    }
    }
}

@Composable
private fun DualChronographDial(
    elapsedMillis: Long,
    currentLapMillis: Long,
    isRunning: Boolean,
    accentColor: Color,
    themeConfig: com.necromagik.pureclock.ui.theme.PureClockThemeConfig
) {
    val totalSeconds = elapsedMillis / 1000f
    val mainAngle by animateFloatAsState(
        targetValue = (totalSeconds % 60f) * 6f,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "MainHandSmooth"
    )

    val lapSeconds = currentLapMillis / 1000f
    val subAngle by animateFloatAsState(
        targetValue = (lapSeconds % 60f) * 6f,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "SubHandSmooth"
    )

    val formattedMainTime = remember(elapsedMillis) {
        val minutes = (elapsedMillis / 60000) % 60
        val seconds = (elapsedMillis / 1000) % 60
        val millis = (elapsedMillis % 1000) / 10
        String.format(Locale.ROOT, "%02d:%02d,%02d", minutes, seconds, millis)
    }

    Box(
        modifier = Modifier
            .size(290.dp)
    .pure3DEffect(
        shape = CircleShape,
    accentColor = if (isRunning) accentColor else Color.DarkGray,
    depthDp = themeConfig.depthIntensityDp,
    is3dEnabled = themeConfig.is3dEnabled,
    isGlowEnabled = isRunning && themeConfig.isGlowEnabled,
    surfaceColor = MaterialTheme.colorScheme.surface
    ),
    contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        .then(if (isRunning) Modifier.breathingGlow(accentColor, minAlpha = 0.04f, maxAlpha = 0.16f) else Modifier)
        ) {
        val center = this.center
        val outerRadius = size.minDimension / 2f - 16.dp.toPx()

        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6).toDouble())
            val isMajor = i % 5 == 0
            val lineLength = if (isMajor) 14.dp.toPx() else 6.dp.toPx()
            val strokeW = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx()

            val startX = (center.x + (outerRadius - lineLength) * sin(angle)).toFloat()
            val startY = (center.y - (outerRadius - lineLength) * cos(angle)).toFloat()
            val endX = (center.x + outerRadius * sin(angle)).toFloat()
            val endY = (center.y - outerRadius * cos(angle)).toFloat()

            drawLine(
                color = if (isMajor) Color.Gray.copy(alpha = 0.85f) else Color.Gray.copy(alpha = 0.3f),
                start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
            )
        }

        val subCenterY = center.y + 58.dp.toPx()
        val subRadius = 42.dp.toPx()

        drawCircle(
            color = Color.Gray.copy(alpha = 0.15f),
        center = Offset(center.x, subCenterY),
        radius = subRadius,
        style = Stroke(width = 2.dp.toPx())
        )

        rotate(degrees = subAngle, pivot = Offset(center.x, subCenterY)) {
            drawLine(
                color = accentColor.copy(alpha = 0.9f),
                start = Offset(center.x, subCenterY),
            end = Offset(center.x, subCenterY - subRadius + 8.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
            )
        }
        drawCircle(color = accentColor, center = Offset(center.x, subCenterY), radius = 3.5.dp.toPx())

        rotate(degrees = mainAngle, pivot = center) {
            drawLine(
                color = if (isRunning) accentColor else Color.White,
            start = Offset(center.x, center.y + 22.dp.toPx()),
            end = Offset(center.x, center.y - outerRadius + 8.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
            )
        }
        drawCircle(color = Color.White, center = center, radius = 5.5.dp.toPx())
        drawCircle(color = accentColor, center = center, radius = 2.5.dp.toPx())
    }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset(y = (-42).dp)
        ) {
        RollingStopwatchTimeText(
            formattedTime = formattedMainTime,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    }
}

@Composable
private fun LapItemCard(
    lap: LapRecord,
isLatest: Boolean,
accentColor: Color
) {
    val statusColor = when (lap.type) {
        LapType.BEST -> Color(0xFF00E676)
            LapType.WORST -> Color(0xFFFF5252)
            LapType.NEUTRAL -> if (isLatest) accentColor else Color.White
    }

    val formattedLapTime = remember(lap.lapTimeMillis) {
        val minutes = (lap.lapTimeMillis / 60000) % 60
        val seconds = (lap.lapTimeMillis / 1000) % 60
        val millis = (lap.lapTimeMillis % 1000) / 10
        String.format(Locale.ROOT, "%02d:%02d,%02d", minutes, seconds, millis)
    }

    val formattedTotalTime = remember(lap.totalTimeMillis) {
        val minutes = (lap.totalTimeMillis / 60000) % 60
        val seconds = (lap.totalTimeMillis / 1000) % 60
        val millis = (lap.totalTimeMillis % 1000) / 10
        String.format(Locale.ROOT, "%02d:%02d,%02d", minutes, seconds, millis)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isLatest) {
                        listOf(accentColor.copy(alpha = 0.15f), MaterialTheme.colorScheme.surface)
                    } else {
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
                    }
                )
            )
            .border(
                width = if (isLatest || lap.type != LapType.NEUTRAL) 1.5.dp else 1.dp,
                color = when {
                    lap.type == LapType.BEST -> Color(0xFF00E676).copy(alpha = 0.7f)
                    lap.type == LapType.WORST -> Color(0xFFFF5252).copy(alpha = 0.7f)
                    isLatest -> accentColor.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
            .background(statusColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format(Locale.ROOT, "#%02d", lap.lapNumber),
                fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = statusColor
            )
        }

        Text(
            text = formattedLapTime,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = statusColor
        )

        Text(
            text = formattedTotalTime,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Gray
        )
    }
    }
}

// ============================================================================
// БАРАБАННЫЙ НАКАТ ДЛЯ ЦИФР СЕКУНДОМЕРА
// ============================================================================
@Composable
private fun RollingStopwatchTimeText(
    formattedTime: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        formattedTime.forEachIndexed { index, char ->
            if (char == ':' || char == ',') {
                Text(
                    text = char.toString(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color.copy(alpha = 0.5f)
                )
            } else {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { -it } + fadeIn()).togetherWith(
                            slideOutVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { it } + fadeOut()
                        )
                    },
                    label = "StopwatchRoll_$index"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color
                    )
                }
            }
        }
    }
}