package com.compressx.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    data class FileSaveResult(
        val uri: Uri?,
        val absolutePath: String?,
        val success: Boolean,
        val errorMessage: String? = null
    )

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

    fun saveToCompressX(context: Context, sourceFile: File, fileName: String, mimeType: String): FileSaveResult {
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CompressX")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val targetFile = File(downloadDir, fileName)
        
        try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            return FileSaveResult(Uri.fromFile(targetFile), targetFile.absolutePath, true)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CompressX")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            sourceFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        return FileSaveResult(uri, targetFile.absolutePath, true)
                    }
                } catch (ex: Exception) {
                    return FileSaveResult(null, null, false, ex.message)
                }
            }
            return FileSaveResult(null, null, false, e.message)
        }
    }

    fun openCompressXFolder(context: Context) {
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CompressX")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val authority = "com.android.externalstorage.documents"
                val documentId = "primary:Download/CompressX"
                val uri = android.provider.DocumentsContract.buildDocumentUri(authority, documentId)
                setDataAndType(uri, "vnd.android.document/directory")
            } else {
                val uri = Uri.fromFile(downloadDir)
                setDataAndType(uri, "resource/folder")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://media/external/file"), "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ex: Exception) {
                Toast.makeText(context, "No File Manager found to open the folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getShareableUri(context: Context, fileUri: Uri): Uri {
        return try {
            if (fileUri.scheme == "file") {
                val file = File(fileUri.path ?: return fileUri)
                FileProvider.getUriForFile(context, "com.compressx.app.fileprovider", file)
            } else {
                fileUri
            }
        } catch (e: Exception) {
            fileUri
        }
    }
}
