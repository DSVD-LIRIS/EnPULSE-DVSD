package kaist.iclab.mobiletracker.repository

import kaist.iclab.mobiletracker.data.survey.SurveyConfigList
import kaist.iclab.mobiletracker.services.SurveyService
import kaist.iclab.mobiletracker.storage.CouchbaseSurveyConfigStorage
import kaist.iclab.mobiletracker.utils.SurveyConfigConverter
import kaist.iclab.tracker.sensor.survey.SurveySensor
import kaist.iclab.tracker.storage.core.StateStorage
import kaist.iclab.tracker.storage.core.SurveyScheduleStorage
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementation of SurveyRepository.
 * Manages survey configurations from Supabase and persists them to Couchbase.
 */
class SurveyRepositoryImpl(
    private val surveyService: SurveyService,
    private val persistentStorage: CouchbaseSurveyConfigStorage,
    private val inMemoryStorage: StateStorage<SurveySensor.Config>,
    private val scheduleStorage: SurveyScheduleStorage
) : SurveyRepository {
    companion object {
        private const val TAG = "SurveyRepo"
    }

    override val surveysFlow: StateFlow<SurveyConfigList>
        get() = persistentStorage.stateFlow

    override suspend fun fetchAndPersistSurveys(campaignId: Int): Result<Int> {
        return ErrorClassifier.runClassified(TAG, "fetch and persist surveys") {
            when (val result = surveyService.getCampaignSurveys(campaignId)) {
                is Result.Success -> {
                    val configs = result.data

                    if (configs.isNotEmpty()) {
                        // 1. Persist to Couchbase
                        persistentStorage.set(SurveyConfigList(configs))

                        // 2. Apply to in-memory storage for SurveySensor
                        val sensorConfig = SurveyConfigConverter.toSurveySensorConfig(configs)
                        inMemoryStorage.set(sensorConfig)
                    } else {
                        // Clear old surveys when new campaign has no surveys
                        clearSurveys()
                    }

                    configs.size
                }

                is Result.Error -> {
                    throw result.exception ?: Exception(result.message)
                }
            }
        }
    }

    override fun getSensorConfig(): SurveySensor.Config {
        val savedConfigs = persistentStorage.get().configs
        return if (savedConfigs.isNotEmpty()) {
            SurveyConfigConverter.toSurveySensorConfig(savedConfigs)
        } else {
            SurveySensor.Config(survey = emptyMap())
        }
    }

    override fun clearSurveys() {
        persistentStorage.set(SurveyConfigList())
        inMemoryStorage.set(SurveySensor.Config(survey = emptyMap()))
        // Clear pending survey schedules/notifications from old configuration
        scheduleStorage.resetSchedule()
    }
}

