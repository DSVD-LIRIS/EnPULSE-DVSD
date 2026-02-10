package kaist.iclab.mobiletracker.services.upload.handlers.phone

import kaist.iclab.mobiletracker.db.dao.phone.BluetoothScanDao
import kaist.iclab.mobiletracker.db.mapper.BluetoothScanMapper
import kaist.iclab.mobiletracker.repository.ErrorClassifier
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.services.supabase.BluetoothScanSensorService
import kaist.iclab.mobiletracker.services.upload.handlers.SensorUploadHandler

/**
 * Upload handler for Bluetooth Scan sensor data.
 */
class BluetoothScanUploadHandler(
    private val dao: BluetoothScanDao,
    private val service: BluetoothScanSensorService
) : SensorUploadHandler {
    override val sensorId = "BluetoothScan"

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

            val supabaseDataList = entities.map { BluetoothScanMapper.map(it, userUuid) }
            service.insertBluetoothScanSensorDataBatch(supabaseDataList)
                .getOrElse { throw it }
            entities.maxOf { it.timestamp }
        }
    }

    override suspend fun pruneData(beforeTimestamp: Long) {
        dao.deleteDataBefore(beforeTimestamp)
    }
}
