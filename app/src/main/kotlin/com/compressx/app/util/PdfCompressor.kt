package com.compressx.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.compressx.app.model.CompressionResult
import com.compressx.app.model.PdfCompressionLevel
import com.compressx.app.model.PdfCompressionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfCompressor {

    suspend fun compress(
        context: Context,
        inputUri: Uri,
        compressionLevel: PdfCompressionLevel,
        outputUri: Uri
    ): CompressionResult = withContext(Dispatchers.IO) {
        var inputTemp: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)

            // PdfRenderer requires a seekable file descriptor, so copy to temp file first
            inputTemp = FileUtils.copyUriToTempFile(context, inputUri, "pdf")
                ?: return@withContext CompressionResult(
                    success = false,
                    originalSize = originalSize,
                    compressedSize = 0,
                    errorMessage = "Failed to read input PDF"
                )

            val scale = compressionLevel.getPageScale()
            val jpegQuality = compressionLevel.getImageQuality()

            val pfd = ParcelFileDescriptor.open(inputTemp, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val document = PdfDocument()

            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    val renderWidth = (page.width * scale).toInt().coerceAtLeast(1)
                    val renderHeight = (page.height * scale).toInt().coerceAtLeast(1)

                    // Render page → bitmap
                    val pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    pageBitmap.eraseColor(Color.WHITE)
                    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    // Re-compress via JPEG round-trip to reduce size
                    val compressed = compressBitmapToJpeg(pageBitmap, jpegQuality)
                    pageBitmap.recycle()

                    // Draw compressed bitmap into PDF page
                    val pageInfo = PdfDocument.PageInfo.Builder(renderWidth, renderHeight, i + 1).create()
                    val docPage = document.startPage(pageInfo)
                    docPage.canvas.drawBitmap(compressed, 0f, 0f, Paint())
                    document.finishPage(docPage)
                    compressed.recycle()
                }

                // Write to output URI
                context.contentResolver.openOutputStream(outputUri)?.use { out ->
                    document.writeTo(out)
                }
            } finally {
                renderer.close()
                pfd.close()
                document.close()
            }

            val compressedSize = FileUtils.getFileSize(context, outputUri)

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
                errorMessage = e.message ?: "Unknown error during PDF compression"
            )
        } finally {
            inputTemp?.delete()
        }
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap, quality: Int): Bitmap {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bytes = baos.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: bitmap
    }

    // Overload for custom PdfCompressionSettings — supports target file size iteration
    suspend fun compress(
        context: Context,
        inputUri: Uri,
        settings: PdfCompressionSettings,
        outputUri: Uri
    ): CompressionResult = withContext(Dispatchers.IO) {
        var inputTemp: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)
            inputTemp = FileUtils.copyUriToTempFile(context, inputUri, "pdf")
                ?: return@withContext CompressionResult(
                    success = false, originalSize = originalSize,
                    compressedSize = 0, errorMessage = "Failed to read input PDF"
                )

            var currentQuality = settings.imageQuality
            var result: CompressionResult

            // Iterative compression when target size is set
            do {
                result = renderAndCompress(context, inputTemp, settings.copy(imageQuality = currentQuality), outputUri, originalSize)
                if (settings.targetFileSizeBytes == null || !result.success) break
                if (result.compressedSize <= settings.targetFileSizeBytes) break
                currentQuality -= 10
            } while (currentQuality >= 10)

            result
        } catch (e: Exception) {
            CompressionResult(success = false, originalSize = FileUtils.getFileSize(context, inputUri),
                compressedSize = 0, errorMessage = e.message ?: "Unknown error")
        } finally {
            inputTemp?.delete()
        }
    }

    private suspend fun renderAndCompress(
        context: Context,
        inputTemp: File,
        settings: PdfCompressionSettings,
        outputUri: Uri,
        originalSize: Long
    ): CompressionResult = withContext(Dispatchers.IO) {
        val pfd = ParcelFileDescriptor.open(inputTemp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val document = PdfDocument()
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val renderWidth = (page.width * settings.scaleFactor).toInt().coerceAtLeast(1)
                val renderHeight = (page.height * settings.scaleFactor).toInt().coerceAtLeast(1)
                val pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                pageBitmap.eraseColor(Color.WHITE)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                val compressed = compressBitmapToJpeg(pageBitmap, settings.imageQuality)
                pageBitmap.recycle()
                val pageInfo = PdfDocument.PageInfo.Builder(renderWidth, renderHeight, i + 1).create()
                val docPage = document.startPage(pageInfo)
                docPage.canvas.drawBitmap(compressed, 0f, 0f, Paint())
                document.finishPage(docPage)
                compressed.recycle()
            }
            context.contentResolver.openOutputStream(outputUri)?.use { document.writeTo(it) }
        } finally {
            renderer.close()
            pfd.close()
            document.close()
        }
        val compressedSize = FileUtils.getFileSize(context, outputUri)
        CompressionResult(success = true, originalSize = originalSize,
            compressedSize = compressedSize, outputPath = outputUri.toString())
    }

    suspend fun compressToTempFile(
        context: Context,
        inputUri: Uri,
        compressionLevel: PdfCompressionLevel
    ): Pair<CompressionResult, File?> = withContext(Dispatchers.IO) {
        var inputTemp: File? = null
        var outputTemp: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)
            inputTemp = FileUtils.copyUriToTempFile(context, inputUri, "pdf")
                ?: return@withContext Pair(
                    CompressionResult(success = false, originalSize = originalSize, compressedSize = 0, errorMessage = "Failed to read input PDF"),
                    null
                )

            val scale = compressionLevel.getPageScale()
            val jpegQuality = compressionLevel.getImageQuality()

            outputTemp = FileUtils.createTempFile(context, "compressed_pdf_", "pdf")
            val pfd = ParcelFileDescriptor.open(inputTemp, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val document = PdfDocument()
            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    val compressed = compressBitmapToJpeg(bmp, jpegQuality)
                    bmp.recycle()
                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                    val docPage = document.startPage(pageInfo)
                    docPage.canvas.drawBitmap(compressed, 0f, 0f, Paint())
                    document.finishPage(docPage)
                    compressed.recycle()
                }
                FileOutputStream(outputTemp).use { document.writeTo(it) }
            } finally {
                renderer.close(); pfd.close(); document.close()
            }

            Pair(
                CompressionResult(success = true, originalSize = originalSize, compressedSize = outputTemp.length(), outputPath = outputTemp.absolutePath),
                outputTemp
            )
        } catch (e: Exception) {
            outputTemp?.delete()
            Pair(
                CompressionResult(success = false, originalSize = FileUtils.getFileSize(context, inputUri), compressedSize = 0, errorMessage = e.message ?: "Unknown error"),
                null
            )
        } finally {
            inputTemp?.delete()
        }
    }

    suspend fun compressToTempFile(
        context: Context,
        inputUri: Uri,
        settings: PdfCompressionSettings
    ): Pair<CompressionResult, File?> = withContext(Dispatchers.IO) {
        var inputTemp: File? = null
        var outputTemp: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)
            inputTemp = FileUtils.copyUriToTempFile(context, inputUri, "pdf")
                ?: return@withContext Pair(
                    CompressionResult(success = false, originalSize = originalSize, compressedSize = 0, errorMessage = "Failed to read input PDF"),
                    null
                )

            outputTemp = FileUtils.createTempFile(context, "compressed_pdf_", "pdf")
            var currentQuality = settings.imageQuality

            do {
                val s = settings.copy(imageQuality = currentQuality)
                val pfd = ParcelFileDescriptor.open(inputTemp, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val document = PdfDocument()
                try {
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val w = (page.width * s.scaleFactor).toInt().coerceAtLeast(1)
                        val h = (page.height * s.scaleFactor).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        val compressed = compressBitmapToJpeg(bmp, s.imageQuality)
                        bmp.recycle()
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                        val docPage = document.startPage(pageInfo)
                        docPage.canvas.drawBitmap(compressed, 0f, 0f, Paint())
                        document.finishPage(docPage)
                        compressed.recycle()
                    }
                    FileOutputStream(outputTemp).use { document.writeTo(it) }
                } finally {
                    renderer.close(); pfd.close(); document.close()
                }
                if (settings.targetFileSizeBytes == null) break
                if (outputTemp.length() <= settings.targetFileSizeBytes) break
                currentQuality -= 10
            } while (currentQuality >= 10)

            Pair(
                CompressionResult(success = true, originalSize = originalSize, compressedSize = outputTemp.length(), outputPath = outputTemp.absolutePath),
                outputTemp
            )
        } catch (e: Exception) {
            outputTemp?.delete()
            Pair(
                CompressionResult(success = false, originalSize = FileUtils.getFileSize(context, inputUri), compressedSize = 0, errorMessage = e.message ?: "Unknown error"),
                null
            )
        } finally {
            inputTemp?.delete()
        }
    }

    fun estimateCompressedSize(originalSize: Long, level: PdfCompressionLevel): Long {
        return (originalSize * level.getEstimatedRatio()).toLong()
    }

    fun estimateCompressedSize(originalSize: Long, settings: PdfCompressionSettings): Long {
        val qualityRatio = settings.imageQuality / 100.0
        val scaleRatio = settings.scaleFactor * settings.scaleFactor
        return (originalSize * qualityRatio * scaleRatio).toLong().coerceAtLeast(1024)
    }
}
