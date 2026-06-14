package com.compressx.app.model

data class PdfCompressionSettings(
    val imageQuality: Int = 60,
    val scaleFactor: Float = 0.75f,
    val targetFileSizeBytes: Long? = null,
    val removeMetadata: Boolean = true,
    val useCustomMode: Boolean = false
)

enum class PdfTargetDpi(val label: String) {
    ORIGINAL("Original"),
    DPI_300("300 DPI"),
    DPI_150("150 DPI"),
    DPI_72("72 DPI");

    fun scale(): Float = when (this) {
        ORIGINAL -> 1.0f
        DPI_300 -> 1.0f
        DPI_150 -> 0.75f
        DPI_72 -> 0.5f
    }

    companion object {
        fun fromLabel(label: String) = values().find { it.label == label } ?: ORIGINAL
    }
}
