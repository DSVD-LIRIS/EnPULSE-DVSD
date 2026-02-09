package kaist.iclab.mobiletracker.data.survey

import kotlinx.serialization.Serializable

/**
 * Survey configuration response structure (flat with parentId references)
 */
@Serializable
data class SurveyConfig(
    val id: Int,
    val campaignId: Int,
    val title: String,
    val description: String?,
    val scheduleType: String,
    val schedule: String?,  // JSON string for Gson compatibility
    val questions: List<QuestionConfig>
)

/**
 * Question configuration within a survey
 */
@Serializable
data class QuestionConfig(
    val id: Int,
    val parentId: Int?,
    val type: String,
    val text: String,
    val shouldAnswer: Boolean,
    val trigger: String?,  // JSON string for Gson compatibility
    val options: List<OptionConfig>?
)

/**
 * Option configuration for RADIO/CHECKBOX questions
 */
@Serializable
data class OptionConfig(
    val id: Int,
    val display: String,
    val allowFreeResponse: Boolean
)

/**
 * Schedule types for surveys
 */
object ScheduleType {
    const val MANUAL = "MANUAL"
    const val TIME_OF_DAY = "TIME_OF_DAY"
    const val ESM = "ESM"
}

/**
 * Wrapper for List<SurveyConfig> for persistence
 */
@Serializable
data class SurveyConfigList(
    val configs: List<SurveyConfig> = emptyList()
)
