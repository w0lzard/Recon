package dev.wolzard.recon.data.media

import dev.wolzard.recon.core.media.VideoQualitySelector
import dev.wolzard.recon.core.media.VideoSaveOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Fetches available video formats for a URL using yt-dlp `--dump-json`.
 */
class VideoFormatFetcher(
    private val runner: YtDlpRunner,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    suspend fun fetchOptions(url: String): List<VideoSaveOption> = withTimeoutOrNull(timeoutMs) {
        try {
            val processId = "probe-${UUID.randomUUID()}"
            val args = listOf("--dump-json", "--skip-download", "--no-playlist")
            val output = runner.run(url, args, processId) {}
            VideoQualitySelector.parseOptions(output)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    } ?: emptyList()

    companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
