package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.AnalysisInput
import java.io.File

/** [audio] is a separate audio file (YouTube two-file fallback); null when [video] carries its own audio track. */
data class FetchedMedia(val video: File, val audio: File? = null, val formatId: String? = null)

enum class FetchQuality {
    DEFAULT,              // ≤ 480p muxed (non-YouTube) or ≤ 480p avc1 + m4a (YouTube)
    OTHER_FORMAT,         // zero-frame retry: anything but the format that gave no frames (T7/T8)
    SHARPER,              // user-triggered "Try sharper video", ≤ 720p (D21)
}

interface MediaFetcher {                              // YtDlpFetcher, ContentUriFetcher
    /** Streams media into [destDir]. @throws AnalyzerException(DOWNLOAD_FAILED|NETWORK) */
    suspend fun fetch(
        source: String,
        destDir: File,
        quality: FetchQuality = FetchQuality.DEFAULT,
        avoidFormatId: String? = null,
        onProgress: (Float) -> Unit = {},
    ): FetchedMedia
}

interface FrameSampler {
    /** ≤ 20 frames every max(2 s, duration/20), near-duplicates skipped, + audio track copy. @throws AnalyzerException(VIDEO_UNREADABLE) */
    suspend fun sample(video: File, outDir: File): AnalysisInput.FramesAndAudio
}
