package com.rcapture.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    @Query("""
        SELECT captures.* FROM captures 
        JOIN captures_fts ON captures.id = captures_fts.rowid 
        WHERE captures_fts MATCH :query
        ORDER BY timestamp DESC
    """)
    fun searchCaptures(query: String): Flow<List<CaptureEntity>>

    @Query("""
        SELECT * FROM captures 
        WHERE (:source IS NULL OR source = :source)
        AND (:startTime IS NULL OR timestamp >= :startTime)
        AND (:endTime IS NULL OR timestamp <= :endTime)
        ORDER BY timestamp DESC
    """)
    fun getCapturesFiltered(
        source: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getCaptureById(id: Long): CaptureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Delete
    suspend fun deleteCapture(capture: CaptureEntity)

    @Query("DELETE FROM captures")
    suspend fun clearAll()
}
