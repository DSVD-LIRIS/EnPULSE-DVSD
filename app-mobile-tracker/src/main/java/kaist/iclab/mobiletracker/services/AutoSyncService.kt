package kaist.iclab.mobiletracker.services

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kaist.iclab.mobiletracker.Constants
import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.helpers.LanguageHelper
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.repository.onFailure
import kaist.iclab.mobiletracker.repository.onSuccess
import kaist.iclab.mobiletracker.services.upload.PhoneSensorUploadService
import kaist.iclab.mobiletracker.services.upload.WatchSensorUploadService
import kaist.iclab.mobiletracker.utils.NotificationHelper
import kaist.iclab.mobiletracker.utils.SensorTypeHelper
import kaist.iclab.tracker.sensor.core.Sensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named

/**
 * Service that handles automatic synchronization of sensor data to Supabase
 * based on configured interval and network preferences.
 *
 * Uses LifecycleService for automatic coroutine lifecycle management.
 */
class AutoSyncService : LifecycleService(), KoinComponent {
    companion object {
        private const val TAG = "AutoSyncService"

        /**
         * Helper function to start the service from a Context
         */
        fun start(context: Context) {
            val intent = Intent(context, AutoSyncService::class.java)
            context.startService(intent)
        }

        /**
         * Helper function to stop the service from a Context
         */
        fun stop(context: Context) {
            val intent = Intent(context, AutoSyncService::class.java)
            context.stopService(intent)
        }
    }

    private val syncTimestampService: SyncTimestampService by lazy {
        SyncTimestampService(this)
    }
    private val phoneSensorUploadService: PhoneSensorUploadService by inject()
    private val watchSensorUploadService: WatchSensorUploadService by inject()
    private val sensors by inject<List<Sensor<*, *>>>(qualifier = named("sensors"))

