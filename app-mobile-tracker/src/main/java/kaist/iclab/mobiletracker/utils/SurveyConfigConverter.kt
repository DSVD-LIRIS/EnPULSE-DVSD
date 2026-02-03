package kaist.iclab.mobiletracker.utils

import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.survey.QuestionConfig
import kaist.iclab.mobiletracker.data.survey.SurveyConfig
import kaist.iclab.tracker.sensor.survey.Survey
import kaist.iclab.tracker.sensor.survey.SurveyNotificationConfig
import kaist.iclab.tracker.sensor.survey.SurveyScheduleMethod
import kaist.iclab.tracker.sensor.survey.SurveySensor
import kaist.iclab.tracker.sensor.survey.config.SurveyBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.TimeUnit
import kaist.iclab.tracker.sensor.survey.config.SurveyConfig as LibSurveyConfig
import kaist.iclab.tracker.sensor.survey.config.ScheduleConfig as LibScheduleConfig
import kaist.iclab.tracker.sensor.survey.config.NotificationConfig as LibNotificationConfig
import kaist.iclab.tracker.sensor.survey.config.QuestionConfig as LibQuestionConfig
import kaist.iclab.tracker.sensor.survey.config.OptionConfig as LibOptionConfig
import kaist.iclab.tracker.sensor.survey.config.ChildQuestionConfig as LibChildQuestionConfig
import kaist.iclab.tracker.sensor.survey.config.TriggerConfig as LibTriggerConfig
import kaist.iclab.tracker.sensor.survey.config.EsmConfig as LibEsmConfig

/**
 * Converts Supabase SurveyConfig to tracker-library Survey objects using Library DTOs.
 */
object SurveyConfigConverter {

    private const val TAG = "SurveyConverter"

    /**
     * Result of converting survey configs.
     */
    data class ConvertedSurveys(
        val surveys: Map<String, Survey>,
        val scheduleMethod: Map<String, SurveyScheduleMethod>,
        val notificationConfig: Map<String, SurveyNotificationConfig>
    )

    /**
     * Convert a list of SurveyConfig to SurveySensor.Config.
     */
    fun toSurveySensorConfig(configs: List<SurveyConfig>): SurveySensor.Config {
        val converted = convertAll(configs)
        return SurveySensor.Config(survey = converted.surveys)
    }

    /**
     * Convert all SurveyConfigs to tracker-library objects.
     */
    fun convertAll(configs: List<SurveyConfig>): ConvertedSurveys {
        val surveys = mutableMapOf<String, Survey>()
        val scheduleMethods = mutableMapOf<String, SurveyScheduleMethod>()
        val notificationConfigs = mutableMapOf<String, SurveyNotificationConfig>()

        configs.forEach { config ->
            try {
                // 1. Map to Library DTO
                val libConfig = mapToLibConfig(config)
                
                // 2. Build Runtime Object
                val survey = SurveyBuilder.build(libConfig)
                
                val surveyId = config.id.toString()
                surveys[surveyId] = survey
                scheduleMethods[surveyId] = survey.scheduleMethod
                notificationConfigs[surveyId] = survey.notificationConfig
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to convert survey ${config.id}: ${e.message}")
            }
        }

        return ConvertedSurveys(surveys, scheduleMethods, notificationConfigs)
    }

    private fun mapToLibConfig(config: SurveyConfig): LibSurveyConfig {
        return LibSurveyConfig(
            id = config.id.toString(),
            schedule = mapSchedule(config.scheduleType, config.schedule),
            notification = LibNotificationConfig(
                title = config.title,
                description = config.description ?: "Please complete the survey"
            ),
            questions = mapQuestionsRecursively(config.questions)
        )
    }

    private fun mapSchedule(type: String, scheduleJson: String?): LibScheduleConfig {
        val json = scheduleJson?.let { 
            try { Json.decodeFromString<JsonObject>(it) } catch(e: Exception) { null } 
        }

        var esmConfig: LibEsmConfig? = null
        var timeOfDay: List<String>? = null

        if (type == "ESM" && json != null) {
            esmConfig = LibEsmConfig(
                numSurvey = json["numSurvey"]?.jsonPrimitive?.int ?: 3,
                minInterval = json["minInterval"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(1),
                maxInterval = json["maxInterval"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(3),
                startOfDay = json["startOfDay"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(9),
                endOfDay = json["endOfDay"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(21)
            )
        } else if (type == "TIME_OF_DAY" && json != null) {
            val time = json["timeOfDay"]?.jsonPrimitive?.contentOrNull
            if (time != null) {
                timeOfDay = listOf(time)
            }
        }

        return LibScheduleConfig(
            type = type,
            esm = esmConfig,
            timeOfDay = timeOfDay
        )
    }

    private fun mapQuestionsRecursively(questions: List<QuestionConfig>): List<LibQuestionConfig> {
        val rootQuestions = questions.filter { it.parentId == null }
        val childrenByParentId = questions.groupBy { it.parentId }

        fun recurse(config: QuestionConfig): LibQuestionConfig {
            val childConfigs = childrenByParentId[config.id] ?: emptyList()
            
            // Group by trigger and create ChildQuestionConfigs
            val children = childConfigs.groupBy { it.trigger }.mapNotNull { (triggerJson, group) ->
                if (triggerJson.isNullOrEmpty() || triggerJson == "null") return@mapNotNull null
                
                try {
                    val triggerObj = Json.decodeFromString<JsonObject>(triggerJson)
                    val op = triggerObj["op"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val value = triggerObj["value"]

                    LibChildQuestionConfig(
                        trigger = LibTriggerConfig(op = op, value = value),
                        questions = group.map { recurse(it) }
                    )
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to parse trigger: $triggerJson", e)
                    null
                }
            }

            return LibQuestionConfig(
                id = config.id,
                type = config.type,
                text = config.text,
                isMandatory = config.shouldAnswer,
                options = config.options?.map { 
                    LibOptionConfig(it.display, it.allowFreeResponse) 
                },
                children = children.ifEmpty { null }
            )
        }

        return rootQuestions.map { recurse(it) }
    }
}
