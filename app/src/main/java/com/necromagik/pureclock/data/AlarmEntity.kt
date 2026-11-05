package com.necromagik.pureclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int = 0,
    val specificDateMillis: Long? = null,
    val skippedDateMillis: Long? = null,

    val extraDatesStr: String? = null,
    val excludedDatesStr: String? = null,

    val isEnabled: Boolean = true,
    val label: String = "",
    val ringtoneUri: String? = null,
    val isVibrate: Boolean = true,
    val snoozeTimeMinutes: Int = 10
) {
    fun parseExtraDates(): Set<LocalDate> {
        if (extraDatesStr.isNullOrBlank()) {
            return specificDateMillis?.let {
                setOf(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
            } ?: emptySet()
        }
        return extraDatesStr.split(",")
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
    }

    fun parseExcludedDates(): Set<LocalDate> {
        if (excludedDatesStr.isNullOrBlank()) return emptySet()
        return excludedDatesStr.split(",")
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
    }

    /**
     * Проверяет, активен ли пропуск именно на будущее время.
     * Если дата и время пропуска уже остались в прошлом, он считается просроченным.
     */
    fun isSkipActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val skip = skippedDateMillis ?: return false
        return skip > nowMillis
    }

    fun getSkippedLocalDate(): LocalDate? {
        val skip = skippedDateMillis ?: return null
        return Instant.ofEpochMilli(skip).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}