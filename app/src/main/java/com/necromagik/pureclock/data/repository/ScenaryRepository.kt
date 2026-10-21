package com.necromagik.pureclock.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.necromagik.pureclock.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScenaryRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "pureclock_scenary_prefs",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val _scenarios = MutableStateFlow(loadFromStorage())
    val scenariosFlow: StateFlow<List<ScenaryItem>> = _scenarios.asStateFlow()

    private val _alarmBindings = MutableStateFlow(loadBindings(KEY_ALARM_BINDINGS))
    val alarmBindingsFlow: StateFlow<Map<Long, String>> = _alarmBindings.asStateFlow()

    private val _timerBindings = MutableStateFlow(loadTimerBindings())
    val timerBindingsFlow: StateFlow<Map<String, String>> = _timerBindings.asStateFlow()

    companion object {
        private const val KEY_SCENARIOS_LIST = "saved_scenarios_json"
        private const val KEY_ALARM_BINDINGS = "alarm_scenario_bindings_json"
        private const val KEY_TIMER_BINDINGS = "timer_scenario_bindings_json"

        @Volatile
        private var INSTANCE: ScenaryRepository? = null

        fun getInstance(context: Context): ScenaryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScenaryRepository(context).also { INSTANCE = it }
            }
        }
    }

    private fun loadFromStorage(): List<ScenaryItem> {
        val json = prefs.getString(KEY_SCENARIOS_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ScenaryItem>>() {}.type
            gson.fromJson<List<ScenaryItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadBindings(key: String): Map<Long, String> {
        val json = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<Long, String>>() {}.type
            gson.fromJson<Map<Long, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadTimerBindings(): Map<String, String> {
        val json = prefs.getString(KEY_TIMER_BINDINGS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveToStorage(list: List<ScenaryItem>) {
        _scenarios.value = list
        prefs.edit {
            putString(KEY_SCENARIOS_LIST, gson.toJson(list))
        }
    }

    fun bindAlarmToScenario(alarmId: Long, scenarioId: String?) {
        val map = _alarmBindings.value.toMutableMap()
        if (scenarioId.isNullOrBlank()) {
            map.remove(alarmId)
        } else {
            map[alarmId] = scenarioId
        }
        _alarmBindings.value = map
        prefs.edit { putString(KEY_ALARM_BINDINGS, gson.toJson(map)) }
    }

    fun getScenarioForAlarm(alarmId: Long): ScenaryItem? {
        val scenarioId = _alarmBindings.value[alarmId] ?: return null
        return _scenarios.value.firstOrNull { it.id == scenarioId }
    }

    fun bindTimerToScenario(timerId: String, scenarioId: String?) {
        val map = _timerBindings.value.toMutableMap()
        if (scenarioId.isNullOrBlank()) {
            map.remove(timerId)
        } else {
            map[timerId] = scenarioId
        }
        _timerBindings.value = map
        prefs.edit { putString(KEY_TIMER_BINDINGS, gson.toJson(map)) }
    }

    fun getScenarioForTimer(timerId: String): ScenaryItem? {
        val scenarioId = _timerBindings.value[timerId] ?: return null
        return _scenarios.value.firstOrNull { it.id == scenarioId }
    }

    fun addOrUpdateScenario(item: ScenaryItem) {
        val current = _scenarios.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
        } else {
            current.add(0, item)
        }
        saveToStorage(current)
    }

    fun toggleScenario(id: String, enabled: Boolean) {
        val current = _scenarios.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveToStorage(current)
    }

    fun deleteScenario(id: String) {
        val current = _scenarios.value.filterNot { it.id == id }
        saveToStorage(current)

        val updatedAlarms = _alarmBindings.value.filterValues { it != id }
        _alarmBindings.value = updatedAlarms
        prefs.edit { putString(KEY_ALARM_BINDINGS, gson.toJson(updatedAlarms)) }

        val updatedTimers = _timerBindings.value.filterValues { it != id }
        _timerBindings.value = updatedTimers
        prefs.edit { putString(KEY_TIMER_BINDINGS, gson.toJson(updatedTimers)) }
    }

    fun getQuickTemplates(): List<ScenaryItem> = listOf(
        ScenaryItem(
            title = "Охота за закатом",
            targetType = ScenaryTargetType.ALARM,
            triggerType = ScenaryTriggerType.SUNSET,
            offsetMinutes = -40,
            baseHour = 18,
            baseMinute = 15,
            condition = ScenaryConditionType.CLEAR_SKY,
            actions = listOf(
                ScenaryActionStep(
                    type = ScenaryActionType.CUSTOM_NOTIFICATION,
                    parameter = "40 минут до захода — иди лови хороший свет"
                ),
                ScenaryActionStep(
                    type = ScenaryActionType.SHOW_CHECKLIST,
                    parameter = "Штатив, ND-фильтр, Запасной аккумулятор, Карта памяти"
                )
            )
        ),
        ScenaryItem(
            title = "Луна в зените",
            targetType = ScenaryTargetType.ALARM,
            triggerType = ScenaryTriggerType.FULL_MOON,
            offsetMinutes = -15,
            baseHour = 0,
            baseMinute = 45,
            condition = ScenaryConditionType.CLEAR_SKY,
            actions = listOf(
                ScenaryActionStep(
                    type = ScenaryActionType.CUSTOM_NOTIFICATION,
                    parameter = "Луна в зените, небо чистое — время снимать"
                ),
                ScenaryActionStep(
                    type = ScenaryActionType.SHOW_BRIEFING,
                    parameter = "Астро-сводка: видимость и фаза Луны"
                )
            )
        ),
        ScenaryItem(
            title = "Утренний рассвет и туман",
            targetType = ScenaryTargetType.ALARM,
            triggerType = ScenaryTriggerType.SUNRISE,
            offsetMinutes = -60,
            baseHour = 5,
            baseMinute = 20,
            condition = ScenaryConditionType.WEEKENDS_ONLY,
            actions = listOf(
                ScenaryActionStep(
                    type = ScenaryActionType.SHOW_BRIEFING,
                    parameter = "Температура, влажность и точка росы"
                ),
                ScenaryActionStep(
                    type = ScenaryActionType.OPEN_APP,
                    parameter = "Камера"
                )
            )
        ),
        ScenaryItem(
            title = "Чайный ритуал",
            targetType = ScenaryTargetType.TIMER,
            triggerType = ScenaryTriggerType.TIMER_COUNTDOWN,
            timerDurationSeconds = 180L,
            condition = ScenaryConditionType.ALWAYS,
            actions = listOf(
                ScenaryActionStep(
                    type = ScenaryActionType.CUSTOM_NOTIFICATION,
                    parameter = "Заваривание завершено"
                )
            )
        )
    )
}