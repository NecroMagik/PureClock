package com.necromagik.pureclock.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.necromagik.pureclock.ui.animation.bounceClick
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val RU_LOCALE = Locale.forLanguageTag("ru")
private const val PAGER_START_OFFSET = 1200

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmCalendarView(
    selectedHour: Int,
    selectedMinute: Int,
    daysMask: Int = 0,
    extraDates: Set<LocalDate> = emptySet(),
    excludedDates: Set<LocalDate> = emptySet(),
    onDateToggled: (LocalDate) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val initialYearMonth = remember { YearMonth.now() }
    val pagerState = rememberPagerState(initialPage = PAGER_START_OFFSET) { PAGER_START_OFFSET * 2 }
    val coroutineScope = rememberCoroutineScope()

    val currentMonth = remember(pagerState.currentPage) {
        val monthOffset = (pagerState.currentPage - PAGER_START_OFFSET).toLong()
        initialYearMonth.plusMonths(monthOffset)
    }

    val today = remember { LocalDate.now() }
    val nowTime = LocalTime.now()

    val isTimePastToday = remember(selectedHour, selectedMinute, nowTime) {
        val selectedTime = LocalTime.of(selectedHour, selectedMinute)
        selectedTime.isBefore(nowTime)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Шапка календаря: синхронизированные стрелки и название
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
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

        Spacer(modifier = Modifier.height(4.dp))

        // Дни недели (ПН..ВС)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Пейджер месяцев, точно следящий за пальцем
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) { page ->
            val pageMonth = remember(page) {
                val offset = (page - PAGER_START_OFFSET).toLong()
                initialYearMonth.plusMonths(offset)
            }

            MonthGrid(
                month = pageMonth,
                today = today,
                isTimePastToday = isTimePastToday,
                daysMask = daysMask,
                extraDates = extraDates,
                excludedDates = excludedDates,
                accentColor = accentColor,
                onDateToggled = onDateToggled
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    isTimePastToday: Boolean,
    daysMask: Int,
    extraDates: Set<LocalDate>,
    excludedDates: Set<LocalDate>,
    accentColor: Color,
    onDateToggled: (LocalDate) -> Unit
) {
    val firstDayOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfWeekOffset = firstDayOfMonth.dayOfWeek.value - 1

    // Фиксируем ровно 6 строк для всех месяцев
    val fixedRows = 6
    var dayCounter = 1

    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until fixedRows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col

                    if (cellIndex < firstDayOfWeekOffset || dayCounter > daysInMonth) {
                        // Пустая ячейка с сохранением точных пропорций сетки
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    } else {
                        val currentDate = month.atDay(dayCounter)

                        val dayBit = 1 shl (currentDate.dayOfWeek.value - 1)
                        val isDayOfWeekMasked = (daysMask and dayBit) != 0
                        val isExcluded = excludedDates.contains(currentDate)
                        val isExtra = extraDates.contains(currentDate)

                        val isToday = currentDate == today
                        val isStrictlyPastDate = currentDate.isBefore(today)
                        val isTodayTimePast = isToday && isTimePastToday
                        val isOccurredInPast = isStrictlyPastDate || isTodayTimePast

                        val isDayActive = (isDayOfWeekMasked && !isExcluded) || isExtra
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
                                        isDayActive && !isOccurredInPast -> accentColor
                                        isDayActive && isOccurredInPast -> accentColor.copy(alpha = 0.25f)
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
                                fontSize = 13.sp,
                                fontWeight = if (isDayActive || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isDayActive && !isOccurredInPast -> Color.Black
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