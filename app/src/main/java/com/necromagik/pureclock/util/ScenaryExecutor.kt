package com.necromagik.pureclock.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.necromagik.pureclock.R
import com.necromagik.pureclock.data.model.*
import com.necromagik.pureclock.data.repository.ScenaryRepository
import com.necromagik.pureclock.util.OriginIslandHelper.attachOriginIslandFlags
import com.necromagik.pureclock.util.OriginIslandHelper.promoteToOriginIsland
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Данные для всплывающего окна пост-срабатывания (Сводка + Чек-лист)
 */
data class PostTriggerUiPayload(
    val scenarioTitle: String,
    val customMessage: String? = null,
    val briefingTitle: String? = null,
    val checklistItems: List<String> = emptyList(),
    val nextTimerMinutes: Int? = null
)

object ScenaryExecutor {

    private const val TAG = "PureClock_SCENARY_EXEC"
    private const val CHANNEL_ID = "pureclock_scenary_action_channel"

    // Поток для отображения карточки сводки/чек-листа поверх MainScreen
    private val _activePostTriggerPayload = MutableStateFlow<PostTriggerUiPayload?>(null)
    val activePostTriggerPayload: StateFlow<PostTriggerUiPayload?> = _activePostTriggerPayload.asStateFlow()

    fun dismissPostTriggerPayload() {
        _activePostTriggerPayload.value = null
    }

    /**
     * Запускает выполнение сценария, привязанного к конкретному ID будильника
     */
    fun executeForAlarm(context: Context, alarmId: Long) {
        val repo = ScenaryRepository.getInstance(context)
        val scenario = repo.getScenarioForAlarm(alarmId) ?: return
        if (!scenario.isEnabled) return
        executeScenario(context, scenario)
    }

    /**
     * Запускает выполнение сценария, привязанного к конкретному ID таймера
     */
    fun executeForTimer(context: Context, timerId: String, onChainNextTimer: ((Int, String) -> Unit)? = null) {
        val repo = ScenaryRepository.getInstance(context)
        val scenario = repo.getScenarioForTimer(timerId) ?: return
        if (!scenario.isEnabled) return
        executeScenario(context, scenario, onChainNextTimer)
    }

    /**
     * Прямой запуск любого сценария (например, для теста из конструктора или после сигнала)
     */
    fun executeScenario(
        context: Context,
        scenario: ScenaryItem,
        onChainNextTimer: ((Int, String) -> Unit)? = null
    ) {
        if (!isConditionMet(scenario.condition)) {
            Log.i(TAG, "Сценарий '${scenario.title}' пропущен: не выполнено условие ${scenario.condition}")
            return
        }

        Log.i(TAG, "==> Выполнение сценария '${scenario.title}' (${scenario.actions.size} шагов)")

        var customMessage: String? = null
        var briefingTitle: String? = null
        val checklistItems = mutableListOf<String>()
        var nextTimerMin: Int? = null
        var appToOpen: String? = null

        scenario.actions.forEach { step ->
            when (step.type) {
                ScenaryActionType.CUSTOM_NOTIFICATION -> {
                    val msg = step.parameter.ifBlank { "Сценарий «${scenario.title}» выполнен" }
                    customMessage = msg
                    sendActionNotification(context, scenario.title, msg)
                }

                ScenaryActionType.SHOW_BRIEFING -> {
                    briefingTitle = step.parameter.ifBlank { "Актуальная сводка условий" }
                }

                ScenaryActionType.SHOW_CHECKLIST -> {
                    val items = step.parameter
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) {
                        checklistItems.addAll(items)
                    } else {
                        checklistItems.addAll(listOf("Проверить заряд устройства", "Подготовить экипировку"))
                    }
                }

                ScenaryActionType.OPEN_APP -> {
                    appToOpen = step.parameter.trim()
                }

                ScenaryActionType.CHAIN_NEXT_TIMER -> {
                    val mins = step.parameter.filter { it.isDigit() }.toIntOrNull() ?: 5
                    nextTimerMin = mins
                    onChainNextTimer?.invoke(mins, "${scenario.title} (Этап)")
                }
            }
        }

        // Если есть визуальные блоки (сводка, чек-лист или сообщение) — выводим карточку на экран
        if (customMessage != null || briefingTitle != null || checklistItems.isNotEmpty() || nextTimerMin != null) {
            _activePostTriggerPayload.value = PostTriggerUiPayload(
                scenarioTitle = scenario.title,
                customMessage = customMessage,
                briefingTitle = briefingTitle,
                checklistItems = checklistItems,
                nextTimerMinutes = nextTimerMin
            )
        }

        // Если задано открытие внешнего приложения (например, "Камера" или пакет)
        if (!appToOpen.isNullOrBlank()) {
            launchTargetApplication(context, appToOpen!!)
        }
    }

    private fun isConditionMet(condition: ScenaryConditionType): Boolean {
        val today = LocalDate.now().dayOfWeek
        val isWeekend = today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY
        return when (condition) {
            ScenaryConditionType.ALWAYS -> true
            ScenaryConditionType.CLEAR_SKY -> true // Базовый допуск (позже можно связать с погодным API)
            ScenaryConditionType.WEEKENDS_ONLY -> isWeekend
            ScenaryConditionType.WORKDAYS_ONLY -> !isWeekend
        }
    }

    private fun sendActionNotification(context: Context, title: String, message: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Сценарии PureClock",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Оповещения пользовательских сценариев"
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .promoteToOriginIsland(
                    chipText = "Сценарий",
                    targetTimeMillis = null
                )
                .build()
                .attachOriginIslandFlags()

            manager.notify(title.hashCode(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки уведомления сценария: ${e.message}")
        }
    }

    private fun launchTargetApplication(context: Context, targetQuery: String) {
        try {
            val lower = targetQuery.lowercase()
            val intent: Intent? = when {
                lower.contains("камер") || lower.contains("camera") -> {
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                }
                targetQuery.contains(".") -> {
                    context.packageManager.getLaunchIntentForPackage(targetQuery)
                }
                else -> {
                    context.packageManager.getLaunchIntentForPackage(targetQuery)
                        ?: Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).takeIf { lower.isEmpty() }
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Приложение «$targetQuery» не найдено", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось открыть приложение '$targetQuery': ${e.message}")
        }
    }
}