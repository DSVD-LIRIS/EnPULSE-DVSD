package kaist.iclab.mobiletracker.data.survey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Entity for inserting survey question responses into Supabase
 * Maps to the 'survey_question_response' table
 */
@Serializable
data class SurveyQuestionResponseInsert(
    @SerialName("question_id") val questionId: Int,
    val uuid: String,
    @SerialName("trigger_time") val triggerTime: String? = null,
    @SerialName("actual_trigger_time") val actualTriggerTime: String? = null,
    @SerialName("survey_start_time") val surveyStartTime: String? = null,
    @SerialName("response_submission_time") val responseSubmissionTime: String? = null,
    val response: JsonElement
)
