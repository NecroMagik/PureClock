package com.necromagik.pureclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ============================================================================
// СЕКЦИЯ 1: СТРУКТУРА ТАБЛИЦЫ ALARMS (ROOM ENTITY)
// ============================================================================
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val hour: Int,                  // Час (0..23)
    val minute: Int,                // Минута (0..59)
    val daysOfWeek: Int = 0,        // Битовая маска дней недели (1..64)
    val specificDateMillis: Long? = null, // Фича OnePlus: Выбранный точечный день в календаре
    val skippedDateMillis: Long? = null,  // Умный пропуск 1 ближайшего сигнала

// ============================================================================
// СЕКЦИЯ 2: ПАРАМЕТРЫ СИГНАЛА И РИНГТОНА
// ============================================================================
    val isEnabled: Boolean = true,  // Включён/выключен
    val label: String = "",         // Название/заметка
    val ringtoneUri: String? = null,// Мелодия звонка
    val isVibrate: Boolean = true,  // Вибрация
    val snoozeTimeMinutes: Int = 10 // Время отсрочки
)