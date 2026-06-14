package com.compressx.app.model

enum class CompressionType {
    IMAGE, PDF
}

enum class PdfCompressionLevel(val displayName: String) {
    LOW("Low"),
    BALANCED("Balanced"),
    MAXIMUM("Maximum");

    fun getImageQuality(): Int = when (this) {
        LOW -> 80
        BALANCED -> 60
        MAXIMUM -> 40
    }

    fun getPageScale(): Float = when (this) {
        LOW -> 1.0f
        BALANCED -> 0.75f
        MAXIMUM -> 0.55f
    }

    fun getEstimatedRatio(): Float = when (this) {
        LOW -> 0.75f
        BALANCED -> 0.55f
        MAXIMUM -> 0.35f
    }
}
