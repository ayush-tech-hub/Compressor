package com.compressx.app.model

import android.net.Uri

data class FileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val type: FileType,
    var status: CompressionStatus = CompressionStatus.PENDING,
    var progress: Int = 0,
    var compressedSize: Long = 0L,
    var errorMessage: String = ""
)

enum class FileType {
    IMAGE, PDF
}

enum class CompressionStatus {
    PENDING, COMPRESSING, DONE, FAILED
}
