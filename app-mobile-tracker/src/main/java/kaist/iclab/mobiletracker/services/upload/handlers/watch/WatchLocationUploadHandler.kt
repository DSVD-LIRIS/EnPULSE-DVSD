package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.data.DeviceType
import kaist.iclab.mobiletracker.db.dao.common.LocationDao
import kaist.iclab.mobiletracker.db.mapper.LocationMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.LocationSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Watch Location sensor data.
 */
class WatchLocationUploadHandler(
    private val dao: LocationDao,
    private val service: LocationSensorService
) : SensorUploadHandler {
    override val sensorId = "WatchLocation"

    override suspend fun hasDataToUpload(lastUploadTimestamp: Long): Boolean {
        val entities =
            dao.getDataAfterTimestampByDeviceType(lastUploadTimestamp, DeviceType.WATCH.value)
        return entities.isNotEmpty()
    }

    override suspend fun uploadData(userUuid: String, lastUploadTimestamp: Long): Result<Long> {
        return ErrorClassifier.runClassified(sensorId, "upload $sensorId") {
            val entities =
                dao.getDataAfterTimestampByDeviceType(lastUploadTimestamp, DeviceType.WATCH.value)
            if (entities.isEmpty()) {
                throw IllegalStateException("No new $sensorId data to upload")
            }

            val supabaseDataList = entities.map { entity -> LocationMapper.map(entity, userUuid) }
            service.insertLocationSensorDataBatch(supabaseDataList)
                .getOrElse { error -> throw error }
            entities.maxOf { entity -> entity.timestamp }
        }
    }
}
