package dev.wolzard.recon.feature.context

import androidx.compose.runtime.Composable
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [ContextViewModel] to [ContextScreen]; navigation is the caller's. */
@Composable
fun ContextRoute(vm: ContextViewModel, onOpenJob: (String) -> Unit, onBack: () -> Unit, compact: Boolean = false) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is ContextEffect.OpenJob -> {
                effect.previousAt?.let {
                    val ago = DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    Toast.makeText(ctx, "Opened previous analysis from $ago", Toast.LENGTH_SHORT).show()
                }
                onOpenJob(effect.id)
            }
            ContextEffect.NavigateBack -> onBack()
        }
    }
    ContextScreen(state, compact, vm::onIntent)
}
