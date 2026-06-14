package com.compressx.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.compressx.app.data.repository.CompressionRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompressionRepository(application)

    val totalCount: LiveData<Int> = repository.totalCount
    val totalSpaceSaved: LiveData<Long?> = repository.totalSpaceSaved

    private val _navigateToImage = MutableLiveData<Boolean>()
    val navigateToImage: LiveData<Boolean> = _navigateToImage

    private val _navigateToPdf = MutableLiveData<Boolean>()
    val navigateToPdf: LiveData<Boolean> = _navigateToPdf

    fun onCompressImagesClicked() {
        _navigateToImage.value = true
    }

    fun onCompressPdfsClicked() {
        _navigateToPdf.value = true
    }

    fun onNavigationDone() {
        _navigateToImage.value = false
        _navigateToPdf.value = false
    }
}
