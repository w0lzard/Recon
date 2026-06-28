package dev.wolzard.recon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.JobStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(store: JobStore) : ViewModel() {
    val rows: StateFlow<List<HomeRow>> = store.observeAll().map { jobs -> jobs.map(HomeRow::from) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
