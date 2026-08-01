package dev.wolzard.recon.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * One screen, one loop: the UI sends an [Intent], the ViewModel reduces it into a new [State] and may emit a one-shot
 * [Effect] (navigate, open a link). State is what the screen shows; effects are consumed exactly once.
 */
abstract class MviViewModel<State, Intent, Effect>(initial: State) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<State> = _state.asStateFlow()

    // Buffered so an effect sent while the UI is between collectors (rotation) is delivered, not dropped.
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: Intent)

    protected fun setState(reduce: State.() -> State) = _state.update { it.reduce() }

    protected fun send(effect: Effect) { _effects.trySend(effect) }
}

/** Runs [onEffect] for every effect while this composable is in the composition. */
@Composable
fun <Effect> CollectEffects(effects: Flow<Effect>, onEffect: (Effect) -> Unit) {
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) { effects.collect { handler(it) } }
}
