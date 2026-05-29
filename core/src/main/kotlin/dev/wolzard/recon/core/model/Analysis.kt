package dev.wolzard.recon.core.model

import kotlinx.serialization.Serializable
import java.io.File

sealed interface AnalysisInput {
    data class YouTubeUrl(val url: String) : AnalysisInput
    data class VideoFile(val file: File, val mimeType: String) : AnalysisInput                // Path A
    data class FramesAndAudio(val frames: List<Frame>, val audio: File?) : AnalysisInput      // Path B
    data class Frame(val file: File, val timestampMs: Long)
}

/** Exactly the model JSON contract of the analyse call. */
@Serializable
data class AnalysisResult(
    val entities: List<Entity> = emptyList(),   // most important first, ≤ 8; empty is a valid answer
    val summary: String,
    val confidence: Double,                     // 0.0..1.0 that entities[0] is the video's main subject
    val onScreenText: List<String> = emptyList(),
)
