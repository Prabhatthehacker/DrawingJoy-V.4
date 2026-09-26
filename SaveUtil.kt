package com.mom.privatedrawing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveUtil {

    private fun timestampName(prefix: String, ext: String): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${prefix}_${sdf.format(Date())}.$ext"
    }

    /** Saves the drawing as a PNG into the device's Pictures gallery. Returns the file Uri, or null on failure. */
    fun savePngToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = timestampName("Drawing", "png")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DrawingJoy")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "DrawingJoy"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /** Saves a PNG into the app's own storage (used right before sharing/recording), returns its Uri. */
    fun saveTempPngForShare(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.getExternalFilesDir(null), "Drawings")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, timestampName("Drawing", "png"))
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Copies a recorded MP4 (currently sitting in the app's private storage) into the
     * public gallery, the same way savePngToGallery() does for images, so it actually
     * shows up in the Gallery/Photos app under Movies > DrawingJoy. Returns the new
     * public Uri, or null on failure.
     */
    fun saveVideoToGallery(context: Context, sourceFile: File): Uri? {
        val filename = sourceFile.name
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DrawingJoy")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out: OutputStream ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "DrawingJoy"
            )
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, filename)
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { out -> input.copyTo(out) }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        }
    }

    fun recordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newRecordingFile(context: Context): File {
        return File(recordingsDir(context), timestampName("Recording", "mp4"))
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
