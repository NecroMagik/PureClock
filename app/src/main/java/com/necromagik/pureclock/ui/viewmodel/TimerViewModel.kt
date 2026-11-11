package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.necromagik.pureclock.alarm.TimerReceiver
import com.necromagik.pureclock.alarm.TimerService
import com.necromagik.pureclock.util.ClockNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class TimerState { IDLE, RUNNING, PAUSED, COMPLETED }
enum class TimerViewMode { CAROUSEL, GRID }
enum class TimerExecutionMode { CHAIN, PARALLEL }

data class TimerItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String = "Таймер",
    val baseTimeSeconds: Long = 300L, // Неизменное изначальное время, заданное при создании
    val initialTimeSeconds: Long = 300L, // Длительность текущего круга с учётом начисленных +1 мин
    var remainingSeconds: Long = 300L,
    var remainingMillis: Long = 300_000L,
    var state: TimerState = TimerState.IDLE,
    var endTimestampMillis: Long = 0L
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pureclock_timers_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _timersList = MutableStateFlow<List<TimerItem>>(emptyList())
    val timersList: StateFlow<List<TimerItem>> = _timersList.asStateFlow()

    private val _currentTimerIndex = MutableStateFlow(0)
    val currentTimerIndex: StateFlow<Int> = _currentTimerIndex.asStateFlow()

    private val _isChainRunning = MutableStateFlow(false)
    val isChainRunning: StateFlow<Boolean> = _isChainRunning.asStateFlow()

    private val _viewMode = MutableStateFlow(TimerViewMode.CAROUSEL)
    val viewMode: StateFlow<TimerViewMode> = _viewMode.asStateFlow()

    private val _executionMode = MutableStateFlow(TimerExecutionMode.CHAIN)
    val executionMode: StateFlow<TimerExecutionMode> = _executionMode.asStateFlow()

    private var tickerJob: Job? = null
    private var isInternalSaving = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "saved_timers_list" && !isInternalSaving) {
            loadTimersFromStorage()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadTimersFromStorage()
        startTickerLoop()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private fun loadTimersFromStorage() {
        val json = prefs.getString("saved_timers_list", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<TimerItem>>() {}.type
                val saved: List<TimerItem> = gson.fromJson(json, type) ?: emptyList()

                val now = System.currentTimeMillis()
                val restored = saved.map { item ->
                    // Защита от старых JSON, где baseTimeSeconds мог быть 0L
                    val safeBase = if (item.baseTimeSeconds > 0L) item.baseTimeSeconds else item.initialTimeSeconds
                    val safeItem = if (item.baseTimeSeconds <= 0L) item.copy(baseTimeSeconds = safeBase) else item

                    if (safeItem.state == TimerState.RUNNING) {
                        val diff = safeItem.endTimestampMillis - now
                        if (diff <= 0) {
                            safeItem.copy(state = TimerState.COMPLETED, remainingMillis = 0L, remainingSeconds = 0L)
                        } else {
                            safeItem.copy(remainingMillis = diff, remainingSeconds = (diff + 999L) / 1000L)
                        }
                    } else safeItem
                }
                _timersList.value = restored
            } catch (_: Exception) {
                _timersList.value = emptyList()
            }
        } else {
            _timersList.value = emptyList()
        }
    }

    private fun saveTimersToStorage() {
        isInternalSaving = true
        try {
            val json = gson.toJson(_timersList.value)
            prefs.edit().putString("saved_timers_list", json).apply()
        } finally {
            isInternalSaving = false
        }
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                var hasRunningTimers = false
                var hasRunningOrPausedTimers = false
                var stateChangedToCompleted = false
                val currentList = _timersList.value.toMutableList()

                for (i in currentList.indices) {
                    val item = currentList[i]
                    if (item.state == TimerState.RUNNING) {
                        val diffMillis = item.endTimestampMillis - now

                        if (diffMillis > 0) {
                            hasRunningTimers = true
                            hasRunningOrPausedTimers = true
                            val leftSec = (diffMillis + 999L) / 1000L
                            currentList[i] = item.copy(
                                remainingMillis = diffMillis,
                                remainingSeconds = leftSec
                            )
                        } else {
                            stateChangedToCompleted = true
                            currentList[i] = item.copy(
                                remainingMillis = 0L,
                                remainingSeconds = 0L,
                                state = TimerState.COMPLETED
                            )
                            TimerReceiver.cancelTimerAlarm(getApplication(), item.id)
                            TimerService.triggerAlarm(getApplication(), item.id, item.label, item.initialTimeSeconds)
                            onSingleTimerFinished(i, currentList)
                        }
                    } else if (item.state == TimerState.PAUSED) {
                        hasRunningOrPausedTimers = true
                    }
                }

                _timersList.value = currentList
                if (stateChangedToCompleted) {
                    saveTimersToStorage()
                }

                if (_isChainRunning.value != hasRunningTimers) {
                    _isChainRunning.value = hasRunningTimers
                }

                if (!hasRunningOrPausedTimers && !TimerService.isRinging) {
                    ClockNotificationManager.cancel(getApplication(), ClockNotificationManager.NOTIFICATION_ID_TIMER)
                }

                delay(100L)
            }
        }
    }

    private fun updateServiceState(hasActiveOrPaused: Boolean) {
        if (hasActiveOrPaused) {
            TimerService.startService(getApplication())
        } else {
            if (!TimerService.isRinging) {
                TimerService.stopService(getApplication())
            }
        }
    }

    private fun formatDurationText(seconds: Long): String {
        return if (seconds < 60) {
            "$seconds сек"
        } else {
            val minutes = seconds / 60
            val remainingSec = seconds % 60
            if (remainingSec == 0L) {
                "$minutes мин"
            } else {
                String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSec)
            }
        }
    }

    private fun onSingleTimerFinished(index: Int, list: MutableList<TimerItem>) {
        if (_executionMode.value == TimerExecutionMode.CHAIN) {
            if (index + 1 < list.size) {
                val nextItem = list[index + 1]
                if (nextItem.remainingMillis > 0) {
                    _currentTimerIndex.value = index + 1
                    val triggerTime = System.currentTimeMillis() + nextItem.remainingMillis
                    list[index + 1] = nextItem.copy(
                        state = TimerState.RUNNING,
                        endTimestampMillis = triggerTime
                    )

                    val durationText = formatDurationText(nextItem.initialTimeSeconds)
                    TimerReceiver.scheduleTimerAlarm(
                        getApplication(),
                        nextItem.id,
                        nextItem.label,
                        durationText,
                        triggerTime
                    )
                }
            }
        }
        saveTimersToStorage()
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == TimerViewMode.CAROUSEL) TimerViewMode.GRID else TimerViewMode.CAROUSEL
    }

    fun addTimerToChain(label: String, seconds: Long) {
        val list = _timersList.value.toMutableList()
        val millis = seconds * 1000L
        val newItem = TimerItem(
            label = label.ifBlank { "Таймер ${list.size + 1}" },
            baseTimeSeconds = seconds,
            initialTimeSeconds = seconds,
            remainingSeconds = seconds,
            remainingMillis = millis,
            state = TimerState.IDLE
        )
        list.add(newItem)
        _timersList.value = list
        saveTimersToStorage()
    }

    fun toggleSingleTimer(id: String) {
        val list = _timersList.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }

        if (index != -1) {
            val item = list[index]
            val now = System.currentTimeMillis()

            if (item.state == TimerState.COMPLETED || TimerService.isRinging) {
                TimerService.dismissRingingTimer(getApplication(), item.id)
                resetSingleTimer(id)
                return
            }

            if (item.state == TimerState.RUNNING) {
                TimerReceiver.cancelTimerAlarm(getApplication(), item.id)
                val exactRemaining = (item.endTimestampMillis - now).coerceAtLeast(0L)
                list[index] = item.copy(
                    state = TimerState.PAUSED,
                    remainingMillis = exactRemaining,
                    remainingSeconds = (exactRemaining + 999L) / 1000L
                )
            } else if (item.remainingMillis > 0L) {
                val triggerTime = now + item.remainingMillis
                list[index] = item.copy(
                    state = TimerState.RUNNING,
                    endTimestampMillis = triggerTime
                )

                val durationText = formatDurationText(item.initialTimeSeconds)
                TimerReceiver.scheduleTimerAlarm(
                    getApplication(),
                    item.id,
                    item.label,
                    durationText,
                    triggerTime
                )
            }

            _timersList.value = list
            saveTimersToStorage()

            val updatedItem = list[index]
            val hasRunningOrPaused = list.any { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }

            if (updatedItem.state == TimerState.RUNNING || updatedItem.state == TimerState.PAUSED) {
                ClockNotificationManager.showTimerNotification(
                    context = getApplication(),
                    timerId = updatedItem.id,
                    remainingMillis = updatedItem.remainingMillis,
                    label = updatedItem.label,
                    isRunning = updatedItem.state == TimerState.RUNNING,
                    initialTimeSeconds = updatedItem.initialTimeSeconds
                )
            } else if (!hasRunningOrPaused) {
                ClockNotificationManager.cancel(getApplication(), ClockNotificationManager.NOTIFICATION_ID_TIMER)
            }

            updateServiceState(hasRunningOrPaused)
        }
    }

    /**
     * Сбрасывает таймер к исходному состоянию (baseTimeSeconds), которое было задано пользователем при создании.
     */
    fun resetSingleTimer(id: String) {
        TimerService.dismissRingingTimer(getApplication(), id)

        val list = _timersList.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = list[index]
            TimerReceiver.cancelTimerAlarm(getApplication(), item.id)

            // Возвращаем строго к базовому времени (например, 5 минут, а не 5+4)
            val resetSeconds = if (item.baseTimeSeconds > 0L) item.baseTimeSeconds else item.initialTimeSeconds
            val resetMillis = resetSeconds * 1000L

            list[index] = item.copy(
                initialTimeSeconds = resetSeconds,
                remainingSeconds = resetSeconds,
                remainingMillis = resetMillis,
                state = TimerState.IDLE,
                endTimestampMillis = 0L
            )
            _timersList.value = list
            saveTimersToStorage()
        }

        val hasRunningOrPaused = _timersList.value.any { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
        if (!hasRunningOrPaused) {
            ClockNotificationManager.cancel(getApplication(), ClockNotificationManager.NOTIFICATION_ID_TIMER)
            TimerService.stopService(getApplication())
        } else {
            updateServiceState(true)
        }
    }

    fun deleteTimer(id: String) {
        TimerService.dismissRingingTimer(getApplication(), id)

        val list = _timersList.value.toMutableList()
        TimerReceiver.cancelTimerAlarm(getApplication(), id)
        list.removeAll { it.id == id }
        _timersList.value = list
        saveTimersToStorage()

        if (_currentTimerIndex.value >= list.size) {
            _currentTimerIndex.value = (list.size - 1).coerceAtLeast(0)
        }
        val hasRunningOrPaused = _timersList.value.any { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
        if (!hasRunningOrPaused) {
            ClockNotificationManager.cancel(getApplication(), ClockNotificationManager.NOTIFICATION_ID_TIMER)
            TimerService.stopService(getApplication())
        } else {
            updateServiceState(true)
        }
    }

    /**
     * Прибавляет секунды (+1 минута) к существующему таймеру, не затирая его baseTimeSeconds.
     */
    fun extendTimer(timerId: String?, label: String, extraSeconds: Long = 60L) {
        val list = _timersList.value.toMutableList()
        val index = if (!timerId.isNullOrEmpty()) list.indexOfFirst { it.id == timerId } else -1
        val now = System.currentTimeMillis()

        val finalId: String
        val initSec: Long
        val newRemainingSec: Long
        val newRemainingMs: Long
        val isTimerRunning: Boolean

        if (index != -1) {
            val item = list[index]
            finalId = item.id

            // Если таймер бежит — увеличиваем оставшееся время и сдвигаем дедлайн на extraSeconds
            if (item.state == TimerState.RUNNING) {
                isTimerRunning = true
                val currentRemainingMs = (item.endTimestampMillis - now).coerceAtLeast(0L)
                newRemainingMs = currentRemainingMs + (extraSeconds * 1000L)
                newRemainingSec = (newRemainingMs + 999L) / 1000L
                val newEndTimestamp = now + newRemainingMs
                initSec = item.initialTimeSeconds + extraSeconds

                list[index] = item.copy(
                    initialTimeSeconds = initSec,
                    remainingSeconds = newRemainingSec,
                    remainingMillis = newRemainingMs,
                    state = TimerState.RUNNING,
                    endTimestampMillis = newEndTimestamp
                )

                val durationText = formatDurationText(newRemainingSec)
                TimerReceiver.scheduleTimerAlarm(
                    getApplication(),
                    finalId,
                    item.label,
                    durationText,
                    newEndTimestamp
                )
            } else {
                // Если таймер на паузе, в режиме ожидания (IDLE) или завершён
                isTimerRunning = item.state == TimerState.COMPLETED
                newRemainingSec = item.remainingSeconds + extraSeconds
                newRemainingMs = newRemainingSec * 1000L
                initSec = item.initialTimeSeconds + extraSeconds
                val newEndTimestamp = if (isTimerRunning) now + newRemainingMs else 0L

                list[index] = item.copy(
                    initialTimeSeconds = initSec,
                    remainingSeconds = newRemainingSec,
                    remainingMillis = newRemainingMs,
                    state = if (isTimerRunning) TimerState.RUNNING else item.state,
                    endTimestampMillis = newEndTimestamp
                )

                if (isTimerRunning) {
                    val durationText = formatDurationText(newRemainingSec)
                    TimerReceiver.scheduleTimerAlarm(
                        getApplication(),
                        finalId,
                        item.label,
                        durationText,
                        newEndTimestamp
                    )
                }
            }
        } else {
            finalId = timerId ?: java.util.UUID.randomUUID().toString()
            initSec = extraSeconds
            newRemainingSec = extraSeconds
            newRemainingMs = extraSeconds * 1000L
            val triggerTime = now + newRemainingMs
            isTimerRunning = true

            val newItem = TimerItem(
                id = finalId,
                label = label.ifBlank { "Таймер" },
                baseTimeSeconds = extraSeconds,
                initialTimeSeconds = initSec,
                remainingSeconds = newRemainingSec,
                remainingMillis = newRemainingMs,
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
            list.add(newItem)

            val durationText = formatDurationText(extraSeconds)
            TimerReceiver.scheduleTimerAlarm(
                getApplication(),
                finalId,
                label,
                durationText,
                triggerTime
            )
        }

        _timersList.value = list
        saveTimersToStorage()

        ClockNotificationManager.showTimerNotification(
            context = getApplication(),
            timerId = finalId,
            remainingMillis = newRemainingMs,
            label = label,
            isRunning = isTimerRunning,
            initialTimeSeconds = initSec
        )

        updateServiceState(true)
    }
}