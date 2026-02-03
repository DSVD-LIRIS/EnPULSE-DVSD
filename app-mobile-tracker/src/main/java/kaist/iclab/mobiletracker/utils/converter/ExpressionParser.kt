package kaist.iclab.mobiletracker.utils.converter

import kaist.iclab.tracker.sensor.survey.question.ComparablePredicate
import kaist.iclab.tracker.sensor.survey.question.Expression
import kaist.iclab.tracker.sensor.survey.question.Predicate
import kaist.iclab.tracker.sensor.survey.question.SetPredicate
import kaist.iclab.tracker.sensor.survey.question.StringPredicate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses trigger JSON strings into Expression objects for different question types.
 * 
 * Trigger JSON format: {"op": "<operator>", "value": <value>}
 * 
 * Supported operators by type:
 * - TEXT: Equal, NotEqual, Empty
 * - NUMBER: Equal, NotEqual, GreaterThan, GreaterThanOrEqual, LessThan, LessThanOrEqual
 * - RADIO: Equal, NotEqual, GreaterThan, GreaterThanOrEqual, LessThan, LessThanOrEqual
 * - CHECKBOX: Equal, Contains
 */
object ExpressionParser {

    /**
     * Result of parsing a trigger expression.
     */
    sealed class ParseResult<out T> {
        /** Successfully parsed expression */
        data class Success<T>(val expression: Expression<T>) : ParseResult<T>()
        
        /** No trigger defined (null or empty) */
        data object NoTrigger : ParseResult<Nothing>()
        
        /** Failed to parse trigger */
        data class Error(val message: String) : ParseResult<Nothing>()
    }

    /**
     * Parse a trigger JSON string for a parent question of the given type.
     * 
     * @param triggerJson The trigger JSON string (may be null)
     * @param parentType The type of the parent question (TEXT, NUMBER, RADIO, CHECKBOX)
     * @return ParseResult containing the expression or error
     */
    fun parse(triggerJson: String?, parentType: String): ParseResult<*> {
        if (triggerJson.isNullOrEmpty() || triggerJson == "null") {
            return ParseResult.NoTrigger
        }

        return try {
            val json = Json.decodeFromString<JsonObject>(triggerJson)
            val op = json["op"]?.jsonPrimitive?.content
                ?: return ParseResult.Error("Missing 'op' field in trigger")
            val value = json["value"]

            when (parentType.uppercase()) {
                "TEXT" -> parseTextExpression(op, value)
                "NUMBER" -> parseNumberExpression(op, value)
                "RADIO" -> parseRadioExpression(op, value)
                "CHECKBOX" -> parseCheckboxExpression(op, value)
                else -> ParseResult.Error("Unknown parent type: $parentType")
            }
        } catch (e: Exception) {
            ParseResult.Error("Failed to parse trigger: ${e.message}")
        }
    }

    // ============================================================
    // TEXT Expression Parsing
    // ============================================================

    private fun parseTextExpression(op: String, value: JsonElement?): ParseResult<String> {
        val expression: Expression<String>? = when (op) {
            "Equal" -> Predicate.Equal(value?.jsonPrimitive?.content ?: "")
            "NotEqual" -> Predicate.NotEqual(value?.jsonPrimitive?.content ?: "")
            "Empty" -> StringPredicate.Empty()
            else -> null
        }

        return if (expression != null) {
            ParseResult.Success(expression)
        } else {
            ParseResult.Error("Unsupported operator '$op' for TEXT type")
        }
    }

    // ============================================================
    // NUMBER Expression Parsing
    // ============================================================

    @Suppress("UNCHECKED_CAST")
    private fun parseNumberExpression(op: String, value: JsonElement?): ParseResult<Double?> {
        val target = value?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

        val expression: Expression<*>? = when (op) {
            "Equal" -> Predicate.Equal(target as Double?)
            "NotEqual" -> Predicate.NotEqual(target as Double?)
            "GreaterThan" -> ComparablePredicate.GreaterThan(target)
            "GreaterThanOrEqual" -> ComparablePredicate.GreaterThanOrEqual(target)
            "LessThan" -> ComparablePredicate.LessThan(target)
            "LessThanOrEqual" -> ComparablePredicate.LessThanOrEqual(target)
            else -> null
        }

        return if (expression != null) {
            ParseResult.Success(expression as Expression<Double?>)
        } else {
            ParseResult.Error("Unsupported operator '$op' for NUMBER type")
        }
    }

    // ============================================================
    // RADIO Expression Parsing
    // ============================================================

    @Suppress("UNCHECKED_CAST")
    private fun parseRadioExpression(op: String, value: JsonElement?): ParseResult<Int?> {
        val target = try {
            value?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
        } catch (e: Exception) {
            null
        }

        val expression: Expression<*>? = when (op) {
            "Equal" -> Predicate.Equal(target)
            "NotEqual" -> Predicate.NotEqual(target)
            "GreaterThan" -> target?.let { ComparablePredicate.GreaterThan(it) }
            "GreaterThanOrEqual" -> target?.let { ComparablePredicate.GreaterThanOrEqual(it) }
            "LessThan" -> target?.let { ComparablePredicate.LessThan(it) }
            "LessThanOrEqual" -> target?.let { ComparablePredicate.LessThanOrEqual(it) }
            else -> null
        }

        return if (expression != null) {
            ParseResult.Success(expression as Expression<Int?>)
        } else {
            ParseResult.Error("Unsupported operator '$op' for RADIO type")
        }
    }

    // ============================================================
    // CHECKBOX Expression Parsing
    // ============================================================

    private fun parseCheckboxExpression(op: String, value: JsonElement?): ParseResult<Set<Int>> {
        val expression: Expression<Set<Int>>? = when (op) {
            "Equal" -> {
                val array = try {
                    if (value is JsonArray) {
                        Json.decodeFromJsonElement<List<Int>>(value)
                    } else if (value != null) {
                        listOf(value.jsonPrimitive.content.toDoubleOrNull()?.toInt() ?: 0)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                Predicate.Equal(array.toSet())
            }
            "Contains" -> {
                val target = value?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
                if (target != null) {
                    SetPredicate.Contains<Int, Set<Int>>(target)
                } else {
                    null
                }
            }
            else -> null
        }

        return if (expression != null) {
            ParseResult.Success(expression)
        } else {
            ParseResult.Error("Unsupported operator '$op' for CHECKBOX type")
        }
    }
}
