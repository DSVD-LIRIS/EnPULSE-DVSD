package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.Constants
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
            // Upload in chunks to avoid HTTP request timeouts on large datasets
            supabaseDataList.chunked(Constants.Network.UPLOAD_BATCH_SIZE).forEach { chunk ->
                service.insertLocationSensorDataBatch(chunk)
                    .getOrElse { error -> throw error }
            }
            entities.maxOf { entity -> entity.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBeforeByDeviceType(beforeTimestamp, DeviceType.WATCH.value)
    }

    override suspend fun getRecordCount(): Int {
        return dao.getRecordCountByDeviceType(DeviceType.WATCH.value)
    }

    override suspend fun getRecordsPaginated(limit: Int, offset: Int): List<Any> {
        return dao.getRecordsPaginatedByDeviceType(
            0L,
            true,
            limit,
            offset,
            DeviceType.WATCH.value
        )
    }

    override fun getCsvHeader(): String {
        return "eventId,uuid,deviceType,received,timestamp,latitude,longitude,altitude,speed,accuracy"
    }

    override fun recordToCsvRow(record: Any): String {
        val entity = record as kaist.iclab.mobiletracker.db.entity.common.LocationEntity
        return "${entity.eventId},${entity.uuid},${entity.deviceType},${entity.received},${entity.timestamp},${entity.latitude},${entity.longitude},${entity.altitude},${entity.speed},${entity.accuracy}"
    }
}
