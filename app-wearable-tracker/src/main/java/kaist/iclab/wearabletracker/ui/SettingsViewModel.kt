package kaist.iclab.wearabletracker.ui

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import kaist.iclab.tracker.listener.SamsungHealthSensorInitializer
import kaist.iclab.tracker.sensor.controller.BackgroundController
import kaist.iclab.tracker.sensor.controller.ControllerState
import kaist.iclab.wearabletracker.data.DeviceInfo
import kaist.iclab.wearabletracker.data.PhoneCommunicationManager
import kaist.iclab.wearabletracker.helpers.NotificationHelper
import kaist.iclab.wearabletracker.repository.Result
import kaist.iclab.wearabletracker.repository.WatchSensorRepository
import kaist.iclab.wearabletracker.storage.SensorDataReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val sensorController: BackgroundController,
    private val sensorDataReceiver: SensorDataReceiver,
    private val phoneCommunicationManager: PhoneCommunicationManager,
    private val repository: WatchSensorRepository,
    private val samsungHealthSensorInitializer: SamsungHealthSensorInitializer
) : ViewModel() {
    companion object {
        private val TAG = SettingsViewModel::class.simpleName
    }

    // StateFlow for last sync timestamp
    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    // Samsung Health connection state - Start button should be disabled when false
    val isSamsungHealthConnected: StateFlow<Boolean> =
        samsungHealthSensorInitializer.connectionStateFlow

    // SDK Policy Error state - true when dev mode is not enabled on Health Platform
    val sdkPolicyError: StateFlow<Boolean> = samsungHealthSensorInitializer.sdkPolicyErrorStateFlow

    /**
     * Clear the SDK Policy Error and stop logging (called when user dismisses the error screen).
     */
    fun clearSdkPolicyError() {
        sensorController.stop()
        samsungHealthSensorInitializer.clearSdkPolicyError()
    }

    init {
        viewModelScope.launch {
            repository.lastSyncTimestampFlow.collect {
                _lastSyncTimestamp.value = it
            }
        }

        viewModelScope.launch {
            sensorController.controllerStateFlow.collect {
                if (it.flag == ControllerState.FLAG.RUNNING) sensorDataReceiver.startBackgroundCollection()
                else sensorDataReceiver.stopBackgroundCollection()
            }
        }

        // Stop controller immediately when SDK Policy Error is detected
        // This ensures the Start button doesn't show "recording" state while error popup is visible
        viewModelScope.launch {
            sdkPolicyError.collect { hasError ->
                if (hasError) {
                    sensorController.stop()
                }
            }
        }
    }

    val sensorMap = sensorController.sensors.associateBy { it.name }
    val sensorState = sensorController.sensors.associate { it.name to it.sensorStateFlow }
    val controllerState = sensorController.controllerStateFlow

    fun update(sensorName: String, status: Boolean) {
        val sensor = sensorMap[sensorName] ?: run {
            Log.w(TAG, "Sensor not found: $sensorName")
            return
        }
        if (status) sensor.enable()
        else sensor.disable()
    }

    fun getDeviceInfo(context: Context, callback: (DeviceInfo) -> Unit) {
        Wearable.getNodeClient(context).localNode
            .addOnSuccessListener { localNode ->
                val deviceInfo = DeviceInfo(
                    name = localNode.displayName,
                    id = localNode.id
                )
                callback(deviceInfo)
            }
            .addOnFailureListener { exception ->
                Log.e(
                    TAG,
                    "Error getting device information from getDeviceInfo(): ${exception.message}",
                    exception
                )
                // Show notification for this error
                NotificationHelper.showException(
                    context,
                    exception,
                    "Failed to get device information"
                )
            }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun startLogging() {
        sensorController.start()
    }

    fun stopLogging() {
        sensorController.stop()
    }

    fun upload() {
        viewModelScope.launch {
            phoneCommunicationManager.sendDataToPhone()
            // The timestamp will update automatically via repository.lastSyncTimestampFlow
            // when SyncPreferencesHelper is updated by SyncAckListener
        }
    }

    /**
     * Load the last sync timestamp from repository.
     */
    fun refreshLastSyncTimestamp() {
        // Now reactive, no manual work needed unless we want to force a one-shot read
    }

    fun flush(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.deleteAllSensorData()
            }

            when (result) {
                is Result.Success -> {
                    NotificationHelper.showFlushSuccess(context)
                }

                is Result.Error -> {
                    // Logging is handled by runClassified inside the repository
                    NotificationHelper.showFlushFailure(
                        context,
                        result.exception,
                        "Failed to delete sensor data"
                    )
                }
            }
        }
    }
}