    private var lastSyncTime: Long = 0

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAutoSync()
        return START_STICKY
    }

    /**
     * Starts the auto-sync coroutine that periodically checks and syncs data.
     * Uses lifecycleScope for automatic cancellation on service destruction.
     */
    private fun startAutoSync() {
        Log.d(TAG, "Starting auto sync service")
        // Create notification channel
        NotificationHelper.ensureNotificationChannel(
            this,
            Constants.Notification.CHANNEL_ID_AUTO_SYNC,
            Constants.Notification.CHANNEL_NAME_AUTO_SYNC
        )

        // lifecycleScope automatically cancels when service is destroyed
        lifecycleScope.launch(Dispatchers.IO) {
            lastSyncTime = System.currentTimeMillis()

            while (isActive) {
                try {
                    checkAndSyncIfNeeded()
                    delay(Constants.AutoSync.CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto-sync loop: ${e.message}", e)
                    delay(Constants.AutoSync.CHECK_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Checks if sync conditions are met and triggers sync if needed
     */
    private suspend fun checkAndSyncIfNeeded() {
        val currentTime = System.currentTimeMillis()

        // Check if data collection is running
        val dataCollectionStarted = syncTimestampService.getDataCollectionStarted()
        if (dataCollectionStarted == null) {
            return
        }

        // Get auto-sync settings
        val intervalMs = syncTimestampService.getAutoSyncIntervalMs()
        if (intervalMs == Constants.AutoSync.INTERVAL_NONE) {
            return
        }

        // Check if enough time has passed since last sync
        val timeSinceLastSync = currentTime - lastSyncTime
        if (timeSinceLastSync < intervalMs) {
            return
        }

        // Check network conditions
        if (!isNetworkConditionMet()) {
            val networkMode = syncTimestampService.getAutoSyncNetworkMode()
            val networkModeName = when (networkMode) {
                Constants.AutoSync.NETWORK_WIFI_MOBILE -> "WiFi/Mobile"
                Constants.AutoSync.NETWORK_WIFI_ONLY -> "WiFi Only"
                Constants.AutoSync.NETWORK_MOBILE_ONLY -> "Mobile Only"
                else -> "Unknown"
            }
            Log.w(TAG, "Network condition not met (mode=$networkModeName), skipping sync")
            return
        }

        // All conditions met, trigger sync
        lifecycleScope.launch(Dispatchers.IO) {
            uploadAllSensorData()
        }
        lastSyncTime = currentTime
    }

    /**
     * Checks if the current network connection meets the configured network preference
     */
    private fun isNetworkConditionMet(): Boolean {
        val networkMode = syncTimestampService.getAutoSyncNetworkMode()

        // If mode is WIFI_MOBILE, any connection is fine
        if (networkMode == Constants.AutoSync.NETWORK_WIFI_MOBILE) {
            return isConnected()
        }

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        return when (networkMode) {
            Constants.AutoSync.NETWORK_WIFI_ONLY -> hasWifi
            Constants.AutoSync.NETWORK_MOBILE_ONLY -> hasCellular
            else -> isConnected()
        }
    }

    /**
     * Checks if device has any network connection
     */
    private fun isConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Uploads all sensor data for all phone sensors
     */
    private suspend fun uploadAllSensorData() {
        var successCount = 0
        var failureCount = 0
        var skippedCount = 0
        val failedSensors = mutableListOf<String>()

        try {
            sensors.forEach { sensor ->
                if (phoneSensorUploadService.hasDataToUpload(sensor.id)) {
                    phoneSensorUploadService.uploadSensorData(sensor.id)
                        .onSuccess { successCount++ }
                        .onFailure { e ->
                            failureCount++
                            failedSensors.add(sensor.name)
                            Log.e(TAG, "Upload failed for ${sensor.name}: ${e.message}", e)
                        }
                } else {
                    skippedCount++
                }
            }

            // Upload all watch sensors
            SensorTypeHelper.watchSensorIds.forEach { sensorId ->
                if (watchSensorUploadService.hasDataToUpload(sensorId)) {
                    watchSensorUploadService.uploadSensorData(sensorId)
                        .onSuccess { successCount++ }
                        .onFailure { e ->
                            failureCount++
                            failedSensors.add(sensorId)
                            Log.e(TAG, "Upload failed for $sensorId: ${e.message}", e)
                        }
                } else {
                    skippedCount++
                }
            }

            // Show notification based on results
            if (successCount > 0) {
                showSuccessNotification(successCount)
            } else if (failureCount > 0) {
                showFailureNotification(failureCount, failedSensors)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in auto-sync upload: ${e.message}", e)
            showFailureNotification(0, listOf("Fatal error: ${e.message}"))
        }
    }

    /**
     * Shows a success notification when auto-sync completes successfully
     */
    private fun showSuccessNotification(successCount: Int) {
        val pendingIntent =
            NotificationHelper.createMainActivityPendingIntent(
                this,
                Constants.Notification.ID_AUTO_SYNC_SUCCESS
            )
        val localizedContext = LanguageHelper(this).applyLanguage(this)
        val notification = NotificationHelper.buildNotification(
            context = this,
            channelId = Constants.Notification.CHANNEL_ID_AUTO_SYNC,
            title = localizedContext.getString(R.string.auto_sync_success_title),
            text = localizedContext.getString(R.string.auto_sync_success_message, successCount),
            pendingIntent = pendingIntent
        ).build()

        NotificationHelper.showNotification(
            this,
            Constants.Notification.ID_AUTO_SYNC_SUCCESS,
            notification
        )
    }

    /**
     * Shows a failure notification when auto-sync encounters errors
     */
    private fun showFailureNotification(failureCount: Int, failedSensors: List<String>) {
        val failedSensorsText = if (failedSensors.isNotEmpty()) {
            failedSensors.take(3).joinToString(", ") + if (failedSensors.size > 3) "..." else ""
        } else {
            ""
        }

        val pendingIntent =
            NotificationHelper.createMainActivityPendingIntent(
                this,
                Constants.Notification.ID_AUTO_SYNC_FAILURE
            )
        val localizedContext = LanguageHelper(this).applyLanguage(this)
        val notification = NotificationHelper.buildNotification(
            context = this,
            channelId = Constants.Notification.CHANNEL_ID_AUTO_SYNC,
            title = localizedContext.getString(R.string.auto_sync_failure_title),
            text = localizedContext.getString(
                R.string.auto_sync_failure_message,
                failureCount,
                failedSensorsText
            ),
            pendingIntent = pendingIntent
        ).build()

        NotificationHelper.showNotification(
            this,
            Constants.Notification.ID_AUTO_SYNC_FAILURE,
            notification
        )
        Log.w(TAG, "Failure notification shown: $failureCount sensors failed")
    }

}
