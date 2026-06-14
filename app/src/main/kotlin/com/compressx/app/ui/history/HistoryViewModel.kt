package com.compressx.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.data.repository.CompressionRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompressionRepository(application)

    val allHistory: LiveData<List<CompressionHistory>> = repository.allHistory

    fun delete(history: CompressionHistory) {
        viewModelScope.launch {
            repository.delete(history)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}
