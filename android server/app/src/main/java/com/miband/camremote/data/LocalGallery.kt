package com.miband.camremote.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalGallery {
    fun save(context: Context, name: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MiBandCamera")
            if (!dir.exists()) dir.mkdirs()
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank {
                "CAPTURE_${System.currentTimeMillis()}.JPG"
            }
            val file = File(dir, safeName)
            FileOutputStream(file).use { it.write(bytes) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mime = when {
                    safeName.endsWith(".jpg", true) || safeName.endsWith(".jpeg", true) -> "image/jpeg"
                    safeName.endsWith(".png", true) -> "image/png"
                    safeName.endsWith(".mp4", true) -> "video/mp4"
                    safeName.endsWith(".mov", true) -> "video/quicktime"
                    safeName.endsWith(".nef", true) -> "image/x-nikon-nef"
                    safeName.endsWith(".raw", true) -> "image/x-raw"
                    safeName.endsWith(".arw", true) -> "image/x-sony-arw"
                    safeName.endsWith(".cr2", true) -> "image/x-canon-cr2"
                    else -> "application/octet-stream"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MiBandCamera")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = if (mime.startsWith("video/")) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            file.absolutePath
        }.getOrNull()
    }

    fun timestampName(prefix: String = "MIBAND", extension: String = "JPG"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.$extension"
    }
}
