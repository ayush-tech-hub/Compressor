package com.compressx.app.model

data class CompressionResult(
    val success: Boolean,
    val originalSize: Long,
    val compressedSize: Long,
    val outputPath: String = "",
    val errorMessage: String = ""
) {
    val compressionRatio: Float
        get() = if (originalSize > 0) {
            (1f - compressedSize.toFloat() / originalSize.toFloat()) * 100f
        } else 0f

    val spaceSaved: Long
        get() = originalSize - compressedSize
}
