package kaist.iclab.mobiletracker.services.upload.handlers.phone

import kaist.iclab.mobiletracker.db.dao.phone.WifiScanDao
import kaist.iclab.mobiletracker.db.mapper.WifiMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.WifiSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for WiFi Scan sensor data.
 */
class WifiScanUploadHandler(
    private val dao: WifiScanDao,
    private val service: WifiSensorService
) : SensorUploadHandler {
    override val sensorId = "WifiScan"

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

            val supabaseDataList = entities.map { WifiMapper.map(it, userUuid) }
            service.insertWifiSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
