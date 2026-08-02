package dev.wolzard.recon.feature.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [SignInViewModel] to [SignInScreen]; [onSignedIn] leaves the screen once Gemini accepted the cookies. */
@Composable
fun SignInRoute(vm: SignInViewModel, onSignedIn: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CollectEffects(vm.effects) { if (it == SignInEffect.SignedIn) onSignedIn() }
    SignInScreen(state, vm::onIntent)
}
