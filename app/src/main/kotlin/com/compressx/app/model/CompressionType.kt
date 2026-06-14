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

    fun getTargetDpi(): Int = when (this) {
        LOW -> 150
        BALANCED -> 100
        MAXIMUM -> 72
    }

    fun getEstimatedRatio(): Float = when (this) {
        LOW -> 0.75f
        BALANCED -> 0.55f
        MAXIMUM -> 0.35f
    }
}
