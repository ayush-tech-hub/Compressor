package com.compressx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_history")
data class CompressionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileType: String, // "image" or "pdf"
    val originalSize: Long,
    val compressedSize: Long,
    val compressionRatio: Float,
    val timestamp: Long,
    val outputPath: String
)
