package dev.wolzard.recon.feature.home

import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.data.update.UpdateInfo

data class HomeState(val rows: List<HomeRow> = emptyList(), val update: UpdateInfo? = null, val pasteError: String? = null, val repeatedFailure: FailureReason? = null, val total: Int = 0) {
    val hasMore get() = total > rows.size
}

sealed interface HomeIntent {
    data class OpenJob(val id: String) : HomeIntent
    data object OpenSettings : HomeIntent
    data object ShowMore : HomeIntent
    data object OpenLibrary : HomeIntent
    data class OpenLink(val url: String) : HomeIntent
    data class DismissUpdate(val version: String) : HomeIntent
    /** [text] = the link field (typed, pasted or filled from the clipboard); a valid link opens the context screen (D23). */
    /** A text share held no link: show the same inline error as a bad paste. */
    data object SharedWithoutLink : HomeIntent
    data class PasteLink(val text: String) : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToJob(val id: String) : HomeEffect
    data object NavigateToSettings : HomeEffect
    data object NavigateToLibrary : HomeEffect
    data class OpenInBrowser(val url: String) : HomeEffect
    data class NavigateToContext(val link: String) : HomeEffect
}
