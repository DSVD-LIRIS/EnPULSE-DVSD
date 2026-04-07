package kaist.iclab.mobiletracker.repository

import kaist.iclab.mobiletracker.data.DeviceType
import kaist.iclab.mobiletracker.db.dao.common.BaseDao
import kaist.iclab.mobiletracker.db.dao.common.LocationDao
import kaist.iclab.mobiletracker.di.AppCoroutineScope
import kaist.iclab.mobiletracker.helpers.SupabaseHelper
import kaist.iclab.mobiletracker.utils.SupabaseSessionHelper
import kaist.iclab.tracker.sensor.core.SensorEntity

/**
 * Implementation of PhoneSensorRepository using Room database DAOs.
 * Delegates to appropriate DAOs based on sensor ID.
 */
class PhoneSensorRepositoryImpl(
    private val sensorDataStorages: Map<String, BaseDao<*, *>>,
    private val supabaseHelper: SupabaseHelper,
    @Suppress("unused") private val appScope: AppCoroutineScope
) : PhoneSensorRepository {

    companion object {
        private const val TAG = "PhoneSensorRepository"
    }

    override suspend fun insertSensorData(sensorId: String, entity: SensorEntity): Result<Unit> {
        return ErrorClassifier.runClassified(TAG, "insert $sensorId") {
            @Suppress("UNCHECKED_CAST")
            val dao = sensorDataStorages[sensorId] as? BaseDao<SensorEntity, *>
                ?: throw IllegalStateException("No DAO found for sensor ID: $sensorId")
            val userUuid = SupabaseSessionHelper.getUuidOrNull(supabaseHelper.supabaseClient)
            dao.insert(entity, userUuid)
        }
    }

    override suspend fun insertSensorDataBatch(
        sensorId: String,
        entities: List<SensorEntity>
    ): Result<Unit> {
        return ErrorClassifier.runClassified(TAG, "insertBatch $sensorId") {
            @Suppress("UNCHECKED_CAST")
            val dao = sensorDataStorages[sensorId] as? BaseDao<SensorEntity, *>
                ?: throw IllegalStateException("No DAO found for sensor ID: $sensorId")
            val userUuid = SupabaseSessionHelper.getUuidOrNull(supabaseHelper.supabaseClient)
            dao.insertBatch(entities, userUuid)
        }
    }

    override suspend fun deleteAllSensorData(sensorId: String): Result<Unit> {
        return ErrorClassifier.runClassified(TAG, "deleteAll $sensorId") {
            @Suppress("UNCHECKED_CAST")
            val dao = sensorDataStorages[sensorId] as? BaseDao<*, *>
                ?: throw IllegalStateException("No DAO found for sensor ID: $sensorId")
            dao.deleteAll()
        }
    }

    override fun hasStorageForSensor(sensorId: String): Boolean {
        return sensorDataStorages.containsKey(sensorId)
    }

    override suspend fun flushAllData(): Result<Unit> {
        return ErrorClassifier.runClassified(TAG, "flush all phone data") {
            sensorDataStorages.values.forEach { dao ->
                @Suppress("UNCHECKED_CAST")
                (dao as? BaseDao<*, *>)?.deleteAll()
            }
        }
    }

    override suspend fun getLatestRecordedTimestamp(sensorId: String): Long? {
        return ErrorClassifier.runClassified(TAG, "getLatestTimestamp $sensorId") {
            if (sensorId == "Location") {
                val locationDao = sensorDataStorages[sensorId] as? LocationDao
                locationDao?.getLatestTimestampByDeviceType(DeviceType.PHONE.value)
            } else {
                @Suppress("UNCHECKED_CAST")
                val dao = sensorDataStorages[sensorId] as? BaseDao<*, *>
                dao?.getLatestTimestamp()
            }
        }.getOrNull()
    }

    override suspend fun getRecordCount(sensorId: String): Int {
        return ErrorClassifier.runClassified(TAG, "getRecordCount $sensorId") {
            if (sensorId == "Location") {
                val locationDao = sensorDataStorages[sensorId] as? LocationDao
                locationDao?.getRecordCountByDeviceType(DeviceType.PHONE.value) ?: 0
            } else {
                @Suppress("UNCHECKED_CAST")
                val dao = sensorDataStorages[sensorId] as? BaseDao<*, *>
                dao?.getRecordCount() ?: 0
            }
        }.getOrNull() ?: 0
    }
}
