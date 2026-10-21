package com.necromagik.pureclock.data.model

import androidx.annotation.Keep
import java.util.Locale
import java.util.UUID

/**
 * Категория сценария (определяет, в какой вкладке появится связанная сущность)
 */
@Keep
enum class ScenaryTargetType(val title: String, val subtitle: String) {
    ALARM("Сценарный будильник", "Привязка ко времени или астрономическому событию"),
    TIMER("Сценарный таймер", "Цепочка отсчёта с действиями по завершении")
}

/**
 * Триггер запуска сценария
 */
@Keep
enum class ScenaryTriggerType(
    val title: String,
    val description: String,
    val isAstro: Boolean = false
) {
    FIXED_TIME("Точное время", "Классический запуск по часам и минутам", false),
    SUNRISE("Восход солнца", "Динамический расчёт рассвета", true),
    SUNSET("Заход солнца", "Динамический расчёт заката", true),
    GOLDEN_HOUR("Золотой час", "Мягкий свет перед закатом или после рассвета", true),
    FULL_MOON("Полнолуние / Зенит луны", "Ночная астрофотография и наблюдение", true),
    TIMER_COUNTDOWN("Обратный отсчёт", "Запуск по истечении заданного таймера", false)
}

/**
 * Дополнительное условие выполнения
 */
@Keep
enum class ScenaryConditionType(val title: String) {
    ALWAYS("Без ограничений (Всегда)"),
    CLEAR_SKY("Чистое небо (Облачность < 20%)"),
    WEEKENDS_ONLY("Только по выходным"),
    WORKDAYS_ONLY("Только по будням")
}

/**
 * Кубики действий, которые пользователь собирает в цепочку
 */
@Keep
enum class ScenaryActionType(
    val title: String,
    val description: String,
    val defaultParamHint: String
) {
    CUSTOM_NOTIFICATION(
        "Голосовое / Текстовое оповещение",
        "Вывести спец-сообщение (например: «40 мин до заката — лови свет!»)",
        "Текст напоминания"
    ),
    SHOW_BRIEFING(
        "Сводка дня и погоды",
        "Показать карточку с погодой, видимостью и фазой дня после сброса",
        "Заголовок сводки"
    ),
    SHOW_CHECKLIST(
        "Чек-лист подготовки",
        "Список вещей перед выходом (штатив, фильтр, аккумулятор и т.д.)",
        "Пункты через запятую"
    ),
    OPEN_APP(
        "Открыть приложение",
        "Автоматически запустить камеру, карту света или музыку",
        "Пакет или название (например: Камера)"
    ),
    CHAIN_NEXT_TIMER(
        "Запустить следующий таймер",
        "Автостарт нового этапа отсчёта (для готовки или тренировок)",
        "Длительность в минутах"
    )
}

/**
 * Один шаг (кубик) действия внутри сценария
 */
@Keep
data class ScenaryActionStep(
    val id: String = UUID.randomUUID().toString(),
    val type: ScenaryActionType = ScenaryActionType.CUSTOM_NOTIFICATION,
    val parameter: String = ""
)

/**
 * Полная модель пользовательского сценария
 */
@Keep
data class ScenaryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetType: ScenaryTargetType = ScenaryTargetType.ALARM,
    val isEnabled: Boolean = true,

    // Параметры триггера
    val triggerType: ScenaryTriggerType = ScenaryTriggerType.SUNSET,
    // Смещение в минутах (например, -40 означает "за 40 минут до события")
    val offsetMinutes: Int = -40,
    // Базовое время (для FIXED_TIME или расчётного времени события)
    val baseHour: Int = 6,
    val baseMinute: Int = 30,
    // Длительность в секундах (если targetType == TIMER)
    val timerDurationSeconds: Long = 300L,

    // Условие
    val condition: ScenaryConditionType = ScenaryConditionType.ALWAYS,

    // Собранная пользователем цепочка пост-действий
    val actions: List<ScenaryActionStep> = emptyList(),

    // ID связанного будильника или таймера для синхронизации
    val linkedEntityId: String? = null
) {
    /**
     * Форматирует читаемое описание триггера со смещением
     */
    fun getFormattedTriggerSummary(): String {
        return when (targetType) {
            ScenaryTargetType.TIMER -> {
                val mins = timerDurationSeconds / 60
                val secs = timerDurationSeconds % 60
                val timeText = if (secs > 0) "${mins}м ${secs}с" else "${mins} мин"
                "Таймер • $timeText"
            }
            ScenaryTargetType.ALARM -> {
                if (triggerType == ScenaryTriggerType.FIXED_TIME) {
                    String.format("Будильник • %02d:%02d", baseHour, baseMinute)
                } else {
                    val offsetText = when {
                        offsetMinutes < 0 -> "за ${-offsetMinutes} мин до"
                        offsetMinutes > 0 -> "через +${offsetMinutes} мин после"
                        else -> "ровно в момент"
                    }
                    "${triggerType.title} ($offsetText)"
                }
            }
        }
    }
}