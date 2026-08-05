package dev.wolzard.recon.feature.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [ContextViewModel] to [ContextScreen]; navigation is the caller's. */
@Composable
fun ContextRoute(vm: ContextViewModel, onOpenJob: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is ContextEffect.OpenJob -> onOpenJob(effect.id)
            ContextEffect.NavigateBack -> onBack()
        }
    }
    ContextScreen(state, vm::onIntent)
}
