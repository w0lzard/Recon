package dev.wolzard.recon.data.link

import dev.wolzard.recon.core.model.LinkPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * D26: title, length and thumbnail for the Add context card, from one yt-dlp metadata call ([metadata] returns its
 * --dump-json output). Best effort: null on timeout, error or nothing usable, and the caller shows the link icon.
 * Timing out cancels the call, which kills the yt-dlp process (YoutubeDlRunner), so a job's download isn't kept waiting.
 */
class LinkPreviewFetcher(private val metadata: suspend (url: String) -> String, private val timeoutMs: Long = TIMEOUT_MS) {
    suspend fun fetch(url: String): LinkPreview? = withTimeoutOrNull(timeoutMs) {
        try {
            parse(metadata(url))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val MAX_TITLE = 120
        private val json = Json { ignoreUnknownKeys = true }

        /** Platform text is untrusted: control characters out, whitespace collapsed, length capped. A non-https thumbnail is dropped. */
        fun parse(out: String): LinkPreview? {
            val o = runCatching { json.parseToJsonElement(out.trim().lineSequence().first { it.startsWith("{") }) }.getOrNull() as? JsonObject ?: return null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            val title = str("title")?.replace(Regex("[\\p{Cntrl}]"), " ")?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { if (it.length <= MAX_TITLE) it else it.take(MAX_TITLE - 1).trimEnd() + "…" }
            val duration = (o["duration"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it >= 0 && it < 100_000 }?.toLong()
            val thumb = str("thumbnail")?.takeIf(::isHttps)
            return LinkPreview(title, duration, thumb).takeUnless { it.isEmpty }
        }

        fun isHttps(url: String?): Boolean = url?.toHttpUrlOrNull()?.scheme == "https"
    }
}
