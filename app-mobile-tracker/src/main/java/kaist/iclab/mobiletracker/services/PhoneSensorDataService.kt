package kaist.iclab.mobiletracker.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.survey.SurveyQuestionResponseInsert
import kaist.iclab.mobiletracker.helpers.LanguageHelper
import kaist.iclab.mobiletracker.repository.PhoneSensorRepository
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.repository.UserProfileRepository
import kaist.iclab.mobiletracker.services.SurveyService
import kaist.iclab.mobiletracker.utils.NotificationHelper
import kaist.iclab.tracker.sensor.controller.BackgroundController
import kaist.iclab.tracker.sensor.core.Sensor
import kaist.iclab.tracker.sensor.core.SensorEntity
import kaist.iclab.tracker.sensor.survey.SurveySensor
import kaist.iclab.mobiletracker.di.AppCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Foreground service for receiving and storing phone sensor data locally in Room database.
 *
 * This service runs in the foreground and listens to sensor data from the tracker library,
 * then stores it using PhoneSensorRepository. It handles local storage only.
 *
 * For remote storage (Supabase upload), see the watch sensor services in this package.
 */
class PhoneSensorDataService : Service(), KoinComponent {
    companion object {
        private const val TAG = "PhoneSensorDataService"

        /**
         * Helper function to start the service from a Context
         */
        fun start(context: Context) {
            val intent = Intent(context, PhoneSensorDataService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Helper function to stop the service from a Context
         */
        fun stop(context: Context) {
            val intent = Intent(context, PhoneSensorDataService::class.java)
            context.stopService(intent)
        }
    }

    private val sensors by inject<List<Sensor<*, *>>>(qualifier = named("phoneSensors"))
    private val phoneSensorRepository by inject<PhoneSensorRepository>()
    private val serviceNotification by inject<BackgroundController.ServiceNotification>()
    private val timestampService: SyncTimestampService by lazy { SyncTimestampService(this) }
    private val surveySensor by inject<SurveySensor>()
    private val surveyService by inject<SurveyService>()
    private val userProfileRepository by inject<UserProfileRepository>()

    // Injected coroutine scope for background operations
    private val appScope by inject<AppCoroutineScope>()

    private val listener: Map<String, (SensorEntity) -> Unit> = sensors.associate {
        it.id to
                { e: SensorEntity ->
                    if (phoneSensorRepository.hasStorageForSensor(it.id)) {
                        appScope.io.launch {
                            when (val result = phoneSensorRepository.insertSensorData(it.id, e)) {
                                is Result.Success -> {
                                    // Track when phone sensor data is collected
                                    timestampService.updateLastPhoneSensorData()
                                }

                                is Result.Error -> {
                                    Log.e(
                                        TAG,
                                        "[PHONE] - Failed to store data from ${it.name}: ${result.message}",
                                        result.exception
                                    )
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "[PHONE] - No storage found for sensor ${it.name} (${it.id})")
                    }
                }
    }

    // Listener for survey responses - submits to Supabase
    private val surveyResponseListener: (SensorEntity) -> Unit = listener@{ entity ->
        val surveyEntity = entity as? SurveySensor.Entity ?: return@listener
        appScope.io.launch {
            try {
                val uuid = userProfileRepository.getCurrentUuid()
                if (uuid == null) {
                    Log.w(TAG, "[SURVEY] - No user UUID available, skipping Supabase submission")
                    return@launch
                }

                val responses = buildSurveyResponses(surveyEntity, uuid)
                if (responses.isEmpty()) {
                    Log.w(TAG, "[SURVEY] - No responses to submit")
                    return@launch
                }

                when (val result = surveyService.submitSurveyResponses(responses)) {
                    is Result.Success -> {
                        Log.d(TAG, "[SURVEY] - Successfully submitted ${responses.size} responses to Supabase")
                    }
                    is Result.Error -> {
                        Log.e(TAG, "[SURVEY] - Failed to submit responses: ${result.message}", result.exception)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[SURVEY] - Error processing survey response", e)
            }
        }
    }

    private fun buildSurveyResponses(
        entity: SurveySensor.Entity,
        uuid: String
    ): List<SurveyQuestionResponseInsert> {
        val formatter = DateTimeFormatter.ISO_INSTANT
        
        fun formatTimestamp(millis: Long?): String? {
            return millis?.let {
                Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).format(formatter)
            }
        }

        val responseJson = entity.response
        
        // Response is a JsonArray: [{"id":25,"response":"..."},{"id":26,"response":55.0},...]
        if (responseJson !is kotlinx.serialization.json.JsonArray) {
            Log.w(TAG, "[SURVEY] - Response is not a JsonArray: ${responseJson::class.simpleName}")
            return emptyList()
        }

        return responseJson.mapNotNull { element ->
            val obj = element as? JsonObject
            if (obj == null) {
                Log.w(TAG, "[SURVEY] - Array element is not a JsonObject: $element")
                return@mapNotNull null
            }

            // Use jsonPrimitive.content to avoid quotes if it's a string, or just toString() for numbers
            val questionId = obj["id"]?.toString()?.replace("\"", "")?.toIntOrNull()
            if (questionId == null) {
                Log.w(TAG, "[SURVEY] - Invalid 'id' field in response element")
                return@mapNotNull null
            }

            SurveyQuestionResponseInsert(
                questionId = questionId,
                uuid = uuid,
                triggerTime = formatTimestamp(entity.triggerTime),
                actualTriggerTime = formatTimestamp(entity.actualTriggerTime),
                surveyStartTime = formatTimestamp(entity.surveyStartTime),
                responseSubmissionTime = formatTimestamp(entity.responseSubmissionTime),
                response = element
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = NotificationHelper.createMainActivityPendingIntent(this, 0)

        // Get localized strings for notification
        val localizedContext = LanguageHelper(this).applyLanguage(this)

        val postNotification = NotificationHelper.buildNotification(
            context = this,
            channelId = serviceNotification.channelId,
            title = localizedContext.getString(R.string.notification_title),
            text = localizedContext.getString(R.string.notification_description),
            smallIcon = serviceNotification.icon,
            ongoing = true,
            pendingIntent = pendingIntent
        ).build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        this.startForeground(
            serviceNotification.notificationId,
            postNotification,
            serviceType
        )

        // Remove listeners first to prevent duplicates if onStartCommand is called multiple times
        for (sensor in sensors) {
            sensor.removeListener(listener[sensor.id]!!)
        }
        surveySensor.removeListener(surveyResponseListener)

        // Then add listeners
        for (sensor in sensors) {
            sensor.addListener(listener[sensor.id]!!)
        }

        // Add survey response listener for Supabase submission
        surveySensor.addListener(surveyResponseListener)

        return START_STICKY
    }

    override fun onDestroy() {
        // Remove all sensor listeners
        for (sensor in sensors) {
            sensor.removeListener(listener[sensor.id]!!)
        }

        // Remove survey response listener
        surveySensor.removeListener(surveyResponseListener)

        super.onDestroy()
    }
}

