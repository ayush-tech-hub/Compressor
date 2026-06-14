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
import com.compressx.app.util.FileUtils
import com.compressx.app.util.PdfCompressor
import kotlinx.coroutines.launch

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

    private val _compressionResult = MutableLiveData<CompressionResult?>()
    val compressionResult: LiveData<CompressionResult?> = _compressionResult

    private val _isCompressing = MutableLiveData(false)
    val isCompressing: LiveData<Boolean> = _isCompressing

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun onPdfSelected(uri: Uri) {
        _selectedUri.value = uri
        _compressionResult.value = null
        viewModelScope.launch {
            val name = FileUtils.getFileName(getApplication(), uri)
            val size = FileUtils.getFileSize(getApplication(), uri)
            _fileName.postValue(name)
            _originalSize.postValue(size)
            updateEstimate(size, _compressionLevel.value ?: PdfCompressionLevel.BALANCED)
        }
    }

    fun setCompressionLevel(level: PdfCompressionLevel) {
        _compressionLevel.value = level
        val size = _originalSize.value ?: 0L
        if (size > 0L) updateEstimate(size, level)
    }

    private fun updateEstimate(originalSize: Long, level: PdfCompressionLevel) {
        val estimated = PdfCompressor.estimateCompressedSize(originalSize, level)
        _estimatedSize.postValue(estimated)
    }

    fun compress(outputUri: Uri) {
        val inputUri = _selectedUri.value ?: return
        val level = _compressionLevel.value ?: PdfCompressionLevel.BALANCED

        viewModelScope.launch {
            _isCompressing.value = true
            try {
                val result = PdfCompressor.compress(
                    context = getApplication(),
                    inputUri = inputUri,
                    compressionLevel = level,
                    outputUri = outputUri
                )
                _compressionResult.value = result

                if (result.success) {
                    repository.insert(
                        CompressionHistory(
                            fileName = _fileName.value ?: FileUtils.getFileName(getApplication(), inputUri),
                            fileType = "pdf",
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

    fun clearError() { _errorMessage.value = null }
    fun clearResult() { _compressionResult.value = null }
}
