package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.db.dao.watch.WatchPPGDao
import kaist.iclab.mobiletracker.db.mapper.PPGMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.PPGSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Watch PPG sensor data.
 */
class WatchPPGUploadHandler(
    private val dao: WatchPPGDao,
    private val service: PPGSensorService
) : SensorUploadHandler {
    override val sensorId = "WatchPPG"

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

            val supabaseDataList = entities.map { PPGMapper.map(it, userUuid) }
            service.insertPPGSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
