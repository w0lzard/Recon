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
    val answer: Answer? = null,                 // D23: only when the viewer added a note or question
)

enum class AnswerStatus { VERIFIED, UNVERIFIED, UNCLEAR }

/** The reply to the viewer's question (D23). Model-written, so every field is bounded by ResultParser. */
@Serializable
data class Answer(
    val text: String,
    val status: AnswerStatus,
    val confidence: Double,                     // 0.0..1.0
    val sources: List<String> = emptyList(),    // where in the video / which reference; plain text, never opened as links
    // D26: optional, model-tagged, shown only as a label and a title line; never used to decide the status.
    val kind: EntityType? = null,               // allow-listed; OTHER and unknown words become null
    val subtype: MediaType? = null,             // only with kind = MEDIA
    val title: String? = null,                  // the thing the answer names, capped (ResultParser)
)
