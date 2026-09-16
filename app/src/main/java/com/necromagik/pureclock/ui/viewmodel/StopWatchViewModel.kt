package com.necromagik.pureclock.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.necromagik.pureclock.alarm.StopwatchService
import com.necromagik.pureclock.data.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LapType {
    BEST,
    WORST,
    NEUTRAL
}

data class LapRecord(
    val lapNumber: Int,
    val lapTimeMillis: Long,
    val totalTimeMillis: Long,
    val type: LapType = LapType.NEUTRAL
)

class StopwatchViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _currentLapMillis = MutableStateFlow(0L)
    val currentLapMillis: StateFlow<Long> = _currentLapMillis.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _laps = MutableStateFlow<List<LapRecord>>(emptyList())
    val laps: StateFlow<List<LapRecord>> = _laps.asStateFlow()

    private var timerJob: Job? = null
    private var startTime = 0L

    fun toggleStartPause() {
        if (_isRunning.value) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        _isRunning.value = true
        startTime = System.currentTimeMillis() - _elapsedMillis.value
        timerJob?.cancel()

        StopwatchService.start(
            context = getApplication(),
            startTimeMillis = _elapsedMillis.value,
            lapCount = _laps.value.size
        )

        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                val now = System.currentTimeMillis()
                val currentElapsed = now - startTime
                _elapsedMillis.value = currentElapsed

                val lastTotalBeforeCurrentLap = _laps.value.sumOf { it.lapTimeMillis }
                _currentLapMillis.value = (currentElapsed - lastTotalBeforeCurrentLap).coerceAtLeast(0L)

                delay(8L)
            }
        }
    }

    private fun pause() {
        _isRunning.value = false
        timerJob?.cancel()

        StopwatchService.pause(
            context = getApplication(),
            startTimeMillis = _elapsedMillis.value,
            lapCount = _laps.value.size
        )
    }

    fun reset() {
        pause()
        _elapsedMillis.value = 0L
        _currentLapMillis.value = 0L
        _laps.value = emptyList()

        StopwatchService.stop(getApplication())
    }

    fun recordLap() {
        if (!_isRunning.value && _elapsedMillis.value == 0L) return

        triggerLapHaptic()

        val lapTime = _currentLapMillis.value
        val totalTime = _elapsedMillis.value
        val nextLapNumber = _laps.value.size + 1

        val newRawLaps = _laps.value.toMutableList().apply {
            add(0, LapRecord(nextLapNumber, lapTime, totalTime, LapType.NEUTRAL))
        }

        _laps.value = recalculateLapTypes(newRawLaps)

        val calculatedTotalLaps = _laps.value.sumOf { it.lapTimeMillis }
        _currentLapMillis.value = (_elapsedMillis.value - calculatedTotalLaps).coerceAtLeast(0L)

        if (_isRunning.value) {
            StopwatchService.updateLaps(
                context = getApplication(),
                startTimeMillis = _elapsedMillis.value,
                lapCount = _laps.value.size
            )
        }
    }

    private fun triggerLapHaptic() {
        if (!settingsManager.isStopwatchLapVibrate) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        } catch (_: Exception) {}
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