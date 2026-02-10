package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.db.dao.watch.WatchAccelerometerDao
import kaist.iclab.mobiletracker.db.mapper.AccelerometerMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.AccelerometerSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Watch Accelerometer sensor data.
 */
class WatchAccelerometerUploadHandler(
    private val dao: WatchAccelerometerDao,
    private val service: AccelerometerSensorService
) : SensorUploadHandler {
    override val sensorId = "WatchAccelerometer"

    override suspend fun hasDataToUpload(lastUploadTimestamp: Long): Boolean {
        val entities = dao.getDataAfterTimestamp(lastUploadTimestamp)
        return entities.isNotEmpty()
    }

    override suspend fun uploadData(userUuid: String, lastUploadTimestamp: Long): Result<Long> {
        return ErrorClassifier.runClassified(sensorId, "upload $sensorId") {
            val entities = dao.getDataAfterTimestamp(lastUploadTimestamp)
            if (entities.isEmpty()) {
                throw IllegalStateException("No new $sensorId data to upload")
            }

            val supabaseDataList = entities.map { AccelerometerMapper.map(it, userUuid) }
            service.insertAccelerometerSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
