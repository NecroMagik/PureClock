package com.necromagik.pureclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int = 0,
    val specificDateMillis: Long? = null,
    val skippedDateMillis: Long? = null,

    // Киллер-фича OxygenOS: включенные и исключенные даты календаря
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
                setOf(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
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
}