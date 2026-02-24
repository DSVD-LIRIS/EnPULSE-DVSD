package kaist.iclab.wearabletracker.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kaist.iclab.tracker.sensor.controller.ControllerState
import kaist.iclab.wearabletracker.helpers.SyncPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var syncJob: Job? = null

    fun evalSync() {
        if (syncJob?.isActive == true) return // Still processing previous evaluation

        syncJob = coroutineScope.launch(Dispatchers.IO) {
            val enabled = syncPreferencesHelper.isAutoSyncEnabled()
            val interval = syncPreferencesHelper.getAutoSyncInterval()
            val lastSync = syncPreferencesHelper.getLastSyncTimestamp()

            if (!enabled || interval <= 0L) {
                return@launch
            }

            // Only sync if data collection is actively running
            val controllerState = controllerStateFlow.value
            if (controllerState.flag != ControllerState.FLAG.RUNNING) {
                return@launch
            }

            val now = System.currentTimeMillis()
            val lastSyncTime = lastSync ?: 0L
            val elapsedTime = now - lastSyncTime

            if (elapsedTime >= interval) {
                // We purposefully do not check isPhoneNearby() here.
                // Wear OS Doze mode disables BLE tracking, so we blindly hand the Urgent payload
                // to Play Services, which queues it and flushes immediately upon phone proximity.
                Log.d(TAG, "Interval reached, triggering silent urgent auto-sync to DataLayer")
                phoneCommunicationManager.sendDataToPhone(isSilent = true)
            }
        }
    }
}

