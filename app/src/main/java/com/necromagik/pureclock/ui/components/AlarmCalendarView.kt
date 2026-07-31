package com.necromagik.pureclock.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val RU_LOCALE = Locale("ru")

// ============================================================================
// СЕКЦИЯ 1: КАЛЕНДАРНЫЙ ИНТЕРФЕЙС И РАСЧЕТ АКТИВНЫХ БУДУЩИХ ДНЕЙ
// ============================================================================
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AlarmCalendarView(
    selectedHour: Int,
    selectedMinute: Int,
    daysMask: Int = 0, // Битовая маска выбранных дней недели (1=ПН, 2=ВТ, 4=СР, 8=ЧТ...)
    extraDates: Set<LocalDate> = emptySet(), // Точечно добавленные даты
    excludedDates: Set<LocalDate> = emptySet(), // Исключенные даты
    onDateToggled: (LocalDate) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var isNextMonthTransition by remember { mutableStateOf(true) }

    val today = LocalDate.now()
    val nowTime = LocalTime.now()

    // Проверяем, прошло ли выбранное время сегодня
    val isTimePastToday = remember(selectedHour, selectedMinute, nowTime) {
        val selectedTime = LocalTime.of(selectedHour, selectedMinute)
        selectedTime.isBefore(nowTime)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Шапка календаря: переключение месяцев
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    isNextMonthTransition = false
                    currentMonth = currentMonth.minusMonths(1)
                },
                modifier = Modifier.bounceClick()
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Предыдущий месяц",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            val monthTitle = currentMonth.month
                .getDisplayName(TextStyle.FULL_STANDALONE, RU_LOCALE)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(RU_LOCALE) else it.toString() }
            Text(
                text = "$monthTitle ${currentMonth.year}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    isNextMonthTransition = true
                    currentMonth = currentMonth.plusMonths(1)
                },
                modifier = Modifier.bounceClick()
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Следующий месяц",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

// ============================================================================
// СЕКЦИЯ 2: ЗАГОЛОВКИ ДНЕЙ НЕДЕЛИ (ПН..ВС)
// ============================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val daysOfWeek = remember {
                listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                )
            }
            daysOfWeek.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, RU_LOCALE)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(RU_LOCALE) else it.toString() },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var totalDragDistance by remember { mutableFloatStateOf(0f) }

// ============================================================================
// СЕКЦИЯ 3: ОТОБРАЖЕНИЕ ЯЧЕЕК С УЧЕТОМ АКТИВНОСТИ И ПРОШЕДШИХ СИГНАЛОВ
// ============================================================================
        AnimatedContent(
            targetState = currentMonth,
            transitionSpec = {
                if (isNextMonthTransition) {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { width -> width } + fadeOut()
                    )
                }
            },
            label = "CalendarMonthTransition"
        ) { month ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(month) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDragDistance < -50f) {
                                    isNextMonthTransition = true
                                    currentMonth = currentMonth.plusMonths(1)
                                } else if (totalDragDistance > 50f) {
                                    isNextMonthTransition = false
                                    currentMonth = currentMonth.minusMonths(1)
                                }
                                totalDragDistance = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragDistance += dragAmount
                            }
                        )
                    }
            ) {
                val firstDayOfMonth = month.atDay(1)
                val daysInMonth = month.lengthOfMonth()
                val firstDayOfWeekOffset = firstDayOfMonth.dayOfWeek.value - 1

                val totalCells = daysInMonth + firstDayOfWeekOffset
                val rows = (totalCells + 6) / 7

                var dayCounter = 1

                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            if (cellIndex < firstDayOfWeekOffset || dayCounter > daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val currentDate = month.atDay(dayCounter)

                                val dayBit = 1 shl (currentDate.dayOfWeek.value - 1)
                                val isDayOfWeekMasked = (daysMask and dayBit) != 0
                                val isExcluded = excludedDates.contains(currentDate)
                                val isExtra = extraDates.contains(currentDate)

                                val isToday = currentDate == today
                                val isStrictlyPastDate = currentDate.isBefore(today)

                                // Проверяем, считается ли сегодняшний день уже прошедшим по времени
                                val isTodayTimePast = isToday && isTimePastToday

                                // День считается прошедшим витком, если дата строго в прошлом или это сегодня и время ушло
                                val isOccurredInPast = isStrictlyPastDate || isTodayTimePast

                                // Активность визуального выделения дня
                                val isDayActive = (isDayOfWeekMasked && !isExcluded) || isExtra

                                // Кликабельность: прошлые календарные даты некликабельны
                                val isClickable = !isStrictlyPastDate

                                val animatedScale by animateFloatAsState(
                                    targetValue = if (isDayActive && !isOccurredInPast) 1.05f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "CellScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .graphicsLayer {
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                        }
                                        .then(if (isClickable) Modifier.bounceClick() else Modifier)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                // Активный будущий день вызова (Яркий акцент)
                                                isDayActive && !isOccurredInPast -> accentColor
                                                // Прошедший виток акцентного дня (Приглушенный контур/фон)
                                                isDayActive && isOccurredInPast -> accentColor.copy(alpha = 0.25f)
                                                // Сегодняшний день (Мягкое подсвечивание)
                                                isToday -> accentColor.copy(alpha = 0.12f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (isExcluded) {
                                                Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                                            } else Modifier
                                        )
                                        .clickable(enabled = isClickable) { onDateToggled(currentDate) }
                                        .alpha(if (isStrictlyPastDate) 0.35f else 1.0f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayCounter.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (isDayActive || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isDayActive && !isOccurredInPast -> MaterialTheme.colorScheme.surface
                                            isDayActive && isOccurredInPast -> accentColor
                                            isStrictlyPastDate -> Color.Gray
                                            isToday -> accentColor
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                dayCounter++
                            }
                        }
                    }
                }
            }
        }
    }
}