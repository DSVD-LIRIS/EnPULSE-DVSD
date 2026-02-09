package kaist.iclab.tracker.sensor.survey.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SurveyConfig(
    val id: String,
    val schedule: ScheduleConfig,
    val notification: NotificationConfig,
    val questions: List<QuestionConfig>
)

@Serializable
data class ScheduleConfig(
    val type: String, // "MANUAL", "TIME_OF_DAY", "ESM"
    val esm: EsmConfig? = null,
    val timeOfDay: List<String>? = null // "HH:mm" or milliseconds
)

@Serializable
data class EsmConfig(
    val numSurvey: Int = 3,
    val minInterval: Long,
    val maxInterval: Long,
    val startOfDay: Long,
    val endOfDay: Long
)

@Serializable
data class NotificationConfig(
    val title: String,
    val description: String
)

@Serializable
data class QuestionConfig(
    val id: Int,
    val type: String,
    val text: String,
    val isMandatory: Boolean,
    val options: List<OptionConfig>? = null,
    val children: List<ChildQuestionConfig>? = null
)

@Serializable
data class OptionConfig(
    val display: String,
    val allowFreeResponse: Boolean = false
)

@Serializable
data class ChildQuestionConfig(
    val trigger: TriggerConfig,
    val questions: List<QuestionConfig>
)

@Serializable
data class TriggerConfig(
    val op: String,
    val value: JsonElement? = null
)
