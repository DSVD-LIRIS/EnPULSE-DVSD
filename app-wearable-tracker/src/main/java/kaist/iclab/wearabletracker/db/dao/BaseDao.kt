package kaist.iclab.wearabletracker.db.dao

import kaist.iclab.tracker.sensor.core.SensorEntity
import kaist.iclab.wearabletracker.db.entity.CsvSerializable

/**
 * Base interface for all wearable sensor DAOs.
 *
 * This is a plain Kotlin interface (NOT annotated with @Dao) — only the concrete
 * subinterfaces carry @Dao, so Room/KSP only processes the leaf DAOs. Each concrete
 * DAO converts from the library's [SensorEntity] subtype to a Room entity and delegates
 * to @Insert/@Query-annotated methods.
 *
 * @param T The library sensor entity type (e.g., HeartRateSensor.Entity)
 */
interface BaseDao<T : SensorEntity> {
    suspend fun insert(sensorEntity: T)
    suspend fun insert(sensorEntities: List<T>)

    suspend fun deleteAll()

    /**
     * Get all data for CSV export.
     */
    suspend fun getAllForExport(): List<CsvSerializable>

    /**
     * Get data since the given timestamp for incremental sync.
     * @param timestamp Only return records with timestamp > this value
     * @param limit Maximum number of records to return
     */
    suspend fun getDataSince(timestamp: Long, limit: Int): List<CsvSerializable>

    /**
     * Delete data before/up to the given timestamp after successful sync.
     * @param timestamp Delete records with timestamp <= this value
     */
    suspend fun deleteDataBefore(timestamp: Long)

    /**
     * Get the total number of records stored for this sensor.
     */
    suspend fun getCount(): Int

    /**
     * Get the number of records with timestamp > the given value.
     */
    suspend fun getCountSince(timestamp: Long): Int
}