package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LapType {
    BEST,    // Зеленый (самый быстрый)
    WORST,   // Красный (самый медленный)
    NEUTRAL  // Белый (обычный / незачёт)
}

data class LapRecord(
    val lapNumber: Int,
    val lapTimeMillis: Long,
    val totalTimeMillis: Long,
    val type: LapType = LapType.NEUTRAL
)

class StopwatchViewModel(application: Application) : AndroidViewModel(application) {

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _currentLapMillis = MutableStateFlow(0L)
    val currentLapMillis: StateFlow<Long> = _currentLapMillis.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _laps = MutableStateFlow<List<LapRecord>>(emptyList())
    val laps: StateFlow<List<LapRecord>> = _laps.asStateFlow()

    private var timerJob: Job? = null
    private var lastTimestamp = 0L

    fun toggleStartPause() {
        if (_isRunning.value) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        _isRunning.value = true
        lastTimestamp = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                val now = System.currentTimeMillis()
                val delta = now - lastTimestamp
                lastTimestamp = now

                _elapsedMillis.value += delta
                _currentLapMillis.value += delta

                delay(16L) // ~60 FPS для плавной анимированной стрелки
            }
        }
    }

    private fun pause() {
        _isRunning.value = false
        timerJob?.cancel()
    }

    fun reset() {
        pause()
        _elapsedMillis.value = 0L
        _currentLapMillis.value = 0L
        _laps.value = emptyList()
    }

    fun recordLap() {
        if (!_isRunning.value && _elapsedMillis.value == 0L) return

        val lapTime = _currentLapMillis.value
        val totalTime = _elapsedMillis.value
        val lapNumber = _laps.value.size + 1

        val newRawLaps = _laps.value.toMutableList().apply {
            add(0, LapRecord(lapNumber, lapTime, totalTime))
        }

        _laps.value = recalculateLapTypes(newRawLaps)
        _currentLapMillis.value = 0L
    }

    private fun recalculateLapTypes(rawLaps: List<LapRecord>): List<LapRecord> {
        if (rawLaps.size < 2) {
            return rawLaps.map { it.copy(type = LapType.NEUTRAL) }
        }

        val minTime = rawLaps.minOf { it.lapTimeMillis }
        val maxTime = rawLaps.maxOf { it.lapTimeMillis }

        return rawLaps.map { lap ->
            when {
                lap.lapTimeMillis == minTime && minTime != maxTime -> lap.copy(type = LapType.BEST)
                lap.lapTimeMillis == maxTime && minTime != maxTime -> lap.copy(type = LapType.WORST)
                else -> lap.copy(type = LapType.NEUTRAL)
            }
        }
    }
}