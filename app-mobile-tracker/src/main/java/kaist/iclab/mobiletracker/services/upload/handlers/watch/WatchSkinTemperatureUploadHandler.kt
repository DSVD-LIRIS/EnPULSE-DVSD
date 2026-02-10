package kaist.iclab.mobiletracker.services.upload.handlers.watch

import kaist.iclab.mobiletracker.db.dao.watch.WatchSkinTemperatureDao
import kaist.iclab.mobiletracker.db.mapper.SkinTemperatureMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.SkinTemperatureSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Watch Skin Temperature sensor data.
 */
class WatchSkinTemperatureUploadHandler(
    private val dao: WatchSkinTemperatureDao,
    private val service: SkinTemperatureSensorService
) : SensorUploadHandler {
    override val sensorId = "WatchSkinTemperature"

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

            val supabaseDataList = entities.map { SkinTemperatureMapper.map(it, userUuid) }
            service.insertSkinTemperatureSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
