package dev.wolzard.recon

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.work.ShareIntake
import dev.wolzard.recon.work.ShareOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the share sheet shows. */
sealed interface SheetStep {
    data object Loading : SheetStep
    data class Link(val link: String) : SheetStep
    data class VideoFile(val localUri: String, val sharedText: String) : SheetStep
    data class Job(val id: String) : SheetStep
    data object Nothing : SheetStep
}

class ShareSheetViewModel(private val intake: ShareIntake) : ViewModel() {
    val step = MutableStateFlow<SheetStep>(SheetStep.Loading)

    fun accept(intent: Intent) = viewModelScope.launch {
        step.value = when (val o = runCatching { intake.accept(intent, fileContext = true) }.getOrNull()) {
            is ShareOutcome.NeedsContext -> SheetStep.Link(o.link)
            is ShareOutcome.FileNeedsContext -> SheetStep.VideoFile(o.localUri, o.sharedText)
            is ShareOutcome.Started -> SheetStep.Job(o.jobId)
            null -> SheetStep.Nothing
        }
    }

    fun open(jobId: String) { step.value = SheetStep.Job(jobId) }
}
