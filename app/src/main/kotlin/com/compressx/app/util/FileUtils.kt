package com.compressx.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) size = cursor.getLong(idx)
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    fun getExtension(context: Context, uri: Uri): String {
        val mimeType = getMimeType(context, uri) ?: return ""
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: ""
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun createTempFile(context: Context, prefix: String, extension: String): File {
        val cacheDir = File(context.cacheDir, "compressed").also { it.mkdirs() }
        return File.createTempFile(prefix, ".$extension", cacheDir)
    }

    fun deleteTempFiles(context: Context) {
        val cacheDir = File(context.cacheDir, "compressed")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, extension: String): File? {
        return try {
            val tempFile = createTempFile(context, "input_", extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    fun buildCompressedFileName(originalName: String): String {
        val dotIdx = originalName.lastIndexOf('.')
        return if (dotIdx >= 0) {
            "${originalName.substring(0, dotIdx)}_compressed${originalName.substring(dotIdx)}"
        } else {
            "${originalName}_compressed"
        }
    }
}
