package dev.wolzard.recon.feature.context

import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.core.model.UserContext

/** [link] is already validated (share/paste); Start validates it again. */
data class ContextState(
    val link: String,
    val host: String,
    val text: String = "",
    val starting: Boolean = false,
    val error: String? = null,
    val preview: LinkPreview? = null,   // D26: best effort; null until (and unless) the metadata call returns
    val duplicateJobId: String? = null, // D31: set when previous DONE job exists and context was typed
) {
    val remaining get() = UserContext.MAX_LENGTH - text.length
}

sealed interface ContextIntent {
    data class TextChanged(val text: String) : ContextIntent
    data class Suggest(val text: String) : ContextIntent
    data object Start : ContextIntent
    data object Skip : ContextIntent
    data object Back : ContextIntent
    data object ViewPrevious : ContextIntent
    data object Reanalyse : ContextIntent
    data object DismissDuplicateDialog : ContextIntent
}

sealed interface ContextEffect {
    /** [previousAt] is set when this opens an earlier finished analysis of the same link (D36): the route says so. */
    data class OpenJob(val id: String, val previousAt: Long? = null) : ContextEffect
    data object NavigateBack : ContextEffect
}

object ContextCopy {
    const val TITLE = "Add context"
    const val SHARED_FILE = "Shared video"
    const val PLACEHOLDER = "Ask something about this video (optional)"
    const val SENT_WITH_VIDEO = "Sent to Gemini with the video"
    const val INVALID_LINK = "This link can't be used. Share a video link starting with http or https."
    val SUGGESTIONS = listOf("What movie is this?", "What song is this?", "Who or what is this?", "Explain this")
}
