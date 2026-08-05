package dev.wolzard.recon.feature.home

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(store: JobStore, private val updates: UpdateChecker) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    init {
        viewModelScope.launch {
            combine(store.observeAll(), updates.available) { jobs, update -> jobs.map(HomeRow::from) to update }
                .collect { (rows, update) -> setState { copy(rows = rows, update = update) } }
        }
    }

    private companion object { const val NO_LINK = "No video link on the clipboard. Copy one first." }

    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.OpenJob -> send(HomeEffect.NavigateToJob(intent.id))
        HomeIntent.OpenSettings -> send(HomeEffect.NavigateToSettings)
        is HomeIntent.OpenLink -> send(HomeEffect.OpenInBrowser(intent.url))
        is HomeIntent.DismissUpdate -> updates.dismiss(intent.version)
        is HomeIntent.PasteLink -> {
            val link = LinkRouter.validLink(intent.text)
            setState { copy(pasteError = if (link == null) NO_LINK else null) }
            if (link != null) send(HomeEffect.NavigateToContext(link))
            Unit
        }
    }
}
