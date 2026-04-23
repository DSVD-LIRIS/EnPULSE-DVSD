package kaist.iclab.wearabletracker.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kaist.iclab.tracker.sensor.galaxywatch.PPGSensor
import kaist.iclab.wearabletracker.db.entity.CsvSerializable
import kaist.iclab.wearabletracker.db.entity.PPGEntity

@Dao
abstract class PPGDao : BaseDao<PPGSensor.Entity> {
    override suspend fun insert(sensorEntity: PPGSensor.Entity) {
        val entity = sensorEntity.dataPoint.map {
            PPGEntity(
                received = it.received,
                timestamp = it.timestamp,
                green = it.green,
                red = it.red,
                ir = it.ir,
                greenStatus = it.greenStatus,
                redStatus = it.redStatus,
                irStatus = it.irStatus,
            )
        }

        insertUsingRoomEntity(entity)
    }

    override suspend fun insert(sensorEntities: List<PPGSensor.Entity>) {
        val entities = sensorEntities.flatMap { sensorEntity ->
            sensorEntity.dataPoint.map {
                PPGEntity(
                    received = it.received,
                    timestamp = it.timestamp,
                    green = it.green,
                    red = it.red,
                    ir = it.ir,
                    greenStatus = it.greenStatus,
                    redStatus = it.redStatus,
                    irStatus = it.irStatus,
                )
            }
        }
        insertUsingRoomEntity(entities)
    }

    @Insert
    abstract suspend fun insertUsingRoomEntity(ppgEntity: List<PPGEntity>)

    @Query("SELECT * FROM PPGEntity ORDER BY timestamp ASC")
    abstract suspend fun getAllPPGData(): List<PPGEntity>

    override suspend fun getAllForExport(): List<CsvSerializable> = getAllPPGData()

    @Query("SELECT * FROM PPGEntity WHERE timestamp > :since ORDER BY timestamp ASC LIMIT :limit")
    abstract suspend fun getPPGDataSince(since: Long, limit: Int): List<PPGEntity>

    override suspend fun getDataSince(timestamp: Long, limit: Int): List<CsvSerializable> =
        getPPGDataSince(timestamp, limit)

    @Query("DELETE FROM PPGEntity WHERE timestamp <= :until")
    abstract suspend fun deletePPGDataBefore(until: Long)

    override suspend fun deleteDataBefore(timestamp: Long) = deletePPGDataBefore(timestamp)

    @Query("DELETE FROM PPGEntity")
    abstract suspend fun deleteAllPPGData()

    override suspend fun deleteAll() {
        deleteAllPPGData()
    }

    @Query("SELECT COUNT(*) FROM PPGEntity")
    abstract override suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM PPGEntity WHERE timestamp > :timestamp")
    abstract override suspend fun getCountSince(timestamp: Long): Int
}
