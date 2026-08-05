package dev.wolzard.recon.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.designsystem.openInBrowser
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [HomeViewModel] to [HomeScreen]; navigation is the caller's, links open here. */
@Composable
fun HomeRoute(vm: HomeViewModel, onOpenJob: (String) -> Unit, onOpenSettings: () -> Unit, onAddContext: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is HomeEffect.NavigateToJob -> onOpenJob(effect.id)
            HomeEffect.NavigateToSettings -> onOpenSettings()
            is HomeEffect.NavigateToContext -> onAddContext(effect.link)
            is HomeEffect.OpenInBrowser -> ctx.openInBrowser(effect.url)
        }
    }
    HomeScreen(state, vm::onIntent)
}
