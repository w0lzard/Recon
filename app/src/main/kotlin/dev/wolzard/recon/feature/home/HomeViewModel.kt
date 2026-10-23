package dev.wolzard.recon.feature.home

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import dev.wolzard.recon.work.ShareCopy
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(store: JobStore, private val updates: UpdateChecker) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    init {
        viewModelScope.launch {
            combine(store.observeAll(), updates.available) { jobs, update -> jobs.map(HomeRow::from) to update }
                .collect { (rows, update) -> setState { copy(rows = rows, update = update) } }
        }
    }

    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.OpenJob -> send(HomeEffect.NavigateToJob(intent.id))
        HomeIntent.OpenSettings -> send(HomeEffect.NavigateToSettings)
        is HomeIntent.OpenLink -> send(HomeEffect.OpenInBrowser(intent.url))
        is HomeIntent.DismissUpdate -> updates.dismiss(intent.version)
        HomeIntent.SharedWithoutLink -> setState { copy(pasteError = ShareCopy.NO_LINK) }
        is HomeIntent.PasteLink -> {
            val link = LinkRouter.validLink(intent.text)
            setState { copy(pasteError = if (link == null) ShareCopy.NO_LINK else null) }
            if (link != null) send(HomeEffect.NavigateToContext(link))
            Unit
        }
    }
}
