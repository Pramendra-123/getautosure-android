package com.enginesense.app.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** content:// URI for the generated report, via the FileProvider declared in AndroidManifest.xml. */
fun reportContentUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun viewPdfIntent(context: Context, file: File): Intent {
    val uri = reportContentUri(context, file)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

fun sharePdfIntent(context: Context, file: File): Intent {
    val uri = reportContentUri(context, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, "Share report")
}

/**
 * Copies the report into the device's public Downloads folder so it survives outside the app
 * and shows up in Files/Downloads apps. API 29+ uses scoped-storage MediaStore, no permission
 * needed. API 26-28 (this app's minSdk) needs the legacy external-storage path, which the
 * caller must have WRITE_EXTERNAL_STORAGE granted for first — see [needsLegacyStoragePermission].
 */
fun saveToDownloads(context: Context, file: File): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        true
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val dest = File(downloadsDir, file.name)
        file.copyTo(dest, overwrite = true)
        true
    }
}

/** Only API 26-28 (this app's minSdk floor) needs the legacy runtime permission before [saveToDownloads]; 29+ uses scoped storage. */
fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
