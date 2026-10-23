package dev.wolzard.recon.feature.context

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.link.UrlNormalizer
import dev.wolzard.recon.core.link.hostOf
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.core.model.UserContext
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.feature.job.JobActions
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.launch

/**
 * [preview] (D26) is fetched once in the background; Start and Skip never wait for it. With [localUri] (share sheet)
 * the shared item is a video file already copied there, and [link] is the share's text; no preview is fetched.
 * D31: checks for previous jobs with the same normalized URL.
 */
class ContextViewModel(
    link: String,
    private val actions: JobActions,
    private val store: JobStore? = null,
    private val normalizer: UrlNormalizer? = null,
    private val localUri: String? = null,
    preview: (suspend (String) -> LinkPreview?)? = null,
) : MviViewModel<ContextState, ContextIntent, ContextEffect>(ContextState(link, if (localUri != null) ContextCopy.SHARED_FILE else hostOf(link))) {

    init {
        if (preview != null && localUri == null) viewModelScope.launch { preview(link)?.let { p -> setState { copy(preview = p) } } }
    }

    override fun onIntent(intent: ContextIntent) {
        when (intent) {
            is ContextIntent.TextChanged -> setState { copy(text = intent.text.take(UserContext.MAX_LENGTH), error = null) }
            is ContextIntent.Suggest -> setState {
                copy(text = (if (text.isBlank()) intent.text else "${text.trimEnd()} ${intent.text}").take(UserContext.MAX_LENGTH), error = null)
            }
            ContextIntent.Start -> start(state.value.text)
            ContextIntent.Skip -> start(null)
            ContextIntent.Back -> send(ContextEffect.NavigateBack)
            ContextIntent.ViewPrevious -> state.value.duplicateJobId?.let { send(ContextEffect.OpenJob(it)) }
            ContextIntent.Reanalyse -> forceReanalyse(state.value.text)
            ContextIntent.DismissDuplicateDialog -> setState { copy(duplicateJobId = null, starting = false) }
        }
    }

    private fun start(context: String?) {
        if (state.value.starting) return
        setState { copy(starting = true) }
        viewModelScope.launch {
            if (localUri == null && store != null && normalizer != null) {
                val validLink = LinkRouter.validLink(state.value.link)
                if (validLink != null) {
                    val normalized = normalizer.normalize(validLink)
                    val existing = store.findByNormalizedUrl(normalized).firstOrNull()
                    if (existing != null) {
                        when (existing.state) {
                            JobState.DONE -> {
                                if (context.isNullOrBlank()) {
                                    send(ContextEffect.OpenJob(existing.id, previousAt = existing.createdAt))
                                    return@launch
                                } else {
                                    setState { copy(duplicateJobId = existing.id, starting = false) }
                                    return@launch
                                }
                            }
                            JobState.QUEUED, JobState.DOWNLOADING, JobState.ANALYSING, JobState.RESEARCHING, JobState.WAITING_QUOTA -> {
                                send(ContextEffect.OpenJob(existing.id))
                                return@launch
                            }
                            JobState.FAILED -> if (existing.failure == FailureReason.NETWORK && existing.localUri == null) {
                                // The same link again after a network failure retries that job instead of starting another.
                                actions.requeue(existing.id, UserAction.RUN)
                                send(ContextEffect.OpenJob(existing.id))
                                return@launch
                            } // other failures: start a fresh attempt without prompting
                        }
                    }
                }
            }

            val id = if (localUri != null) actions.startFile(localUri, state.value.link, context)
            else actions.start(state.value.link, context, state.value.preview)
            if (id == null) setState { copy(starting = false, error = ContextCopy.INVALID_LINK) }
            else send(ContextEffect.OpenJob(id))
        }
    }

    private fun forceReanalyse(context: String?) {
        setState { copy(duplicateJobId = null, starting = true) }
        viewModelScope.launch {
            val id = actions.start(state.value.link, context, state.value.preview)
            if (id == null) setState { copy(starting = false, error = ContextCopy.INVALID_LINK) }
            else send(ContextEffect.OpenJob(id))
        }
    }
}
