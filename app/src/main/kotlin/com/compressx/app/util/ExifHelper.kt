package com.compressx.app.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ExifHelper {

    private val TAGS_TO_COPY = arrayOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION
    )

    fun copyExif(context: Context, sourceUri: Uri, destPath: String) {
        try {
            val sourceStream = context.contentResolver.openInputStream(sourceUri) ?: return
            val sourceExif = ExifInterface(sourceStream)
            val destExif = ExifInterface(destPath)

            for (tag in TAGS_TO_COPY) {
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }

            destExif.saveAttributes()
            sourceStream.close()
        } catch (_: Exception) {
            // EXIF copy is best-effort; don't fail compression if it errors
        }
    }

    fun getOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }
}
