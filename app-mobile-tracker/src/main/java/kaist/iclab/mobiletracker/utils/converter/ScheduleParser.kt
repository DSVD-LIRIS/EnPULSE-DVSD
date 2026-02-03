package kaist.iclab.mobiletracker.utils.converter

import kaist.iclab.tracker.sensor.survey.SurveyScheduleMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.TimeUnit

/**
 * Parses schedule JSON strings into SurveyScheduleMethod objects.
 * Handles MANUAL, TIME_OF_DAY, and ESM schedule types.
 */
object ScheduleParser {

    private object ScheduleType {
        const val MANUAL = "MANUAL"
        const val TIME_OF_DAY = "TIME_OF_DAY"
        const val ESM = "ESM"
    }

    /**
     * Parse schedule type and JSON into a SurveyScheduleMethod.
     * 
     * @param scheduleType The type of schedule (MANUAL, TIME_OF_DAY, ESM)
     * @param scheduleJson Optional JSON string containing schedule parameters
     * @return The parsed SurveyScheduleMethod
     */
    fun parse(scheduleType: String, scheduleJson: String?): SurveyScheduleMethod {
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
            ScheduleType.ESM -> parseESMSchedule(schedule)
            else -> SurveyScheduleMethod.Manual()
        }
    }

    /**
     * Parse TIME_OF_DAY schedule parameters.
     * Expected format: {"timeOfDay": "HH:mm"} or {"timeOfDay": <milliseconds>}
     */
    private fun parseTimeOfDay(schedule: JsonObject?): List<Long> {
        if (schedule == null) return listOf(TimeUnit.HOURS.toMillis(12))

        return try {
            val timeOfDay = schedule["timeOfDay"]
            if (timeOfDay != null) {
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
     * Parse ESM (Experience Sampling Method) schedule parameters.
     * Expected format: {
     *   "numSurvey": <int>,
     *   "minInterval": <long>,
     *   "maxInterval": <long>,
     *   "startOfDay": <long>,
     *   "endOfDay": <long>
     * }
     */
    private fun parseESMSchedule(schedule: JsonObject?): SurveyScheduleMethod {
        if (schedule == null) return SurveyScheduleMethod.Manual()

        return try {
            val numSurvey = schedule["numSurvey"]?.jsonPrimitive?.int ?: 3
            val minInterval = schedule["minInterval"]?.jsonPrimitive?.long 
                ?: TimeUnit.HOURS.toMillis(1)
            val maxInterval = schedule["maxInterval"]?.jsonPrimitive?.long 
                ?: TimeUnit.HOURS.toMillis(3)
            val startOfDay = schedule["startOfDay"]?.jsonPrimitive?.long 
                ?: TimeUnit.HOURS.toMillis(9)
            val endOfDay = schedule["endOfDay"]?.jsonPrimitive?.long 
                ?: TimeUnit.HOURS.toMillis(21)

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
}
