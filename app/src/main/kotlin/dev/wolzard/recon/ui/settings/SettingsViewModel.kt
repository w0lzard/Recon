package dev.wolzard.recon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.data.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val signedIn: Boolean = false,
    val hasGitHubToken: Boolean = false,
    val checkingUpdate: Boolean = false,
    val update: UpdateResult? = null,
)

class SettingsViewModel(
    private val secrets: SecretStore,
    private val updates: UpdateChecker? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update {
            it.copy(
                signedIn = secrets.get(Secret.GEMINI_COOKIES) != null,
                hasGitHubToken = secrets.get(Secret.GITHUB_TOKEN) != null,
            )
        }
    }

    /** Forgets the saved Google cookies; the WebView's own copy is cleared by the screen. */
    fun signOut() { secrets.put(Secret.GEMINI_COOKIES, null); refresh() }

    fun saveGitHubToken(token: String?) { secrets.put(Secret.GITHUB_TOKEN, token?.trim()?.ifEmpty { null }); refresh() }

    /** About → "Check for updates": forced, result shown inline. */
    fun checkForUpdates() {
        val u = updates ?: return
        _state.update { it.copy(checkingUpdate = true, update = null) }
        viewModelScope.launch {
            val r = u.checkNow()
            if (r is UpdateResult.Newer) u.check() // refresh Home's banner from the stored result
            _state.update { it.copy(checkingUpdate = false, update = r) }
        }
    }
}
