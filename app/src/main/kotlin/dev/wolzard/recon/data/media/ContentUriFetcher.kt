package dev.wolzard.recon.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.FetchQuality
import dev.wolzard.recon.core.port.FetchedMedia
import dev.wolzard.recon.core.port.MediaFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Copies a shared video into the job dir through an 8 KB buffer.
 *
 * Security (confused deputy): only `content://` URIs from the share, or files Recon itself copied under
 * [allowedLocalRoot]. A `file://` URI from another app could otherwise point at Recon's private files (prefs,
 * database) and get them uploaded to Gemini. Local paths are percent-decoded once, canonicalised, checked, and
 * that exact [File] is opened ([openFile]); a `file://` URI never reaches the ContentResolver, so the check and
 * the open can't disagree (`%2e%2e`). Copies stop at [maxBytes].
 */
class ContentUriFetcher(
    private val open: (String) -> InputStream?,               // content:// only
    private val describe: (String) -> Pair<String?, Long?>,   // mime type, size in bytes
    private val allowedLocalRoot: File?,
    private val openFile: (File) -> InputStream = { it.inputStream() },
    private val maxBytes: Long = MAX_BYTES,
) : MediaFetcher {

    constructor(context: Context) : this(
        open = { context.contentResolver.openInputStream(Uri.parse(it)) },
        describe = { uri ->
            val u = Uri.parse(uri)
            val size = runCatching {
                context.contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                }
            }.getOrNull()
            context.contentResolver.getType(u) to size
        },
        allowedLocalRoot = File(context.filesDir, SHARED_DIR),
    )

    override suspend fun fetch(
        source: String,
        destDir: File,
        quality: FetchQuality,
        avoidFormatId: String?,
        onProgress: (Float) -> Unit,
    ): FetchedMedia = withContext(Dispatchers.IO) {
        val local = localFileOrNull(source)
        destDir.mkdirs()
        val (mime, size) = if (local != null) null to local.length() else describe(source)
        val out = File(destDir, "video.${extension(mime ?: local?.extension?.let { "video/$it" })}")
        val input = (if (local != null) openFile(local) else open(source))
            ?: throw AnalyzerException(FailureReason.DOWNLOAD_FAILED, message = "shared file can't be opened")
        try {
            input.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(BUFFER)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = inp.read(buf, 0, BUFFER)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        copied += n
                        if (copied > maxBytes) {
                            out.delete()
                            throw AnalyzerException(FailureReason.DOWNLOAD_FAILED, message = "shared file is too large")
                        }
                        if (size != null && size > 0) onProgress((copied.toFloat() / size).coerceAtMost(1f))
                    }
                }
            }
        } catch (e: java.io.IOException) {
            throw AnalyzerException(FailureReason.DOWNLOAD_FAILED, message = "shared file can't be read", cause = e)
        }
        onProgress(1f)
        FetchedMedia(out)
    }

    /** null for content:// (opened by the resolver); the validated canonical file for local sources; else refuse. */
    private fun localFileOrNull(source: String): File? {
        val scheme = source.substringBefore("://", missingDelimiterValue = "").lowercase()
        val path = when (scheme) {
            "content" -> return null
            "file" -> runCatching { java.net.URI(source) }.getOrNull()?.takeIf { it.host == null }?.path ?: refuse()
            "" -> runCatching { java.net.URLDecoder.decode(source.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: refuse()
            else -> refuse()
        }
        val root = allowedLocalRoot?.canonicalFile ?: refuse()
        val file = File(path).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) refuse()
        return file
    }

    private fun refuse(): Nothing = throw AnalyzerException(FailureReason.DOWNLOAD_FAILED, message = "this shared file can't be used")

    private fun extension(mime: String?) = when (mime) {
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        "video/x-matroska" -> "mkv"
        else -> "mp4"
    }

    companion object {
        private const val BUFFER = 8 * 1024
        /** filesDir/shared: where the share activity copies incoming videos (T12). */
        const val SHARED_DIR = "shared"
        /** Larger shares are refused (protects storage); Path A already skips files over 50 MB. */
        const val MAX_BYTES = 512L * 1024 * 1024
    }
}
