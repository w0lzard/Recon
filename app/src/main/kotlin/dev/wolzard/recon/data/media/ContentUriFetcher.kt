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
