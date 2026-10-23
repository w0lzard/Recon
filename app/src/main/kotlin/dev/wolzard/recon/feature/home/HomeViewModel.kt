package dev.wolzard.recon.feature.home

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.diagnostics.Diagnostics
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import dev.wolzard.recon.work.ShareCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(store: JobStore, private val updates: UpdateChecker) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    private val limit = MutableStateFlow(PAGE)

    init {
        viewModelScope.launch {
            // Paged: the list grows by PAGE on "Show more"; the rows are built off the main thread.
            combine(limit.flatMapLatest { store.observeRecent(it) }, store.observeCount(), updates.available) { jobs, total, update ->
                HomeState(jobs.map(HomeRow::from), update, repeatedFailure = Diagnostics.repeatedFailure(jobs), total = total)
            }.flowOn(Dispatchers.Default)
                .collect { s -> setState { copy(rows = s.rows, update = s.update, repeatedFailure = s.repeatedFailure, total = s.total) } }
        }
    }

    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.OpenJob -> send(HomeEffect.NavigateToJob(intent.id))
        HomeIntent.OpenSettings -> send(HomeEffect.NavigateToSettings)
        HomeIntent.OpenLibrary -> send(HomeEffect.NavigateToLibrary)
        HomeIntent.ShowMore -> limit.update { it + PAGE }
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

private const val PAGE = 30
