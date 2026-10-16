package dev.wolzard.recon.feature.job

import dev.wolzard.recon.core.media.VideoSaveOption
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult

/** [job] is null until the first read from the store. */
data class JobViewState(
    val job: Job? = null,
    val result: JobResult? = null,
    val isProbingFormats: Boolean = false,
    val saveOptions: List<VideoSaveOption>? = null,
    val saveError: String? = null,
)

sealed interface JobIntent {
    data object Retry : JobIntent
    data object CheckAgain : JobIntent
    data object LookUp : JobIntent
    data object TrySharper : JobIntent
    data object SignIn : JobIntent
    /** Back from sign-in that this job's "Sign in again" opened (D25). */
    data object SignedInAgain : JobIntent
    data object OpenSettings : JobIntent
    data object Cancel : JobIntent
    data object Back : JobIntent
    data class Pick(val findingIndex: Int, val repo: GhRepo?) : JobIntent   // null = "None of these"
    data class Open(val url: String) : JobIntent
    data object RequestSaveVideo : JobIntent
    data object DismissSaveDialog : JobIntent
}

sealed interface JobEffect {
    data object NavigateBack : JobEffect
    data object NavigateToSignIn : JobEffect
    data object NavigateToSettings : JobEffect
    data class OpenUrl(val url: String) : JobEffect
}
