package com.compressx.app.ui.image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.data.repository.CompressionRepository
import com.compressx.app.model.CompressionQuality
import com.compressx.app.model.CompressionResult
import com.compressx.app.util.FileUtils
import com.compressx.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ImageCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompressionRepository(application)

    private val _selectedUri = MutableLiveData<Uri?>()
    val selectedUri: LiveData<Uri?> = _selectedUri

    private val _originalSize = MutableLiveData<Long>(0L)
    val originalSize: LiveData<Long> = _originalSize

    private val _estimatedSize = MutableLiveData<Long>(0L)
    val estimatedSize: LiveData<Long> = _estimatedSize

    private val _quality = MutableLiveData<Int>(60)
    val quality: LiveData<Int> = _quality

    private val _compressionResult = MutableLiveData<CompressionResult?>()
    val compressionResult: LiveData<CompressionResult?> = _compressionResult

    private val _isCompressing = MutableLiveData<Boolean>(false)
    val isCompressing: LiveData<Boolean> = _isCompressing

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var previewJob: Job? = null

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _compressionResult.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val size = FileUtils.getFileSize(getApplication(), uri)
            _originalSize.postValue(size)
            updateEstimate(size, _quality.value ?: 60)
        }
    }

    fun setQuality(qualityValue: Int) {
        _quality.value = qualityValue
        val original = _originalSize.value ?: 0L
        if (original > 0L) {
            updateEstimate(original, qualityValue)
        }
    }

    private fun updateEstimate(originalSize: Long, quality: Int) {
        val estimated = ImageCompressor.estimateCompressedSize(originalSize, quality)
        _estimatedSize.postValue(estimated)
    }

    fun compress(outputUri: Uri) {
        val inputUri = _selectedUri.value ?: return
        val quality = _quality.value ?: 60

        viewModelScope.launch {
            _isCompressing.value = true
            try {
                val result = ImageCompressor.compress(
                    context = getApplication(),
                    inputUri = inputUri,
                    quality = quality,
                    outputUri = outputUri
                )
                _compressionResult.value = result

                if (result.success) {
                    repository.insert(
                        CompressionHistory(
                            fileName = FileUtils.getFileName(getApplication(), inputUri),
                            fileType = "image",
                            originalSize = result.originalSize,
                            compressedSize = result.compressedSize,
                            compressionRatio = result.compressionRatio,
                            timestamp = System.currentTimeMillis(),
                            outputPath = result.outputPath
                        )
                    )
                } else {
                    _errorMessage.value = result.errorMessage
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Compression failed"
            } finally {
                _isCompressing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearResult() {
        _compressionResult.value = null
    }
}
