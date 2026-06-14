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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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

    fun estimateCompressedSize(originalSize: Long, level: PdfCompressionLevel): Long {
        return (originalSize * level.getEstimatedRatio()).toLong()
    }
}
