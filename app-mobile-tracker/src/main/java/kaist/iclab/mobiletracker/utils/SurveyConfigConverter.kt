package kaist.iclab.mobiletracker.utils

import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.survey.OptionConfig
import kaist.iclab.mobiletracker.data.survey.QuestionConfig
import kaist.iclab.mobiletracker.data.survey.ScheduleType
import kaist.iclab.mobiletracker.data.survey.SurveyConfig
import kaist.iclab.tracker.sensor.survey.Survey
import kaist.iclab.tracker.sensor.survey.SurveyNotificationConfig
import kaist.iclab.tracker.sensor.survey.SurveyScheduleMethod
import kaist.iclab.tracker.sensor.survey.SurveySensor
import kaist.iclab.tracker.sensor.survey.question.CheckboxQuestion
import kaist.iclab.tracker.sensor.survey.question.NumberQuestion
import kaist.iclab.tracker.sensor.survey.question.Option
import kaist.iclab.tracker.sensor.survey.question.Question
import kaist.iclab.tracker.sensor.survey.question.RadioQuestion
import kaist.iclab.tracker.sensor.survey.question.TextQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.TimeUnit

/**
 * Converts Supabase SurveyConfig to tracker-library Survey objects
 */
object SurveyConfigConverter {

    /**
     * Result of converting survey configs
     */
    data class ConvertedSurveys(
        val surveys: Map<String, Survey>,
        val scheduleMethod: Map<String, SurveyScheduleMethod>,
        val notificationConfig: Map<String, SurveyNotificationConfig>
    )

    /**
     * Convert a list of SurveyConfig to SurveySensor.Config
     */
    fun toSurveySensorConfig(configs: List<SurveyConfig>): SurveySensor.Config {
        val converted = convertAll(configs)
        return SurveySensor.Config(
            survey = converted.surveys
        )
    }

    /**
     * Convert all SurveyConfigs to tracker-library objects
     */
    fun convertAll(configs: List<SurveyConfig>): ConvertedSurveys {
        val surveys = mutableMapOf<String, Survey>()
        val scheduleMethods = mutableMapOf<String, SurveyScheduleMethod>()
        val notificationConfigs = mutableMapOf<String, SurveyNotificationConfig>()

        configs.forEach { config ->
            try {
                val surveyId = config.id.toString()
                val scheduleMethod = parseScheduleMethod(config.scheduleType, config.schedule)
                val notificationConfig = SurveyNotificationConfig(
                    title = config.title,
                    description = config.description ?: "Please complete the survey",
                    icon = R.drawable.ic_launcher_foreground
                )
                val questions = convertQuestions(config.questions)

                if (questions.isNotEmpty()) {
                    surveys[surveyId] = Survey(
                        scheduleMethod = scheduleMethod,
                        notificationConfig = notificationConfig,
                        *questions.toTypedArray()
                    )
                    scheduleMethods[surveyId] = scheduleMethod
                    notificationConfigs[surveyId] = notificationConfig
                }
            } catch (e: Exception) {
                // Skip surveys that fail to convert
            }
        }

        return ConvertedSurveys(surveys, scheduleMethods, notificationConfigs)
    }

    /**
     * Parse schedule method from Supabase schedule JSON string
     */
    private fun parseScheduleMethod(scheduleType: String, scheduleJson: String?): SurveyScheduleMethod {
        val schedule = scheduleJson?.let {
            try {
                Json.decodeFromString<JsonObject>(it)
            } catch (e: Exception) {
                null
            }
        }
        
        return when (scheduleType) {
            ScheduleType.TIME_OF_DAY -> {
                val times = parseTimeOfDay(schedule)
                SurveyScheduleMethod.Fixed(timeOfDay = times)
            }
            ScheduleType.ESM -> {
                parseESMSchedule(schedule)
            }
            else -> SurveyScheduleMethod.Manual()
        }
    }

    /**
     * Parse TIME_OF_DAY schedule
     */
    private fun parseTimeOfDay(schedule: JsonObject?): List<Long> {
        if (schedule == null) return listOf(TimeUnit.HOURS.toMillis(12))

        return try {
            val timeOfDay = schedule["timeOfDay"]
            if (timeOfDay != null) {
                // Assuming timeOfDay is in format "HH:mm" or milliseconds
                val timeStr = timeOfDay.jsonPrimitive.content
                if (timeStr.contains(":")) {
                    val parts = timeStr.split(":")
                    val hours = parts[0].toLongOrNull() ?: 12
                    val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0
                    listOf(TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes))
                } else {
                    listOf(timeStr.toLongOrNull() ?: TimeUnit.HOURS.toMillis(12))
                }
            } else {
                listOf(TimeUnit.HOURS.toMillis(12))
            }
        } catch (e: Exception) {
            listOf(TimeUnit.HOURS.toMillis(12))
        }
    }

    /**
     * Parse ESM schedule
     */
    private fun parseESMSchedule(schedule: JsonObject?): SurveyScheduleMethod {
        if (schedule == null) return SurveyScheduleMethod.Manual()

        return try {
            val numSurvey = schedule["numSurvey"]?.jsonPrimitive?.int ?: 3
            val minInterval = schedule["minInterval"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(1)
            val maxInterval = schedule["maxInterval"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(3)
            val startOfDay = schedule["startOfDay"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(9)
            val endOfDay = schedule["endOfDay"]?.jsonPrimitive?.long ?: TimeUnit.HOURS.toMillis(21)

            SurveyScheduleMethod.ESM(
                minInterval = minInterval,
                maxInterval = maxInterval,
                startOfDay = startOfDay,
                endOfDay = endOfDay,
                numSurvey = numSurvey
            )
        } catch (e: Exception) {
            SurveyScheduleMethod.Manual()
        }
    }

    /**
     * Convert question configs to Question objects
     * Only converts root-level questions (parentId == null)
     */
    private fun convertQuestions(questions: List<QuestionConfig>): List<Question<*>> {
        // First pass: convert all questions
        val rootQuestions = questions.filter { it.parentId == null }
        return rootQuestions.mapNotNull { convertQuestion(it) }
    }

    /**
     * Convert a single QuestionConfig to Question
     */
    private fun convertQuestion(config: QuestionConfig): Question<*>? {
        return try {
            when (config.type.uppercase()) {
                "TEXT" -> TextQuestion(
                    id = config.id,
                    question = config.text,
                    isMandatory = config.shouldAnswer
                )
                "NUMBER" -> NumberQuestion(
                    id = config.id,
                    question = config.text,
                    isMandatory = config.shouldAnswer
                )
                "RADIO" -> {
                    val options = convertOptions(config.options)
                    if (options.isEmpty()) return null
                    RadioQuestion(
                        id = config.id,
                        question = config.text,
                        isMandatory = config.shouldAnswer,
                        option = options
                    )
                }
                "CHECKBOX" -> {
                    val options = convertOptions(config.options)
                    if (options.isEmpty()) return null
                    CheckboxQuestion(
                        id = config.id,
                        question = config.text,
                        isMandatory = config.shouldAnswer,
                        option = options
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert OptionConfig list to Option list
     */
    private fun convertOptions(options: List<OptionConfig>?): List<Option> {
        return options?.map { option ->
            Option(
                displayText = option.display,
                allowFreeResponse = option.allowFreeResponse
            )
        } ?: emptyList()
    }
}
