package dev.wolzard.recon.feature.context

import dev.wolzard.recon.core.model.UserContext

/** [link] is already validated (share/paste); Start validates it again. */
data class ContextState(
    val link: String,
    val host: String,
    val text: String = "",
    val starting: Boolean = false,
    val error: String? = null,
) {
    val remaining get() = UserContext.MAX_LENGTH - text.length
}

sealed interface ContextIntent {
    data class TextChanged(val text: String) : ContextIntent
    data class Suggest(val text: String) : ContextIntent
    data object Start : ContextIntent
    data object Skip : ContextIntent
    data object Back : ContextIntent
}

sealed interface ContextEffect {
    data class OpenJob(val id: String) : ContextEffect
    data object NavigateBack : ContextEffect
}

object ContextCopy {
    const val TITLE = "Add context (optional)"
    const val HINT = "Ask something about the video, or say what to look for."
    const val INVALID_LINK = "This link can't be used. Share a video link starting with http or https."
    val SUGGESTIONS = listOf("What is this?", "Who is this?", "What song is this?", "Is this true?", "Where is this?")
}
