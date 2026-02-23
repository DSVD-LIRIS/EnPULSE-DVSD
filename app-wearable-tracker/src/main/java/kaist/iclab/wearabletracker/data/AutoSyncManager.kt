package kaist.iclab.wearabletracker.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kaist.iclab.tracker.sensor.controller.ControllerState
import kaist.iclab.wearabletracker.helpers.SyncPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages automatic synchronization based on phone proximity and user-defined intervals.
 * Only syncs when data collection is actively running.
 */
class AutoSyncManager(
    private val context: Context,
    private val phoneCommunicationManager: PhoneCommunicationManager,
    private val syncPreferencesHelper: SyncPreferencesHelper,
    private val controllerStateFlow: StateFlow<ControllerState>,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoSyncManager"
        private const val CHECK_INTERVAL_MS = 60_000L // Check every 1 minute
    }

    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    fun start() {
        coroutineScope.launch(Dispatchers.IO) {
            // Monitor settings changes and last sync time
            combine(
                syncPreferencesHelper.autoSyncEnabledFlow,
                syncPreferencesHelper.autoSyncIntervalFlow,
                syncPreferencesHelper.lastSyncTimestampFlow
            ) { enabled, interval, lastSync ->
                Triple(enabled, interval, lastSync)
            }.collectLatest { (enabled, interval, lastSync) ->
                if (!enabled || interval <= 0L) {
                    Log.d(TAG, "Auto-sync disabled or interval=None")
                    return@collectLatest
                }

                while (isActive) {
                    // Only sync if data collection is actively running
                    val controllerState = controllerStateFlow.value
                    if (controllerState.flag != ControllerState.FLAG.RUNNING) {
                        delay(CHECK_INTERVAL_MS)
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val lastSyncTime = lastSync ?: 0L
                    val elapsedTime = now - lastSyncTime

                    if (elapsedTime >= interval) {
                        Log.d(
                            TAG,
                            "Interval reached ($elapsedTime >= $interval), checking proximity..."
                        )
                        if (isPhoneNearby()) {
                            Log.i(TAG, "Phone nearby, triggering auto-sync")
                            phoneCommunicationManager.sendDataToPhone()
                        } else {
                            Log.d(TAG, "Phone not nearby, skipping auto-sync")
                        }
                    } else {
                        Log.v(
                            TAG,
                            "Interval not yet reached. Remaining: ${interval - elapsedTime}ms"
                        )
                    }

                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Check if any phone node is currently reachable (nearby).
     */
    private suspend fun isPhoneNearby(): Boolean {
        return try {
            val connectedNodes = suspendCancellableCoroutine<List<Node>> { continuation ->
                nodeClient.connectedNodes
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            connectedNodes.any { it.isNearby }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking nearby nodes: ${e.message}")
            false
        }
    }
}

