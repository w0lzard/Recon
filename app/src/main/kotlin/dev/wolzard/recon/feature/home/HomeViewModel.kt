package dev.wolzard.recon.feature.home

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(store: JobStore, private val updates: UpdateChecker) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    init {
        viewModelScope.launch {
            combine(store.observeAll(), updates.available) { jobs, update -> HomeState(jobs.map(HomeRow::from), update) }
                .collect { s -> setState { s } }
        }
    }

    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.OpenJob -> send(HomeEffect.NavigateToJob(intent.id))
        HomeIntent.OpenSettings -> send(HomeEffect.NavigateToSettings)
        is HomeIntent.OpenLink -> send(HomeEffect.OpenInBrowser(intent.url))
        is HomeIntent.DismissUpdate -> updates.dismiss(intent.version)
    }
}
