package dev.wolzard.recon.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInState(val checking: Boolean = false, val error: String? = null, val done: Boolean = false)

/** Saves the Google cookies from the sign-in WebView (or pasted) and checks Gemini accepts them (D22). */
class SignInViewModel(private val secrets: SecretStore, private val verify: suspend () -> Unit) : ViewModel() {
    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** [raw] = a Cookie header value; a leading "Cookie:" from a copied request is dropped. */
    fun onCookies(raw: String) {
        if (_state.value.checking || _state.value.done) return
        val cookies = raw.trim().removePrefix("Cookie:").trim()
        if (!cookies.contains("__Secure-1PSID=")) {
            _state.update { it.copy(error = SetupCopy.NO_SESSION_COOKIE) }
            return
        }
        _state.update { it.copy(checking = true, error = null) }
        secrets.put(Secret.GEMINI_COOKIES, cookies)
        viewModelScope.launch {
            val error = try { verify(); null } catch (e: AnalyzerException) { SetupCopy.signInFailed(e.reason) }
            if (error != null) secrets.put(Secret.GEMINI_COOKIES, null)
            _state.update { it.copy(checking = false, error = error, done = error == null) }
        }
    }
}
