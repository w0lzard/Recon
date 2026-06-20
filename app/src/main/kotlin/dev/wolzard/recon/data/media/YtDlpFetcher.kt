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
    ): FetchedMedia {
        destDir.mkdirs()
        val processId = destDir.name
        return try {
            if (LinkRouter.classify(source) is Route.YouTube) {
                val (formatId, video) = parse(runner.run(source, youTubeVideoArgs(destDir, quality, avoidFormatId), processId) { onProgress(it * 0.8f) }, destDir, "video")
                val (_, audio) = parse(runner.run(source, youTubeAudioArgs(destDir), processId) { onProgress(0.8f + it * 0.2f) }, destDir, "audio")
                FetchedMedia(video, audio, formatId)
            } else {
                val (formatId, video) = parse(runner.run(source, buildArgs(destDir, quality, avoidFormatId), processId, onProgress), destDir, "video")
                FetchedMedia(video, null, formatId)
            }
        } catch (e: YtDlpException) {
            val stderr = e.message.orEmpty()
            val last = stderr.lines().lastOrNull { it.startsWith("ERROR") } ?: stderr.lines().lastOrNull(String::isNotBlank).orEmpty()
            val reason = if (NETWORK_RE.containsMatchIn(stderr)) FailureReason.NETWORK else FailureReason.DOWNLOAD_FAILED
            throw AnalyzerException(reason, message = last.take(300), cause = e)
        }
    }

    /** Last "format_id|filepath" line printed by `--print after_move:…`; falls back to the expected file name. */
    private fun parse(stdout: String, destDir: File, stem: String): Pair<String?, File> {
        val line = stdout.lines().lastOrNull { it.contains('|') }
        val file = line?.substringAfter('|')?.trim()?.let(::File)?.takeIf { it.exists() }
            ?: destDir.listFiles()?.firstOrNull { it.name.startsWith("$stem.") && !it.name.endsWith(".part") }
            ?: throw AnalyzerException(FailureReason.DOWNLOAD_FAILED, message = "yt-dlp produced no file")
        return line?.substringBefore('|')?.trim()?.ifEmpty { null } to file
    }

    companion object {
        private const val CAP_480 = "[height<=854][width<=854]"
        private const val CAP_720 = "[height<=1280][width<=1280]"
        private val NETWORK_RE = Regex("""Errno (7|-2|-3|101|110|111)|timed out|Network is unreachable|Temporary failure in name resolution""")

        fun buildArgs(destDir: File, quality: FetchQuality = FetchQuality.DEFAULT, avoid: String? = null): List<String> {
            val x = avoid?.let { "[format_id!=$it]" }.orEmpty()
            val format = when (quality) {
                FetchQuality.DEFAULT -> "b$CAP_480/b$CAP_720/w"
                FetchQuality.SHARPER -> "b$CAP_720/w"
                FetchQuality.OTHER_FORMAT -> "bv*[vcodec^=avc1]$CAP_480$x/b$CAP_480$x/w$x"
            }
            return common(format, destDir, "video")
        }

        fun youTubeVideoArgs(destDir: File, quality: FetchQuality, avoid: String?): List<String> {
            val cap = if (quality == FetchQuality.SHARPER) CAP_720 else CAP_480
            val x = if (quality == FetchQuality.OTHER_FORMAT) avoid?.let { "[format_id!=$it]" }.orEmpty() else ""
            return common("bv*[vcodec^=avc1]$cap$x/bv*[vcodec!^=av01]$cap$x", destDir, "video")
        }

        fun youTubeAudioArgs(destDir: File): List<String> = common("ba[ext=m4a]", destDir, "audio")

        private fun common(format: String, destDir: File, stem: String) = listOf(
            "-f", format,
            "--no-playlist",
            "-o", "${destDir.path}/$stem.%(ext)s",
            "--print", "after_move:%(format_id)s|%(filepath)s", "--no-simulate",
        )
    }
}
