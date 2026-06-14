package com.compressx.app.ui.batch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.compressx.app.model.CompressionStatus
import com.compressx.app.model.FileItem
import com.compressx.app.model.FileType
import com.compressx.app.util.FileUtils
import com.compressx.app.worker.ImageCompressionWorker
import com.compressx.app.worker.PdfCompressionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class BatchViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    private val _fileItems = MutableLiveData<MutableList<FileItem>>(mutableListOf())
    val fileItems: LiveData<MutableList<FileItem>> = _fileItems

    private val _overallProgress = MutableLiveData(0)
    val overallProgress: LiveData<Int> = _overallProgress

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _statusText = MutableLiveData<String>("")
    val statusText: LiveData<String> = _statusText

    private val _etaText = MutableLiveData<String>("")
    val etaText: LiveData<String> = _etaText

    private var startTime = 0L
    private val workObservers = mutableMapOf<androidx.lifecycle.LiveData<WorkInfo>, Observer<WorkInfo>>()

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _fileItems.value?.toMutableList() ?: mutableListOf()
            for (uri in uris) {
                val name = FileUtils.getFileName(getApplication(), uri)
                val size = FileUtils.getFileSize(getApplication(), uri)
                current.add(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        name = name,
                        size = size,
                        type = FileType.IMAGE
                    )
                )
            }
            _fileItems.postValue(current)
        }
    }

    fun addPdfs(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _fileItems.value?.toMutableList() ?: mutableListOf()
            for (uri in uris) {
                val name = FileUtils.getFileName(getApplication(), uri)
                val size = FileUtils.getFileSize(getApplication(), uri)
                current.add(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        name = name,
                        size = size,
                        type = FileType.PDF
                    )
                )
            }
            _fileItems.postValue(current)
        }
    }

    fun removeItem(item: FileItem) {
        val current = _fileItems.value?.toMutableList() ?: return
        current.removeAll { it.id == item.id }
        _fileItems.value = current
    }

    fun startCompression(quality: Int = 60) {
        val items = _fileItems.value ?: return
        if (items.isEmpty()) return

        _isRunning.value = true
        startTime = System.currentTimeMillis()

        // Reset all to pending
        items.forEach { it.status = CompressionStatus.PENDING; it.progress = 0 }
        _fileItems.value = items

        val context = getApplication<Application>()
        val cacheDir = java.io.File(context.cacheDir, "compressed").also { it.mkdirs() }

        val workRequests = items.map { item ->
            val outputFile = java.io.File(cacheDir, FileUtils.buildCompressedFileName(item.name))
            val outputUri = Uri.fromFile(outputFile)

            when (item.type) {
                FileType.IMAGE -> {
                    OneTimeWorkRequestBuilder<ImageCompressionWorker>()
                        .setInputData(
                            workDataOf(
                                ImageCompressionWorker.KEY_INPUT_URI to item.uri.toString(),
                                ImageCompressionWorker.KEY_OUTPUT_URI to outputUri.toString(),
                                ImageCompressionWorker.KEY_QUALITY to quality,
                                ImageCompressionWorker.KEY_FILE_ID to item.id
                            )
                        )
                        .addTag(item.id)
                        .build()
                }
                FileType.PDF -> {
                    OneTimeWorkRequestBuilder<PdfCompressionWorker>()
                        .setInputData(
                            workDataOf(
                                PdfCompressionWorker.KEY_INPUT_URI to item.uri.toString(),
                                PdfCompressionWorker.KEY_OUTPUT_URI to outputUri.toString(),
                                PdfCompressionWorker.KEY_FILE_ID to item.id
                            )
                        )
                        .addTag(item.id)
                        .build()
                }
            }
        }

        workManager.enqueue(workRequests)

        // Observe progress for each work request, tracking observers for cleanup
        workRequests.forEach { request ->
            val liveData = workManager.getWorkInfoByIdLiveData(request.id)
            val observer = Observer<WorkInfo> { workInfo ->
                workInfo?.let { updateItemFromWorkInfo(it) }
            }
            workObservers[liveData] = observer
            liveData.observeForever(observer)
        }
    }

    override fun onCleared() {
        super.onCleared()
        workObservers.forEach { (liveData, observer) ->
            liveData.removeObserver(observer)
        }
        workObservers.clear()
    }

    private fun updateItemFromWorkInfo(workInfo: WorkInfo) {
        val fileId = workInfo.progress.getString(ImageCompressionWorker.KEY_FILE_ID)
            ?: workInfo.outputData.getString(ImageCompressionWorker.KEY_FILE_ID)
            ?: return

        val current = _fileItems.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == fileId }
        if (idx < 0) return

        val statusName = workInfo.progress.getString(ImageCompressionWorker.KEY_STATUS)
        val progress = workInfo.progress.getInt(ImageCompressionWorker.KEY_PROGRESS, 0)

        current[idx] = current[idx].copy(
            status = when {
                workInfo.state == WorkInfo.State.SUCCEEDED -> CompressionStatus.DONE
                workInfo.state == WorkInfo.State.FAILED -> CompressionStatus.FAILED
                workInfo.state == WorkInfo.State.CANCELLED -> CompressionStatus.FAILED
                statusName == CompressionStatus.COMPRESSING.name -> CompressionStatus.COMPRESSING
                else -> CompressionStatus.PENDING
            },
            progress = when {
                workInfo.state == WorkInfo.State.SUCCEEDED -> 100
                else -> progress
            },
            compressedSize = workInfo.outputData.getLong(ImageCompressionWorker.KEY_COMPRESSED_SIZE, 0L)
                .takeIf { it > 0L } ?: workInfo.progress.getLong(ImageCompressionWorker.KEY_COMPRESSED_SIZE, 0L)
        )

        _fileItems.postValue(current)
        updateOverallProgress(current)
    }

    private fun updateOverallProgress(items: List<FileItem>) {
        val total = items.size
        if (total == 0) return

        val done = items.count { it.status == CompressionStatus.DONE || it.status == CompressionStatus.FAILED }
        val overallPct = (done * 100) / total
        _overallProgress.postValue(overallPct)

        val allDone = items.all { it.status == CompressionStatus.DONE || it.status == CompressionStatus.FAILED }
        if (allDone) {
            _isRunning.postValue(false)
            val successCount = items.count { it.status == CompressionStatus.DONE }
            _statusText.postValue("Done: $successCount/${total} succeeded")
            _etaText.postValue("")
        } else {
            val elapsed = System.currentTimeMillis() - startTime
            if (done > 0 && elapsed > 0) {
                val msPerItem = elapsed / done
                val remaining = total - done
                val etaMs = msPerItem * remaining
                val etaSecs = etaMs / 1000
                _etaText.postValue("ETA: ${etaSecs}s")
            }
            _statusText.postValue("Compressing $done/$total...")
        }
    }

    fun clearAll() {
        _fileItems.value = mutableListOf()
        _overallProgress.value = 0
        _statusText.value = ""
        _etaText.value = ""
    }
}
