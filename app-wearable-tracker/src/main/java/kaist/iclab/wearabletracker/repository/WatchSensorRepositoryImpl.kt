package kaist.iclab.wearabletracker.repository

import kaist.iclab.wearabletracker.db.dao.BaseDao
import kaist.iclab.wearabletracker.helpers.SyncPreferencesHelper
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of WatchSensorRepository.
 * Handles data operations using DAOs and SyncPreferencesHelper.
 */
class WatchSensorRepositoryImpl(
    private val sensorDataStorages: Map<String, BaseDao<*>>,
    private val syncPreferencesHelper: SyncPreferencesHelper
) : WatchSensorRepository {
    private val TAG = WatchSensorRepositoryImpl::class.simpleName ?: "WatchSensorRepositoryImpl"

    override suspend fun deleteAllSensorData(): Result<Unit> =
        ErrorClassifier.runClassified(TAG, "delete all sensor data") {
            sensorDataStorages.values.forEach { it.deleteAll() }
        }

    override fun getLastSyncTimestamp(): Long? {
        return syncPreferencesHelper.getLastSyncTimestamp()
    }

    override val lastSyncTimestampFlow: Flow<Long?> = syncPreferencesHelper.lastSyncTimestampFlow

    override fun saveLastSyncTimestamp(timestamp: Long) {
        syncPreferencesHelper.saveLastSyncTimestamp(timestamp)
    }

    override suspend fun getTotalRecordCount(): Int {
        return sensorDataStorages.values.sumOf { it.getCount() }
    }

    override suspend fun getRecordCountSince(timestamp: Long): Int {
        return sensorDataStorages.values.sumOf { it.getCountSince(timestamp) }
    }
}
