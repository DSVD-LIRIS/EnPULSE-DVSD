package kaist.iclab.wearabletracker.data

import android.util.Log
import kaist.iclab.tracker.sync.ble.BLEDataChannel
import kaist.iclab.wearabletracker.Constants
import kaist.iclab.wearabletracker.db.dao.BaseDao
import kaist.iclab.wearabletracker.helpers.SyncPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kaist.iclab.wearabletracker.repository.ErrorClassifier
import kaist.iclab.wearabletracker.repository.Result
import kotlinx.serialization.json.JsonPrimitive

/**
 * Listens for sync acknowledgment (ACK) messages from the phone.
 * When an ACK is received, it confirms the sync was successful and triggers cleanup.
 */
class SyncAckListener(
    private val bleChannel: BLEDataChannel,
    private val daos: Map<String, BaseDao<*>>,
    private val syncPreferencesHelper: SyncPreferencesHelper,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = javaClass.simpleName

    /**
     * Start listening for ACK messages from the phone.
     */
    fun startListening() {
        bleChannel.addOnReceivedListener(setOf(Constants.BLE.KEY_SYNC_ACK)) { _, jsonElement ->
            val ackData = when (jsonElement) {
                is JsonPrimitive -> jsonElement.content
                else -> jsonElement.toString().trim('"')
            }
            handleAck(ackData)
        }
    }

    /**
     * Handle incoming ACK message.
     * Format: "batchId:OK" or "batchId:FAIL"
     */
    private fun handleAck(ackData: String) {
        coroutineScope.launch {
            ErrorClassifier.runClassified(TAG, "handle sync ACK") {
                val parts = ackData.split(":")
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid ACK format: $ackData")
                }

                val receivedBatchId = parts[0]
                val status = parts[1]

                val pendingBatch = syncPreferencesHelper.getPendingBatch()
                if (pendingBatch == null) {
                    Log.w(TAG, "Received ACK but no pending batch: $receivedBatchId")
                    return@runClassified
                }

                if (pendingBatch.batchId != receivedBatchId) {
                    Log.w(
                        TAG,
                        "ACK batch ID mismatch. Expected: ${pendingBatch.batchId}, Received: $receivedBatchId"
                    )
                    return@runClassified
                }

                when (status) {
                    "OK" -> onSyncConfirmed(pendingBatch)
                    "FAIL" -> Log.e(TAG, "Received failure ACK for batch: $receivedBatchId")
                    else -> Log.e(TAG, "Received unknown status in ACK: $status")
                }
            }
        }
    }

    /**
     * Called when sync is confirmed successful by the phone.
     * Deletes synced data and updates sync timestamp.
     */
    private suspend fun onSyncConfirmed(batch: SyncBatch) {
        // Delete synced data from all DAOs
        daos.values.forEach { dao ->
            dao.deleteDataBefore(batch.endTimestamp)
        }

        // Update last sync timestamp
        syncPreferencesHelper.saveLastSyncTimestamp(batch.endTimestamp)

        // Clear pending batch
        syncPreferencesHelper.clearPendingBatch()
    }

    /**
     * Cleanup method - call when listener is no longer needed.
     */
    fun cleanup() {
        // Handled by injected scope lifecycle
    }
}
