package dev.wolzard.recon.feature.signin

import dev.wolzard.recon.core.copy.SetupCopy
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.CookieHeader
import dev.wolzard.recon.data.gemini.GeminiWebClient
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.launch

/** Saves the Google cookies from the sign-in WebView (or pasted) and checks Gemini accepts them before leaving (D22, D25). */
class SignInViewModel(private val secrets: SecretStore, private val verify: suspend () -> Unit) :
    MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {
    private var signedIn = false

    override fun onIntent(intent: SignInIntent) = when (intent) {
        is SignInIntent.CookiesReceived -> onCookies(intent.raw)
    }

    private fun onCookies(raw: String) {
        if (state.value.checking || signedIn) return
        val cookies = when (val p = CookieHeader.parse(raw)) {
            is CookieHeader.Parsed.Ok -> p.header
            CookieHeader.Parsed.Empty -> return setState { copy(error = SetupCopy.PASTE_EMPTY) }
            is CookieHeader.Parsed.Malformed -> return setState { copy(error = SetupCopy.pasteMalformed(p.name)) }
            CookieHeader.Parsed.NoSession -> return setState { copy(error = SetupCopy.NO_SESSION_COOKIE) }
        }
        setState { copy(checking = true, error = null) }
        secrets.put(Secret.GEMINI_COOKIES, cookies)
        viewModelScope.launch {
            val error = try {
                verify(); null
            } catch (e: AnalyzerException) {
                if (e.message == GeminiWebClient.GOOGLE_CHALLENGE) SetupCopy.GOOGLE_CHALLENGE else SetupCopy.signInFailed(e.reason)
            }
            if (error != null) secrets.put(Secret.GEMINI_COOKIES, null)
            signedIn = error == null
            setState { copy(checking = false, error = error) }
            if (signedIn) send(SignInEffect.SignedIn)
        }
    }
}
