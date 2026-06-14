package com.compressx.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.compressx.app.model.CompressionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    suspend fun compress(
        context: Context,
        inputUri: Uri,
        quality: Int,
        outputUri: Uri
    ): CompressionResult = withContext(Dispatchers.IO) {
        var tempOutputFile: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)
            val extension = FileUtils.getExtension(context, inputUri).ifEmpty { "jpg" }

            // Decode bounds first to pick inSampleSize
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(inputUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            val sampleSize = calculateInSampleSize(boundsOptions, 4096, 4096)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = context.contentResolver.openInputStream(inputUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext CompressionResult(
                success = false,
                originalSize = originalSize,
                compressedSize = 0,
                errorMessage = "Failed to decode image"
            )

            val format = when (extension.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            tempOutputFile = FileUtils.createTempFile(context, "compressed_", extension)
            FileOutputStream(tempOutputFile).use { out ->
                bitmap.compress(format, quality.coerceIn(1, 100), out)
            }
            bitmap.recycle()

            val compressedSize = tempOutputFile.length()

            // Copy compressed temp file to the destination URI
            context.contentResolver.openOutputStream(outputUri)?.use { dest ->
                tempOutputFile.inputStream().use { src -> src.copyTo(dest) }
            }

            // Preserve EXIF for JPEG
            if (format == Bitmap.CompressFormat.JPEG) {
                ExifHelper.copyExif(context, inputUri, tempOutputFile.absolutePath)
            }

            CompressionResult(
                success = true,
                originalSize = originalSize,
                compressedSize = compressedSize,
                outputPath = outputUri.toString()
            )
        } catch (e: Exception) {
            CompressionResult(
                success = false,
                originalSize = FileUtils.getFileSize(context, inputUri),
                compressedSize = 0,
                errorMessage = e.message ?: "Unknown error"
            )
        } finally {
            tempOutputFile?.delete()
        }
    }

    fun estimateCompressedSize(originalSize: Long, quality: Int): Long {
        val ratio = when {
            quality >= 80 -> 0.75
            quality >= 60 -> 0.55
            quality >= 40 -> 0.35
            quality >= 20 -> 0.20
            else -> 0.10
        }
        return (originalSize * ratio).toLong()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
