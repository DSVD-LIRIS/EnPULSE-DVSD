package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.db.dao.watch.WatchHeartRateDao
import kaist.iclab.mobiletracker.db.mapper.HeartRateMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.HeartRateSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Watch Heart Rate sensor data.
 */
class WatchHeartRateUploadHandler(
    private val dao: WatchHeartRateDao,
    private val service: HeartRateSensorService
) : SensorUploadHandler {
    override val sensorId = "WatchHeartRate"

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

            val supabaseDataList = entities.map { HeartRateMapper.map(it, userUuid) }
            service.insertHeartRateSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
