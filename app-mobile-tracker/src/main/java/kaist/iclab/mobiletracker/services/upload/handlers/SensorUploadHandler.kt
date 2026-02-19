package kaist.iclab.mobiletracker.services.upload.handlers

import kaist.iclab.mobiletracker.repository.Result

/**
 * Interface for handling sensor-specific upload operations.
 * Each sensor type has its own handler implementation that encapsulates
 * DAO, mapper, and service references for uploading data to Supabase.
 */
interface SensorUploadHandler {
    /** Unique identifier for the sensor (e.g., "Location", "Battery") */
    val sensorId: String

    /**
     * Check if there is data available to upload.
     * @param lastUploadTimestamp The timestamp of the last successful upload
     * @return true if there is new data to upload
     */
    suspend fun hasDataToUpload(lastUploadTimestamp: Long): Boolean

    /**
     * Upload sensor data to Supabase.
     * @param userUuid The UUID of the current user
     * @param lastUploadTimestamp The timestamp of the last successful upload
     * @return Result containing the max timestamp of uploaded data on success
     */
    suspend fun uploadData(userUuid: String, lastUploadTimestamp: Long): Result<Long>

    /**
     * Delete local data older than the specified timestamp.
     * @param beforeTimestamp The timestamp threshold for deletion
     */
    suspend fun pruneData(beforeTimestamp: Long)

    /**
     * Get the total record count available locally.
     */
    suspend fun getRecordCount(): Int

    /**
     * Get paginated records.
     * @param limit Maximum number of records
     * @param offset Number of records to skip
     * @return List of records (type generic to the handler)
     */
    suspend fun getRecordsPaginated(limit: Int, offset: Int): List<Any>

    /**
     * Get the CSV header for this sensor's data.
     */
    fun getCsvHeader(): String

    /**
     * Convert a record to a CSV row string.
     * @param record The record to convert
     */
    fun recordToCsvRow(record: Any): String
}
