package dev.wolzard.recon.data.media

import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.link.Route
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.FetchQuality
import dev.wolzard.recon.core.port.FetchedMedia
import dev.wolzard.recon.core.port.MediaFetcher
import java.io.File

/** Runs yt-dlp; [run] returns stdout and throws [YtDlpException] with stderr on failure. */
interface YtDlpRunner {
    suspend fun run(url: String, args: List<String>, processId: String, onProgress: (Float) -> Unit): String
}

class YtDlpException(stderr: String) : Exception(stderr)

/**
 * Link downloads (D5, policy list): ≤ 480p muxed for non-YouTube; YouTube has no muxed formats, so it is
 * two separate files (avc1 video ≤ 480p + m4a audio), never merged and never AV1 (API 26 can't decode it).
 */
class YtDlpFetcher(private val runner: YtDlpRunner) : MediaFetcher {

    override suspend fun fetch(
        source: String,
        destDir: File,
        quality: FetchQuality,
        avoidFormatId: String?,
        onProgress: (Float) -> Unit,
