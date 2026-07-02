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

