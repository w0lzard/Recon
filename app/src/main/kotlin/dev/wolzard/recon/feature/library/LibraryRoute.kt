package dev.wolzard.recon.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.designsystem.openInBrowser
import dev.wolzard.recon.mvi.CollectEffects

@Composable
fun LibraryRoute(vm: LibraryViewModel, onOpenEntry: (Long) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is LibraryEffect.NavigateToEntry -> onOpenEntry(effect.id)
            LibraryEffect.NavigateBack -> onBack()
        }
    }
    LibraryScreen(state, vm::onIntent)
}

@Composable
fun EntryRoute(vm: EntryViewModel, onOpenJob: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is EntryEffect.NavigateToJob -> onOpenJob(effect.id)
            is EntryEffect.OpenInBrowser -> ctx.openInBrowser(effect.url)
            EntryEffect.NavigateBack -> onBack()
        }
    }
    EntryScreen(state, vm::onIntent)
}
