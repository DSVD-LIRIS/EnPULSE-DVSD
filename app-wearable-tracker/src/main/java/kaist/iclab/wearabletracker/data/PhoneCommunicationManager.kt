package kaist.iclab.wearabletracker.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kaist.iclab.tracker.sync.ble.BLEDataChannel
import kaist.iclab.wearabletracker.Constants
import kaist.iclab.wearabletracker.R
import kaist.iclab.wearabletracker.db.dao.BaseDao
import kaist.iclab.wearabletracker.helpers.NotificationHelper
import kaist.iclab.wearabletracker.helpers.SyncPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PhoneCommunicationManager(
    private val androidContext: Context,
    private val daos: Map<String, BaseDao<*>>,
    private val syncPreferencesHelper: SyncPreferencesHelper,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    private val TAG = javaClass.simpleName
    private val bleChannel: BLEDataChannel = BLEDataChannel(androidContext)
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(androidContext) }

    fun getBleChannel(): BLEDataChannel = bleChannel

    /**
     * Check if phone node is available and reachable
     */
    private suspend fun isPhoneAvailable(): Boolean = try {
        val connectedNodes = suspendCancellableCoroutine<List<Node>> { continuation ->
            nodeClient.connectedNodes
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        connectedNodes.isNotEmpty()
    } catch (e: Exception) {
        Log.e(TAG, "Error checking phone availability: ${e.message}", e)
        false
    }

    /**
     * Send new sensor data to the phone app via BLE (incremental sync).
     * Only sends data collected since the last successful sync.
     * Implements chunked sync to avoid OOM.
     */
    fun sendDataToPhone() {
        coroutineScope.launch {
            try {
                if (!isPhoneAvailable()) {
                    Log.e(
                        TAG,
                        "Error sending data to phone: Phone is not available or not connected"
                    )
                    withContext(Dispatchers.Main) {
                        NotificationHelper.showPhoneCommunicationFailure(
                            androidContext,
                            androidContext.getString(R.string.notification_phone_not_available)
                        )
                    }
                    return@launch
                }

                // Global start time for this sync session
                val lastSyncTime = syncPreferencesHelper.getLastSyncTimestamp() ?: 0L
                var dataSent = false
                var errorOccurred = false

                // Track max timestamp seen across all sensors to update global pref at end
                var maxTimestampSeen = lastSyncTime
                var totalRecordCount = 0
                val batchId = UUID.randomUUID().toString()

                // Per-sensor tracking
                var successSensorCount = 0
                var failedSensorId: String? = null

                // Iterate each sensor and send its data in chunks
                daos.forEach { (sensorId, dao) ->
                    // Guard to stop processing if error occurred
                    if (errorOccurred) return@forEach

                    var currentSensorLastTimestamp = lastSyncTime

                    while (coroutineContext.isActive) {
                        // Fetch a page of data
                        val data = dao.getDataSince(
                            currentSensorLastTimestamp,
                            kaist.iclab.wearabletracker.Constants.DB.SYNC_BATCH_LIMIT
                        )

                        if (data.isEmpty()) {
                            break // Sensor done
                        }

                        dataSent = true
                        totalRecordCount += data.size

                        // Calculate max timestamp in this chunk
                        val chunkMaxTimestamp = data.maxOf { it.timestamp }
                        maxTimestampSeen = maxOf(maxTimestampSeen, chunkMaxTimestamp)
                        currentSensorLastTimestamp = chunkMaxTimestamp

                        // Build CSV for this chunk
                        val csvBuilder = StringBuilder()
                        csvBuilder.append("BATCH:$batchId\n")
                        csvBuilder.append("SINCE:$lastSyncTime\n")
                        csvBuilder.append("---DATA---\n")
                        csvBuilder.append("$sensorId\n")
                        csvBuilder.append(data.first().toCsvHeader() + "\n")
                        data.forEach { entity ->
                            csvBuilder.append(entity.toCsvRow() + "\n")
                        }
                        csvBuilder.append("\n")

                        try {
                            bleChannel.send(Constants.BLE.KEY_SENSOR_DATA, csvBuilder.toString())
                            Log.d(TAG, "[$sensorId] Sent chunk: ${data.size} records, maxTs=$chunkMaxTimestamp")
                        } catch (e: Exception) {
                            Log.e(TAG, "[$sensorId] Error sending chunk: ${e.message}", e)
                            errorOccurred = true
                            failedSensorId = sensorId
                            break // Stop this sensor loop
                        }
                    }
                    if (!errorOccurred) successSensorCount++
                }

                if (dataSent && !errorOccurred) {
                    Log.i(TAG, "Sync payload sent: $successSensorCount sensors, batchId=$batchId")

                    // Save as pending batch - do NOT delete yet.
                    // SyncAckListener will perform cleanup after phone confirms receipt.
                    syncPreferencesHelper.savePendingBatch(
                        SyncBatch(
                            batchId = batchId,
                            startTimestamp = lastSyncTime,
                            endTimestamp = maxTimestampSeen,
                            recordCount = totalRecordCount,
                            createdAt = System.currentTimeMillis()
                        )
                    )

                    withContext(Dispatchers.Main) {
                        NotificationHelper.showPhoneCommunicationSuccess(androidContext)
                    }
                } else if (!dataSent) {
                    Log.w(TAG, "No new data to send")
                    withContext(Dispatchers.Main) {
                        NotificationHelper.showPhoneCommunicationFailure(
                            androidContext,
                            androidContext.getString(R.string.notification_no_data)
                        )
                    }
                } else {
                    Log.w(TAG, "Sync aborted at sensor '$failedSensorId' after $successSensorCount sensors")
                    withContext(Dispatchers.Main) {
                        NotificationHelper.showPhoneCommunicationFailure(
                            androidContext,
                            androidContext.getString(R.string.notification_send_failed)
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in sendDataToPhone: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    NotificationHelper.showPhoneCommunicationFailure(
                        androidContext,
                        e,
                        "Error in sendDataToPhone"
                    )
                }
            }
        }
    }
}

