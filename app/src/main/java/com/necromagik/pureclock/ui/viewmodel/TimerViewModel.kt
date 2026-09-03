package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.necromagik.pureclock.alarm.TimerReceiver
import com.necromagik.pureclock.alarm.TimerService
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
val initialTimeSeconds: Long = 300L,
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

    init {
        loadTimersFromStorage()
        startTickerLoop()
    }

    private fun loadTimersFromStorage() {
        val json = prefs.getString("saved_timers_list", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<TimerItem>>() {}.type
                val saved: List<TimerItem> = gson.fromJson(json, type)

                val now = System.currentTimeMillis()
                val restored = saved.map { item ->
                    if (item.state == TimerState.RUNNING) {
                        val diff = item.endTimestampMillis - now
                        if (diff <= 0) {
                            item.copy(state = TimerState.COMPLETED, remainingMillis = 0L, remainingSeconds = 0L)
                        } else {
                            item.copy(remainingMillis = diff, remainingSeconds = (diff + 999L) / 1000L)
                        }
                    } else item
                }
                _timersList.value = restored
            } catch (_: Exception) {
                _timersList.value = emptyList()
            }
        }
    }

    private fun saveTimersToStorage() {
        val json = gson.toJson(_timersList.value)
        prefs.edit().putString("saved_timers_list", json).apply()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                var hasActiveTimers = false
                val currentList = _timersList.value.toMutableList()

                for (i in currentList.indices) {
                    val item = currentList[i]
                    if (item.state == TimerState.RUNNING) {
                        val diffMillis = item.endTimestampMillis - now

                        if (diffMillis > 0) {
                            hasActiveTimers = true
                            val leftSec = (diffMillis + 999L) / 1000L
                            currentList[i] = item.copy(
                                remainingMillis = diffMillis,
                            remainingSeconds = leftSec
                            )
                        } else {
                            // Таймер завершился — активируем экран и звук тревоги
                            currentList[i] = item.copy(
                                remainingMillis = 0L,
                            remainingSeconds = 0L,
                            state = TimerState.COMPLETED
                            )
                            TimerReceiver.cancelTimerAlarm(getApplication(), item.id)
                            TimerService.triggerAlarm(getApplication(), item.id, item.label, item.initialTimeSeconds)
                            onSingleTimerFinished(i, currentList)
                        }
                    }
                }

                _timersList.value = currentList

                if (_isChainRunning.value != hasActiveTimers) {
                    _isChainRunning.value = hasActiveTimers
                    updateServiceState(hasActiveTimers)
                }

                delay(100L)
            }
        }
    }

    private fun updateServiceState(isRunning: Boolean) {
        if (isRunning) {
            TimerService.startService(getApplication())
        } else {
            // Не выключаем службу, пока звенит тревога завершенного таймера
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
        }

        val hasRunning = _timersList.value.any { it.state == TimerState.RUNNING }
        updateServiceState(hasRunning)
    }

    fun resetSingleTimer(id: String) {
        val list = _timersList.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = list[index]
            TimerReceiver.cancelTimerAlarm(getApplication(), item.id)
            val initMillis = item.initialTimeSeconds * 1000L
            list[index] = item.copy(
                remainingSeconds = item.initialTimeSeconds,
            remainingMillis = initMillis,
            state = TimerState.IDLE
            )
            _timersList.value = list
            saveTimersToStorage()
        }
        val hasRunning = _timersList.value.any { it.state == TimerState.RUNNING }
        updateServiceState(hasRunning)
    }

    fun deleteTimer(id: String) {
        val list = _timersList.value.toMutableList()
        TimerReceiver.cancelTimerAlarm(getApplication(), id)
        list.removeAll { it.id == id }
        _timersList.value = list
        saveTimersToStorage()

        if (_currentTimerIndex.value >= list.size) {
            _currentTimerIndex.value = (list.size - 1).coerceAtLeast(0)
        }
        val hasRunning = _timersList.value.any { it.state == TimerState.RUNNING }
        updateServiceState(hasRunning)
    }

    fun extendTimer(timerId: String?, label: String, extraSeconds: Long) {
        val list = _timersList.value.toMutableList()
        val index = if (!timerId.isNullOrEmpty()) list.indexOfFirst { it.id == timerId } else -1
        val extraMillis = extraSeconds * 1000L
        val triggerTime = System.currentTimeMillis() + extraMillis

        if (index != -1) {
            val item = list[index]
            list[index] = item.copy(
                initialTimeSeconds = extraSeconds,
                remainingSeconds = extraSeconds,
                remainingMillis = extraMillis,
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
        } else {
            // Если карточка была удалена, восстанавливаем её живой
            val newItem = TimerItem(
                id = timerId ?: java.util.UUID.randomUUID().toString(),
                label = label.ifBlank { "Таймер" },
                initialTimeSeconds = extraSeconds,
                remainingSeconds = extraSeconds,
                remainingMillis = extraMillis,
                state = TimerState.RUNNING,
                endTimestampMillis = triggerTime
            )
            list.add(newItem)
        }

        _timersList.value = list
        saveTimersToStorage()

        val durationText = formatDurationText(extraSeconds)
        val activeId = if (index != -1) list[index].id else list.last().id
        TimerReceiver.scheduleTimerAlarm(
            getApplication(),
            activeId,
            label,
            durationText,
            triggerTime
        )
        updateServiceState(true)
    }
}