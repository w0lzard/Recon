package dev.wolzard.recon.feature.library

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.EntryStore
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** [backfill] files finished jobs from before the library existed; it runs once per install (the caller keeps the flag). */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(entries: EntryStore, backfill: suspend () -> Unit = {}) : MviViewModel<LibraryState, LibraryIntent, LibraryEffect>(LibraryState()) {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val limit = MutableStateFlow(LIBRARY_PAGE)

    init {
        viewModelScope.launch { backfill() }
        viewModelScope.launch {
            combine(query, filter, limit) { q, f, l -> Triple(q, f, l) }
                .flatMapLatest { (q, f, l) -> entries.observeList(q, f.types, l) }
                .collect { list -> setState { copy(entries = list, loaded = true) } }
        }
    }

    override fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.QueryChanged -> { setState { copy(query = intent.text, limit = LIBRARY_PAGE) }; limit.value = LIBRARY_PAGE; query.value = intent.text }
            is LibraryIntent.FilterChanged -> { setState { copy(filter = intent.filter, limit = LIBRARY_PAGE) }; limit.value = LIBRARY_PAGE; filter.value = intent.filter }
            LibraryIntent.ShowMore -> { val next = limit.value + LIBRARY_PAGE; limit.value = next; setState { copy(limit = next) } }
            is LibraryIntent.OpenEntry -> send(LibraryEffect.NavigateToEntry(intent.id))
            LibraryIntent.Back -> send(LibraryEffect.NavigateBack)
        }
    }
}
