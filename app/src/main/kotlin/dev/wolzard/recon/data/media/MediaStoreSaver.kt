package dev.wolzard.recon.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.IOException

object MediaStoreSaver {

    /**
     * Saves [sourceFile] into MediaStore under Movies/Recon on API 29+.
     * Uses IS_PENDING while copying so partially written files are not visible to other apps.
     * Stream-copies with a 64KB buffer; never loads the file into memory.
     */
    fun saveToMediaStore(context: Context, sourceFile: File, title: String): Uri? {
        val cleanTitle = cleanFileName(title)
        val ext = sourceFile.extension.ifEmpty { "mp4" }
        val mimeType = mimeTypeFor(ext)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$cleanTitle.$ext")
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Recon")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().buffered().use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            } ?: throw IOException("Could not open MediaStore output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /**
     * Stream-copies [sourceFile] into the user-selected document [targetUri] (for API 26-28 ACTION_CREATE_DOCUMENT).
     */
    fun saveToUri(context: Context, sourceFile: File, targetUri: Uri) {
        val resolver = context.contentResolver
        resolver.openOutputStream(targetUri)?.use { out ->
            sourceFile.inputStream().buffered().use { input ->
                input.copyTo(out, bufferSize = 64 * 1024)
            }
        } ?: throw IOException("Could not open output stream for $targetUri")
    }

    fun cleanFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "video" }

    fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }
}
