package dev.wolzard.recon.feature.context

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.link.hostOf
import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.core.model.UserContext
import dev.wolzard.recon.feature.job.JobActions
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.launch

/** [preview] (D26) is fetched once in the background; Start and Skip never wait for it. */
class ContextViewModel(link: String, private val actions: JobActions, preview: (suspend (String) -> LinkPreview?)? = null) :
    MviViewModel<ContextState, ContextIntent, ContextEffect>(ContextState(link, hostOf(link))) {

    init {
        if (preview != null) viewModelScope.launch { preview(link)?.let { p -> setState { copy(preview = p) } } }
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
        }
    }

    private fun start(context: String?) {
        if (state.value.starting) return
        setState { copy(starting = true) }
        viewModelScope.launch {
            val id = actions.start(state.value.link, context, state.value.preview)   // re-validates the link; creates nothing if it fails
            if (id == null) setState { copy(starting = false, error = ContextCopy.INVALID_LINK) }
            else send(ContextEffect.OpenJob(id))
        }
    }
}
