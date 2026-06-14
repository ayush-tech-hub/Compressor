package com.compressx.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.compressx.app.model.CompressionResult
import com.compressx.app.model.PdfCompressionLevel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfCompressor {

    suspend fun compress(
        context: Context,
        inputUri: Uri,
        compressionLevel: PdfCompressionLevel,
        outputUri: Uri
    ): CompressionResult = withContext(Dispatchers.IO) {
        var inputTemp: File? = null
        var outputTemp: File? = null
        try {
            val originalSize = FileUtils.getFileSize(context, inputUri)

            // Copy input URI to a temp file (PDFBox needs file path)
            inputTemp = FileUtils.copyUriToTempFile(context, inputUri, "pdf")
                ?: return@withContext CompressionResult(
                    success = false,
                    originalSize = originalSize,
                    compressedSize = 0,
                    errorMessage = "Failed to read input PDF"
                )

            outputTemp = FileUtils.createTempFile(context, "compressed_pdf_", "pdf")

            val quality = compressionLevel.getImageQuality()
            val targetDpi = compressionLevel.getTargetDpi()

            PDDocument.load(inputTemp).use { document ->
                val resources = document.documentCatalog.pages
                for (page in resources) {
                    compressPageImages(page, document, quality, targetDpi)
                }
                document.save(outputTemp)
            }

            val compressedSize = outputTemp.length()

            // Copy result to output URI
            context.contentResolver.openOutputStream(outputUri)?.use { dest ->
                outputTemp.inputStream().use { src -> src.copyTo(dest) }
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
                errorMessage = e.message ?: "Unknown error during PDF compression"
            )
        } finally {
            inputTemp?.delete()
            outputTemp?.delete()
        }
    }

    private fun compressPageImages(
        page: PDPage,
        document: PDDocument,
        quality: Int,
        targetDpi: Int
    ) {
        try {
            val resources = page.resources ?: return
            val xObjectNames = resources.xObjectNames ?: return

            for (name in xObjectNames) {
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    try {
                        val image = xObject.image ?: continue
                        val currentDpi = xObject.metadata?.let { 72 } ?: 72

                        // Scale down if needed
                        val scaleFactor = if (currentDpi > targetDpi) {
                            targetDpi.toFloat() / currentDpi.toFloat()
                        } else 1f

                        val targetWidth = (image.width * scaleFactor).toInt().coerceAtLeast(1)
                        val targetHeight = (image.height * scaleFactor).toInt().coerceAtLeast(1)

                        val scaledBitmap = if (scaleFactor < 1f) {
                            Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true)
                        } else {
                            image
                        }

                        val newImage = JPEGFactory.createFromImage(document, scaledBitmap, quality / 100f)
                        resources.put(name, newImage)

                        if (scaledBitmap !== image) {
                            scaledBitmap.recycle()
                        }
                        image.recycle()
                    } catch (_: Exception) {
                        // Skip images that can't be compressed
                    }
                }
            }
        } catch (_: Exception) {
            // Skip pages that error
        }
    }

    fun estimateCompressedSize(originalSize: Long, level: PdfCompressionLevel): Long {
        return (originalSize * level.getEstimatedRatio()).toLong()
    }
}
