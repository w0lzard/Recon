package dev.wolzard.recon.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.data.update.UpdateResult
import dev.wolzard.recon.designsystem.UpdateBanner
import dev.wolzard.recon.core.copy.SetupCopy

@Composable
fun SettingsScreen(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Text("Gemini account", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(if (state.signedIn) "Signed in (stored encrypted on this phone)." else "Not signed in.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SignIn) }) { Text(if (state.signedIn) "Sign in again" else "Sign in") }
            if (state.signedIn) OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SignOut) }) { Text("Sign out") }
        }
        Text(SetupCopy.ACCOUNT_RISK, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("GitHub token (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text("Raises GitHub's lookup limit. A token with no scopes is enough.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = token, onValueChange = { token = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (state.hasGitHubToken) "Token saved; enter a new one to replace" else "Token") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(token)); token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
            if (state.hasGitHubToken) OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(null)) }) { Text("Remove token") }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Privacy", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(
            "Videos, or frames and audio, go to Gemini with your Google account, in temporary chats. Names of things found go to " +
                "GitHub and Wikipedia. Your history and sign-in stay on this phone. ${SetupCopy.DATA_NOTE}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("About", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text("Recon ${BuildConfig.VERSION_NAME}")
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.CheckForUpdates) }, enabled = !state.checkingUpdate) {
            Text(if (state.checkingUpdate) "Checking…" else "Check for updates")
        }
        when (val u = state.update) {
            is UpdateResult.Newer -> UpdateBanner(u.info, onOpen = { onIntent(SettingsIntent.OpenUpdate(it)) }, onDismiss = null)
            UpdateResult.UpToDate -> Text("Up to date")
            UpdateResult.Failed -> Text("Couldn't check. Try again later.")
            null -> Unit
        }
        TextButton(onClick = { onIntent(SettingsIntent.OpenReleases) }) { Text("Releases on GitHub") }
        TextButton(onClick = { onIntent(SettingsIntent.OpenNotice) }) { Text("Personal-use notice") }
    }
}
