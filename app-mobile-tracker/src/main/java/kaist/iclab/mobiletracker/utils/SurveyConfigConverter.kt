package kaist.iclab.mobiletracker.utils

import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.survey.QuestionConfig
import kaist.iclab.mobiletracker.data.survey.SurveyConfig
import kaist.iclab.mobiletracker.utils.converter.ExpressionParser
import kaist.iclab.mobiletracker.utils.converter.QuestionFactory
import kaist.iclab.mobiletracker.utils.converter.ScheduleParser
import kaist.iclab.tracker.sensor.survey.Survey
import kaist.iclab.tracker.sensor.survey.SurveyNotificationConfig
import kaist.iclab.tracker.sensor.survey.SurveyScheduleMethod
import kaist.iclab.tracker.sensor.survey.SurveySensor
import kaist.iclab.tracker.sensor.survey.question.Question
import kaist.iclab.tracker.sensor.survey.question.QuestionTrigger

/**
 * Converts Supabase SurveyConfig to tracker-library Survey objects.
 * 
 * This is the main orchestrator that delegates to:
 * - [ScheduleParser] for schedule parsing
 * - [ExpressionParser] for trigger parsing
 * - [QuestionFactory] for question creation
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
                val surveyId = config.id.toString()
                val scheduleMethod = ScheduleParser.parse(config.scheduleType, config.schedule)
                val notificationConfig = SurveyNotificationConfig(
                    title = config.title,
                    description = config.description ?: "Please complete the survey",
                    icon = R.drawable.ic_launcher_foreground
                )
                val questions = assembleQuestionHierarchy(config.questions)

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
                android.util.Log.e(TAG, "Failed to convert survey ${config.id}: ${e.message}")
            }
        }

        return ConvertedSurveys(surveys, scheduleMethods, notificationConfigs)
    }

    /**
     * Assemble questions into a hierarchy based on parentId relationships.
     * Returns only root-level questions (parentId == null) with children attached via triggers.
     */
    private fun assembleQuestionHierarchy(questions: List<QuestionConfig>): List<Question<*>> {
        val childrenByParentId = questions.groupBy { it.parentId }

        fun assemble(config: QuestionConfig): Question<*>? {
            val childConfigs = childrenByParentId[config.id] ?: emptyList()

            // Group children by their trigger and create QuestionTriggers
            val triggers = childConfigs
                .groupBy { it.trigger }
                .mapNotNull { (triggerJson, subConfigs) ->
                    val parseResult = ExpressionParser.parse(triggerJson, config.type)

                    when (parseResult) {
                        is ExpressionParser.ParseResult.Success -> {
                            val subQuestions = subConfigs.mapNotNull { assemble(it) }
                            if (subQuestions.isEmpty()) return@mapNotNull null

                            QuestionFactory.createTrigger(
                                parentType = config.type,
                                expression = parseResult.expression,
                                children = subQuestions
                            )
                        }
                        is ExpressionParser.ParseResult.NoTrigger -> null
                        is ExpressionParser.ParseResult.Error -> {
                            android.util.Log.w(TAG, "Trigger parse error for Q${config.id}: ${parseResult.message}")
                            null
                        }
                    }
                }

            return QuestionFactory.create(config, triggers.ifEmpty { null })
        }

        // Build from root questions only
        return questions
            .filter { it.parentId == null }
            .mapNotNull { assemble(it) }
    }
}
