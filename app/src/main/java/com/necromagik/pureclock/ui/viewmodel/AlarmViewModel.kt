package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.necromagik.pureclock.alarm.AlarmScheduler
import com.necromagik.pureclock.data.AlarmEntity
import com.necromagik.pureclock.data.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).alarmDao()
    private val scheduler = AlarmScheduler(application)

    // ============================================================================
// СЕКЦИЯ 1: ПОТОК ДАННЫХ ВСЕХ БУДИЛЬНИКОВ (STATE FLOW)
// ============================================================================
    val alarms: StateFlow<List<AlarmEntity>> = dao.getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ============================================================================
// СЕКЦИЯ 2: СОХРАНЕНИЕ И ОБНОВЛЕНИЕ СИГНАЛА
// ============================================================================
    fun saveAlarm(
        id: Long = 0,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        specificDateMillis: Long?,
        label: String = "",
        isVibrate: Boolean = true
    ) {
        viewModelScope.launch {
            val alarm = AlarmEntity(
                id = id,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek,
                specificDateMillis = specificDateMillis,
                skippedDateMillis = null,
                isEnabled = true,
                label = label,
                isVibrate = isVibrate
            )

            val savedId = if (id == 0L) dao.insertAlarm(alarm) else { dao.updateAlarm(alarm); id }
            scheduler.schedule(alarm.copy(id = savedId))
        }
    }

    // ============================================================================
// СЕКЦИЯ 3: ПЕРЕКЛЮЧЕНИЕ, УМНЫЙ ПРОПУСК И УДАЛЕНИЕ
// ============================================================================
    fun toggleAlarm(alarm: AlarmEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            val validSpecificDate = if (alarm.specificDateMillis != null && alarm.specificDateMillis < nowMillis) {
                null // Дата уже в прошлом, сбрасываем
            } else {
                alarm.specificDateMillis
            }
            val updated = alarm.copy(isEnabled = isEnabled, skippedDateMillis = null, specificDateMillis = validSpecificDate)
            dao.updateAlarm(updated)
            if (isEnabled) scheduler.schedule(updated) else scheduler.cancel(updated)
        }
    }

    fun skipNextOccurrence(alarm: AlarmEntity, nextTriggerMillis: Long) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = true, skippedDateMillis = nextTriggerMillis)
            dao.updateAlarm(updated)
            scheduler.schedule(updated)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            scheduler.cancel(alarm)
            dao.deleteAlarm(alarm)
        }
    }
}