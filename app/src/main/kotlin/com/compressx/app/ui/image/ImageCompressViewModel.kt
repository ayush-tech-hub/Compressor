package com.compressx.app.ui.image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.data.repository.CompressionRepository
import com.compressx.app.model.CompressionResult
import com.compressx.app.util.FileUtils
import com.compressx.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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

    private val _savedPath = MutableLiveData<String?>()
    val savedPath: LiveData<String?> = _savedPath

    private val _savedUri = MutableLiveData<Uri?>()
    val savedUri: LiveData<Uri?> = _savedUri

    var tempFile: File? = null
        private set

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _compressionResult.value = null
        _savedPath.value = null
        _savedUri.value = null
        tempFile?.delete()
        tempFile = null
        viewModelScope.launch(Dispatchers.IO) {
            val size = FileUtils.getFileSize(getApplication(), uri)
            _originalSize.postValue(size)
            updateEstimate(size, _quality.value ?: 60)
        }
    }

    fun setQuality(qualityValue: Int) {
        _quality.value = qualityValue
        val original = _originalSize.value ?: 0L
        if (original > 0L) updateEstimate(original, qualityValue)
    }

    private fun updateEstimate(originalSize: Long, quality: Int) {
        _estimatedSize.postValue(ImageCompressor.estimateCompressedSize(originalSize, quality))
    }

    fun compress() {
        val inputUri = _selectedUri.value ?: return
        val quality = _quality.value ?: 60

        viewModelScope.launch {
            _isCompressing.value = true
            _savedPath.value = null
            _savedUri.value = null
            try {
                val (result, file) = ImageCompressor.compressToTempFile(
                    context = getApplication(),
                    inputUri = inputUri,
                    quality = quality
                )
                tempFile?.delete()
                tempFile = file
                _compressionResult.value = result
                if (!result.success) _errorMessage.value = result.errorMessage
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Compression failed"
            } finally {
                _isCompressing.value = false
            }
        }
    }

    fun saveToDownloads(originalFileName: String) {
        val file = tempFile ?: return
        val inputUri = _selectedUri.value ?: return
        val mimeType = FileUtils.getMimeType(getApplication(), inputUri) ?: "image/jpeg"
        val destName = FileUtils.buildCompressedFileName(originalFileName)

        viewModelScope.launch(Dispatchers.IO) {
            val saved = FileUtils.saveToCompressXDownloads(getApplication(), file, destName, mimeType)
            if (saved != null) {
                val (uri, path) = saved
                _savedUri.postValue(uri)
                _savedPath.postValue(path)
                val result = _compressionResult.value
                if (result != null && result.success) {
                    repository.insert(
                        CompressionHistory(
                            fileName = originalFileName,
                            fileType = "image",
                            originalSize = result.originalSize,
                            compressedSize = result.compressedSize,
                            compressionRatio = result.compressionRatio,
                            timestamp = System.currentTimeMillis(),
                            outputPath = path
                        )
                    )
                }
            } else {
                _errorMessage.postValue("Failed to save file")
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    fun clearResult() {
        _compressionResult.value = null
        _savedPath.value = null
        _savedUri.value = null
        tempFile?.delete()
        tempFile = null
    }

    override fun onCleared() {
        super.onCleared()
        tempFile?.delete()
        tempFile = null
    }
}
