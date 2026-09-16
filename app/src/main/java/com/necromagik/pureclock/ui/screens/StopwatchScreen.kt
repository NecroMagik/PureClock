package com.necromagik.pureclock.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.breathingGlow
import com.necromagik.pureclock.ui.theme.LocalPureClockConfig
import com.necromagik.pureclock.ui.theme.pure3DEffect
import com.necromagik.pureclock.ui.viewmodel.LapRecord
import com.necromagik.pureclock.ui.viewmodel.LapType
import com.necromagik.pureclock.ui.viewmodel.StopwatchViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    val listState = rememberLazyListState()

    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val formattedCurrentLap = remember(currentLapMillis) {
        val minutes = (currentLapMillis / 60000) % 60
        val seconds = (currentLapMillis / 1000) % 60
        val millis = (currentLapMillis % 1000) / 10
        String.format(Locale.ROOT, "%02d:%02d,%02d", minutes, seconds, millis)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        DualChronographDial(
            elapsedMillis = elapsedMillis,
            currentLapMillis = currentLapMillis,
            isRunning = isRunning,
            accentColor = accentColor,
            themeConfig = themeConfig
        )

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
            visible = laps.isNotEmpty(),
            enter = fadeIn(tween(250)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(200)) + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "КРУГ #${laps.size + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor.copy(alpha = 0.85f),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formattedCurrentLap,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    style = LocalTextStyle.current.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
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
                    LapItemCard(lap = lap)
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
    var lastMainAngle by remember { mutableFloatStateOf(0f) }
    var lastSubAngle by remember { mutableFloatStateOf(0f) }

    var isResetting by remember { mutableStateOf(false) }
    val resetAnimMain = remember { Animatable(0f) }
    val resetAnimSub = remember { Animatable(0f) }

    LaunchedEffect(elapsedMillis) {
        if (elapsedMillis == 0L && (lastMainAngle > 0.1f || lastSubAngle > 0.1f)) {
            isResetting = true

            // Расчет кратчайшего пути к 0
            val remMain = (lastMainAngle % 360f + 360f) % 360f
            val targetMain = if (remMain > 180f) lastMainAngle + (360f - remMain) else lastMainAngle - remMain

            val remSub = (lastSubAngle % 360f + 360f) % 360f
            val targetSub = if (remSub > 180f) lastSubAngle + (360f - remSub) else lastSubAngle - remSub

            resetAnimMain.snapTo(lastMainAngle)
            resetAnimSub.snapTo(lastSubAngle)

            coroutineScope {
                launch {
                    resetAnimMain.animateTo(
                        targetValue = targetMain,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                launch {
                    resetAnimSub.animateTo(
                        targetValue = targetSub,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }

            lastMainAngle = 0f
            lastSubAngle = 0f
            isResetting = false
        }
    }

    val mainAngle = when {
        isResetting -> resetAnimMain.value
        elapsedMillis > 0L -> {
            val angle = (elapsedMillis / 1000f) * 6f
            lastMainAngle = angle
            angle
        }
        else -> 0f
    }

    val subAngle = when {
        isResetting -> resetAnimSub.value
        currentLapMillis > 0L -> {
            val angle = (currentLapMillis / 1000f) * 6f
            lastSubAngle = angle
            angle
        }
        else -> 0f
    }

    val formattedMainTime = remember(elapsedMillis) {
        val minutes = (elapsedMillis / 60000) % 60
        val seconds = (elapsedMillis / 1000) % 60
        val millis = (elapsedMillis % 1000) / 10
        String.format(Locale.ROOT, "%02d:%02d,%02d", minutes, seconds, millis)
    }

    Box(
        modifier = Modifier
            .size(300.dp)
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

            // Внешняя шкала
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

            // Малый дополнительный циферблат круга (Sub-dial)
            val subCenterY = center.y + 48.dp.toPx()
            val subRadius = 42.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                center = Offset(center.x, subCenterY),
                radius = subRadius
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.3f),
                center = Offset(center.x, subCenterY),
                radius = subRadius,
                style = Stroke(width = 1.5.dp.toPx())
            )

            val subTextPaint = Paint().apply {
                isAntiAlias = true
                textSize = 7.dp.toPx()
                color = android.graphics.Color.GRAY
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30).toDouble())
                val isMajor = i % 3 == 0
                val tickLen = if (isMajor) 5.dp.toPx() else 3.dp.toPx()

                val sX = (center.x + (subRadius - tickLen) * sin(angle)).toFloat()
                val sY = (subCenterY - (subRadius - tickLen) * cos(angle)).toFloat()
                val eX = (center.x + subRadius * sin(angle)).toFloat()
                val eY = (subCenterY - subRadius * cos(angle)).toFloat()

                drawLine(
                    color = if (isMajor) accentColor else Color.Gray.copy(alpha = 0.5f),
                    start = Offset(sX, sY),
                    end = Offset(eX, eY),
                    strokeWidth = if (isMajor) 1.8.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawContext.canvas.nativeCanvas.drawText("60", center.x, subCenterY - subRadius + 12.dp.toPx(), subTextPaint)
            drawContext.canvas.nativeCanvas.drawText("30", center.x, subCenterY + subRadius - 6.dp.toPx(), subTextPaint)

            // Стрелка малого циферблата
            rotate(degrees = subAngle, pivot = Offset(center.x, subCenterY)) {
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, subCenterY + 8.dp.toPx()),
                    end = Offset(center.x, subCenterY - subRadius + 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = accentColor, center = Offset(center.x, subCenterY), radius = 3.5.dp.toPx())
            drawCircle(color = Color.Black, center = Offset(center.x, subCenterY), radius = 1.5.dp.toPx())

            // Главная стрелка
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
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = formattedMainTime,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                style = LocalTextStyle.current.copy(
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
private fun LapItemCard(
    lap: LapRecord
) {
    val statusColor = when (lap.type) {
        LapType.BEST -> Color(0xFF00E676)
        LapType.WORST -> Color(0xFFFF5252)
        LapType.NEUTRAL -> MaterialTheme.colorScheme.onSurface
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
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (lap.type != LapType.NEUTRAL) 1.5.dp else 1.dp,
                color = when (lap.type) {
                    LapType.BEST -> Color(0xFF00E676).copy(alpha = 0.7f)
                    LapType.WORST -> Color(0xFFFF5252).copy(alpha = 0.7f)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(64.dp)
            ) {
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
                    color = statusColor,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
            }

            Text(
                text = formattedLapTime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
            )

            Text(
                text = formattedTotalTime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
            )
        }
    }
}