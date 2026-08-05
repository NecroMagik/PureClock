package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.necromagik.pureclock.alarm.TimerReceiver
import com.necromagik.pureclock.alarm.TimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class TimerState {
    IDLE, RUNNING, PAUSED, COMPLETED
}

enum class TimerViewMode {
    CAROUSEL, GRID
}

enum class TimerExecutionMode {
    CHAIN, PARALLEL
}

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

    private val _timersList = MutableStateFlow(
        listOf(TimerItem(label = "Таймер 1", initialTimeSeconds = 300L, remainingSeconds = 300L, remainingMillis = 300000L))
    )
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
        startTickerLoop()
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
                        hasActiveTimers = true
                        val diffMillis = item.endTimestampMillis - now

                        if (diffMillis > 0) {
                            val leftSec = (diffMillis + 999L) / 1000L
                            currentList[i] = item.copy(
                                remainingMillis = diffMillis,
                                remainingSeconds = leftSec
                            )
                        } else {
                            currentList[i] = item.copy(
                                remainingMillis = 0L,
                                remainingSeconds = 0L,
                                state = TimerState.COMPLETED
                            )
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
            TimerService.stopService(getApplication())
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
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == TimerViewMode.CAROUSEL) TimerViewMode.GRID else TimerViewMode.CAROUSEL
    }

    fun toggleExecutionMode() {
        _executionMode.value = if (_executionMode.value == TimerExecutionMode.CHAIN) TimerExecutionMode.PARALLEL else TimerExecutionMode.CHAIN
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

                // Форматируем длительность (например "15 сек" или "05:00")
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
        }
        val hasRunning = _timersList.value.any { it.state == TimerState.RUNNING }
        updateServiceState(hasRunning)
    }

    fun deleteTimer(id: String) {
        val list = _timersList.value.toMutableList()
        if (list.size > 1) {
            TimerReceiver.cancelTimerAlarm(getApplication(), id)
            list.removeAll { it.id == id }
            _timersList.value = list
            if (_currentTimerIndex.value >= list.size) {
                _currentTimerIndex.value = list.size - 1
            }
        }
        val hasRunning = _timersList.value.any { it.state == TimerState.RUNNING }
        updateServiceState(hasRunning)
    }

    fun addNewTimer(seconds: Long) {
        val list = _timersList.value.toMutableList()
        val nextNum = list.size + 1
        val millis = seconds * 1000L
        val newItem = TimerItem(
            label = "Таймер $nextNum",
            initialTimeSeconds = seconds,
            remainingSeconds = seconds,
            remainingMillis = millis,
            state = TimerState.IDLE
        )
        list.add(newItem)
        _timersList.value = list

        if (!_isChainRunning.value) {
            _currentTimerIndex.value = list.size - 1
        }
    }
}