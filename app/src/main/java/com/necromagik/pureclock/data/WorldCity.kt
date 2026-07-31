package com.necromagik.pureclock.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================================
// СЕКЦИЯ 1: МОДЕЛЬ ДАННЫХ ГОРОДА МИРОВОГО ВРЕМЕНИ
// ============================================================================
data class WorldCity(
    val id: String,
    val cityName: String,
    val countryName: String,
    val timeZoneId: String,
    val searchKeywords: String
) {
    // ============================================================================
// СЕКЦИЯ 2: ВЫЧИСЛЕНИЕ ВРЕМЕНИ И РАЗНИЦЫ ДАТ/ЧАСОВЫХ ПОЯСОВ
// ============================================================================
    fun getFormattedTime(is24Hour: Boolean, shiftHours: Int = 0): String {
        val zoneId = ZoneId.of(timeZoneId)
        val zonedDateTime = ZonedDateTime.now(zoneId).plusHours(shiftHours.toLong())
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern))
    }

    // Расчёт текста разницы: "Сегодня", "Завтра", "Вчера" и количества часов ("+3 ч")
    fun getTimeDifferenceText(shiftHours: Int = 0): String {
        val systemZone = ZoneId.systemDefault()
        val ruLocale = Locale("ru")

        val systemNowDate = LocalDate.now(systemZone)
        val cityZonedDateTime = ZonedDateTime.now(ZoneId.of(timeZoneId)).plusHours(shiftHours.toLong())
        val cityDate = cityZonedDateTime.toLocalDate()

        val systemOffset = ZonedDateTime.now(systemZone).offset.totalSeconds
        val cityOffset = cityZonedDateTime.offset.totalSeconds
        val diffHours = (cityOffset - systemOffset) / 3600

        val diffText = when {
            diffHours > 0 -> "+$diffHours ч"
            diffHours < 0 -> "$diffHours ч"
            else -> "То же время"
        }

        val dayDiff = cityDate.toEpochDay() - systemNowDate.toEpochDay()
        val dayText = when (dayDiff) {
            0L -> "Сегодня"
            1L -> "Завтра"
            -1L -> "Вчера"
            else -> cityDate.format(DateTimeFormatter.ofPattern("d MMM", ruLocale))
        }

        return "$dayText, $diffText"
    }

    fun isDaytime(shiftHours: Int = 0): Boolean {
        val cityTime = ZonedDateTime.now(ZoneId.of(timeZoneId)).plusHours(shiftHours.toLong())
        val hour = cityTime.hour
        return hour in 6..21
    }
}