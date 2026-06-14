package com.compressx.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface CompressionHistoryDao {

    @Query("SELECT * FROM compression_history ORDER BY timestamp DESC")
    fun getAllHistory(): LiveData<List<CompressionHistory>>

    @Query("SELECT * FROM compression_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<CompressionHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: CompressionHistory): Long

    @Delete
    suspend fun delete(history: CompressionHistory)

    @Query("DELETE FROM compression_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM compression_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM compression_history")
    suspend fun getCount(): Int

    @Query("SELECT SUM(originalSize - compressedSize) FROM compression_history")
    suspend fun getTotalSpaceSaved(): Long?

    @Query("SELECT COUNT(*) FROM compression_history")
    fun getCountLive(): LiveData<Int>

    @Query("SELECT SUM(originalSize - compressedSize) FROM compression_history")
    fun getTotalSpaceSavedLive(): LiveData<Long?>
}
