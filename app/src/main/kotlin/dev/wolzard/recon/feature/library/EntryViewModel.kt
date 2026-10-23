package dev.wolzard.recon.feature.library

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.EntryStore
import dev.wolzard.recon.feature.home.HomeRow
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class EntryViewModel(id: Long, entries: EntryStore) : MviViewModel<EntryState, EntryIntent, EntryEffect>(EntryState()) {
    init {
        viewModelScope.launch {
            combine(entries.observe(id), entries.observeVideos(id)) { e, videos -> EntryState(e, videos.map { HomeRow.from(it) }, loaded = true) }
                .flowOn(Dispatchers.Default)
                .collect { s -> setState { s } }
        }
    }

    override fun onIntent(intent: EntryIntent) {
        when (intent) {
            is EntryIntent.OpenVideo -> send(EntryEffect.NavigateToJob(intent.id))
            is EntryIntent.OpenLink -> send(EntryEffect.OpenInBrowser(intent.url))
            EntryIntent.Back -> send(EntryEffect.NavigateBack)
        }
    }
}
