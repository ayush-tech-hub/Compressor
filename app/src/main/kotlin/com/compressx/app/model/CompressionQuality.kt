package com.compressx.app.model

enum class CompressionQuality(val value: Int, val displayName: String) {
    LOW(80, "Low"),
    MEDIUM(60, "Medium"),
    HIGH(40, "High"),
    CUSTOM(-1, "Custom");

    companion object {
        fun fromValue(value: Int): CompressionQuality {
            return when {
                value >= 75 -> LOW
                value >= 50 -> MEDIUM
                value >= 25 -> HIGH
                else -> CUSTOM
            }
        }
    }
}
