package com.compressx.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.data.repository.CompressionRepository
import com.compressx.app.model.CompressionStatus
import com.compressx.app.util.FileUtils
import com.compressx.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageCompressionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputUriString = inputData.getString(KEY_INPUT_URI)
            ?: return@withContext Result.failure()
        val outputUriString = inputData.getString(KEY_OUTPUT_URI)
            ?: return@withContext Result.failure()
        val quality = inputData.getInt(KEY_QUALITY, 60)
        val fileId = inputData.getString(KEY_FILE_ID) ?: return@withContext Result.failure()

        val inputUri = Uri.parse(inputUriString)
        val outputUri = Uri.parse(outputUriString)

        try {
            setProgress(workDataOf(
                KEY_FILE_ID to fileId,
                KEY_STATUS to CompressionStatus.COMPRESSING.name,
                KEY_PROGRESS to 10
            ))

            val result = ImageCompressor.compress(
                context = applicationContext,
                inputUri = inputUri,
                quality = quality,
                outputUri = outputUri
            )

            if (result.success) {
                // Save to history
                val repository = CompressionRepository(applicationContext)
                val fileName = FileUtils.getFileName(applicationContext, inputUri)
                repository.insert(
                    CompressionHistory(
                        fileName = fileName,
                        fileType = "image",
                        originalSize = result.originalSize,
                        compressedSize = result.compressedSize,
                        compressionRatio = result.compressionRatio,
                        timestamp = System.currentTimeMillis(),
                        outputPath = result.outputPath
                    )
                )

                setProgress(workDataOf(
                    KEY_FILE_ID to fileId,
                    KEY_STATUS to CompressionStatus.DONE.name,
                    KEY_PROGRESS to 100,
                    KEY_COMPRESSED_SIZE to result.compressedSize
                ))

                Result.success(
                    workDataOf(
                        KEY_FILE_ID to fileId,
                        KEY_COMPRESSED_SIZE to result.compressedSize,
                        KEY_ORIGINAL_SIZE to result.originalSize
                    )
                )
            } else {
                setProgress(workDataOf(
                    KEY_FILE_ID to fileId,
                    KEY_STATUS to CompressionStatus.FAILED.name,
                    KEY_ERROR to result.errorMessage
                ))
                Result.failure(workDataOf(KEY_ERROR to result.errorMessage))
            }
        } catch (e: Exception) {
            setProgress(workDataOf(
                KEY_FILE_ID to fileId,
                KEY_STATUS to CompressionStatus.FAILED.name,
                KEY_ERROR to (e.message ?: "Unknown error")
            ))
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    companion object {
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_QUALITY = "quality"
        const val KEY_FILE_ID = "file_id"
        const val KEY_STATUS = "status"
        const val KEY_PROGRESS = "progress"
        const val KEY_COMPRESSED_SIZE = "compressed_size"
        const val KEY_ORIGINAL_SIZE = "original_size"
        const val KEY_ERROR = "error"
    }
}
