package com.compressx.app.ui.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.data.repository.CompressionRepository
import com.compressx.app.model.CompressionResult
import com.compressx.app.model.PdfCompressionLevel
import com.compressx.app.model.PdfCompressionSettings
import com.compressx.app.model.PdfTargetDpi
import com.compressx.app.util.FileUtils
import com.compressx.app.util.PdfCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PdfCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompressionRepository(application)

    private val _selectedUri = MutableLiveData<Uri?>()
    val selectedUri: LiveData<Uri?> = _selectedUri

    private val _fileName = MutableLiveData<String>("")
    val fileName: LiveData<String> = _fileName

    private val _originalSize = MutableLiveData<Long>(0L)
    val originalSize: LiveData<Long> = _originalSize

    private val _estimatedSize = MutableLiveData<Long>(0L)
    val estimatedSize: LiveData<Long> = _estimatedSize

    private val _compressionLevel = MutableLiveData(PdfCompressionLevel.BALANCED)
    val compressionLevel: LiveData<PdfCompressionLevel> = _compressionLevel

    private val _isCustomMode = MutableLiveData(false)
    val isCustomMode: LiveData<Boolean> = _isCustomMode

    private val _customSettings = MutableLiveData(PdfCompressionSettings())
    val customSettings: LiveData<PdfCompressionSettings> = _customSettings

    private val _compressionResult = MutableLiveData<CompressionResult?>()
    val compressionResult: LiveData<CompressionResult?> = _compressionResult

    private val _isCompressing = MutableLiveData(false)
    val isCompressing: LiveData<Boolean> = _isCompressing

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _savedPath = MutableLiveData<String?>()
    val savedPath: LiveData<String?> = _savedPath

    private val _savedUri = MutableLiveData<Uri?>()
    val savedUri: LiveData<Uri?> = _savedUri

    var tempFile: File? = null
        private set

    fun onPdfSelected(uri: Uri) {
        _selectedUri.value = uri
        _compressionResult.value = null
        _savedPath.value = null
        _savedUri.value = null
        tempFile?.delete()
        tempFile = null
        viewModelScope.launch {
            val name = FileUtils.getFileName(getApplication(), uri)
            val size = FileUtils.getFileSize(getApplication(), uri)
            _fileName.postValue(name)
            _originalSize.postValue(size)
            refreshEstimate(size)
        }
    }

    fun setCompressionLevel(level: PdfCompressionLevel) {
        _compressionLevel.value = level
        refreshEstimate(_originalSize.value ?: 0L)
    }

    fun setCustomMode(enabled: Boolean) {
        _isCustomMode.value = enabled
        refreshEstimate(_originalSize.value ?: 0L)
    }

    fun setImageQuality(quality: Int) {
        _customSettings.value = _customSettings.value!!.copy(imageQuality = quality.coerceIn(10, 100))
        refreshEstimate(_originalSize.value ?: 0L)
    }

    fun setTargetDpi(dpi: PdfTargetDpi) {
        _customSettings.value = _customSettings.value!!.copy(scaleFactor = dpi.scale())
        refreshEstimate(_originalSize.value ?: 0L)
    }

    fun setTargetFileSize(bytes: Long?) {
        _customSettings.value = _customSettings.value!!.copy(targetFileSizeBytes = bytes)
    }

    fun setRemoveMetadata(remove: Boolean) {
        _customSettings.value = _customSettings.value!!.copy(removeMetadata = remove)
    }

    private fun refreshEstimate(size: Long) {
        if (size <= 0L) return
        val estimated = if (_isCustomMode.value == true) {
            PdfCompressor.estimateCompressedSize(size, _customSettings.value ?: PdfCompressionSettings())
        } else {
            PdfCompressor.estimateCompressedSize(size, _compressionLevel.value ?: PdfCompressionLevel.BALANCED)
        }
        _estimatedSize.postValue(estimated)
    }

    fun compress() {
        val inputUri = _selectedUri.value ?: return

        viewModelScope.launch {
            _isCompressing.value = true
            _savedPath.value = null
            _savedUri.value = null
            try {
                val (result, file) = if (_isCustomMode.value == true) {
                    PdfCompressor.compressToTempFile(
                        context = getApplication(),
                        inputUri = inputUri,
                        settings = _customSettings.value ?: PdfCompressionSettings()
                    )
                } else {
                    PdfCompressor.compressToTempFile(
                        context = getApplication(),
                        inputUri = inputUri,
                        compressionLevel = _compressionLevel.value ?: PdfCompressionLevel.BALANCED
                    )
                }
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
        val destName = FileUtils.buildCompressedFileName(originalFileName)

        viewModelScope.launch(Dispatchers.IO) {
            val saved = FileUtils.saveToCompressXDownloads(getApplication(), file, destName, "application/pdf")
            if (saved != null) {
                val (uri, path) = saved
                _savedUri.postValue(uri)
                _savedPath.postValue(path)
                val result = _compressionResult.value
                if (result != null && result.success) {
                    repository.insert(
                        CompressionHistory(
                            fileName = originalFileName,
                            fileType = "pdf",
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
