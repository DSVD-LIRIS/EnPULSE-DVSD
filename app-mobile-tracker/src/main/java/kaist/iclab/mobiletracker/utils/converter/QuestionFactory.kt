package kaist.iclab.mobiletracker.utils.converter

import kaist.iclab.mobiletracker.data.survey.OptionConfig
import kaist.iclab.mobiletracker.data.survey.QuestionConfig
import kaist.iclab.tracker.sensor.survey.question.CheckboxQuestion
import kaist.iclab.tracker.sensor.survey.question.NumberQuestion
import kaist.iclab.tracker.sensor.survey.question.Option
import kaist.iclab.tracker.sensor.survey.question.Question
import kaist.iclab.tracker.sensor.survey.question.QuestionTrigger
import kaist.iclab.tracker.sensor.survey.question.RadioQuestion
import kaist.iclab.tracker.sensor.survey.question.TextQuestion
import kaist.iclab.tracker.sensor.survey.question.Expression

/**
 * Factory for creating Question objects from QuestionConfig.
 * Handles type-specific construction with proper generic types.
 */
object QuestionFactory {

    /**
     * Supported question types.
     */
    object Types {
        const val TEXT = "TEXT"
        const val NUMBER = "NUMBER"
        const val RADIO = "RADIO"
        const val CHECKBOX = "CHECKBOX"
    }

    /**
     * Create a Question object from config and optional triggers.
     * 
     * @param config The question configuration from Supabase
     * @param triggers Optional list of triggers for child questions
     * @return The created Question or null if type is unsupported
     */
    @Suppress("UNCHECKED_CAST")
    fun create(config: QuestionConfig, triggers: List<QuestionTrigger<*>>?): Question<*>? {
        return try {
            when (config.type.uppercase()) {
                Types.TEXT -> createTextQuestion(config, triggers)
                Types.NUMBER -> createNumberQuestion(config, triggers)
                Types.RADIO -> createRadioQuestion(config, triggers)
                Types.CHECKBOX -> createCheckboxQuestion(config, triggers)
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("QuestionFactory", "Error creating question ${config.id}: ${e.message}")
            null
        }
    }

    /**
     * Create a QuestionTrigger with the correct generic type for a parent question type.
     */
    @Suppress("UNCHECKED_CAST")
    fun createTrigger(
        parentType: String,
        expression: Expression<*>,
        children: List<Question<*>>
    ): QuestionTrigger<*>? {
        return when (parentType.uppercase()) {
            Types.TEXT -> QuestionTrigger(expression as Expression<String>, children)
            Types.NUMBER -> QuestionTrigger(expression as Expression<Double?>, children)
            Types.RADIO -> QuestionTrigger(expression as Expression<Int?>, children)
            Types.CHECKBOX -> QuestionTrigger(expression as Expression<Set<Int>>, children)
            else -> null
        }
    }

    // ============================================================
    // Private Question Creators
    // ============================================================

    @Suppress("UNCHECKED_CAST")
    private fun createTextQuestion(
        config: QuestionConfig,
        triggers: List<QuestionTrigger<*>>?
    ): TextQuestion {
        return TextQuestion(
            id = config.id,
            question = config.text,
            isMandatory = config.shouldAnswer,
            questionTrigger = triggers as? List<QuestionTrigger<String>>
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createNumberQuestion(
        config: QuestionConfig,
        triggers: List<QuestionTrigger<*>>?
    ): NumberQuestion {
        return NumberQuestion(
            id = config.id,
            question = config.text,
            isMandatory = config.shouldAnswer,
            questionTrigger = triggers as? List<QuestionTrigger<Double?>>
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createRadioQuestion(
        config: QuestionConfig,
        triggers: List<QuestionTrigger<*>>?
    ): RadioQuestion? {
        val options = convertOptions(config.options)
        if (options.isEmpty()) return null

        return RadioQuestion(
            id = config.id,
            question = config.text,
            isMandatory = config.shouldAnswer,
            option = options,
            questionTrigger = triggers as? List<QuestionTrigger<Int?>>
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createCheckboxQuestion(
        config: QuestionConfig,
        triggers: List<QuestionTrigger<*>>?
    ): CheckboxQuestion? {
        val options = convertOptions(config.options)
        if (options.isEmpty()) return null

        return CheckboxQuestion(
            id = config.id,
            question = config.text,
            isMandatory = config.shouldAnswer,
            option = options,
            questionTrigger = triggers as? List<QuestionTrigger<Set<Int>>>
        )
    }

    // ============================================================
    // Option Conversion
    // ============================================================

    private fun convertOptions(options: List<OptionConfig>?): List<Option> {
        return options?.map { option ->
            Option(
                displayText = option.display,
                allowFreeResponse = option.allowFreeResponse
            )
        } ?: emptyList()
    }
}
