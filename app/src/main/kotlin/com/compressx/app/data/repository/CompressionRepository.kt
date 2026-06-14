package com.compressx.app.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.compressx.app.data.db.AppDatabase
import com.compressx.app.data.db.CompressionHistory

class CompressionRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.compressionHistoryDao()

    val allHistory: LiveData<List<CompressionHistory>> = dao.getAllHistory()
    val totalCount: LiveData<Int> = dao.getCountLive()
    val totalSpaceSaved: LiveData<Long?> = dao.getTotalSpaceSavedLive()

    suspend fun insert(history: CompressionHistory): Long {
        return dao.insert(history)
    }

    suspend fun delete(history: CompressionHistory) {
        dao.delete(history)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getCount(): Int {
        return dao.getCount()
    }

    suspend fun getTotalSpaceSaved(): Long {
        return dao.getTotalSpaceSaved() ?: 0L
    }

    suspend fun getRecentHistory(limit: Int = 5): List<CompressionHistory> {
        return dao.getRecentHistory(limit)
    }
}
