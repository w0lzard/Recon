package dev.wolzard.recon.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.font.FontWeight
import dev.wolzard.recon.designsystem.Mono
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

/** Mono section label over a hairline group, as in the design (ACCOUNT, UPDATES, ABOUT). */
@Composable
private fun Group(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

@Composable
fun SettingsScreen(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    var token by remember { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        (state.update as? UpdateResult.Newer)?.let { UpdateBanner(it.info, onOpen = { url -> onIntent(SettingsIntent.OpenUpdate(url)) }, onDismiss = null) }

        Group("GEMINI ACCOUNT") {
            Text(if (state.signedIn) "Signed in (stored encrypted on this phone)." else "Not signed in.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SignIn) }) { Text(if (state.signedIn) "Sign in again" else "Sign in") }
                if (state.signedIn) OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SignOut) }) { Text("Sign out") }
            }
            Text(SetupCopy.ACCOUNT_RISK, color = muted, style = MaterialTheme.typography.bodyMedium)
        }

        Group("GITHUB TOKEN (OPTIONAL)") {
            Text("Raises GitHub's lookup limit. A token with no scopes is enough.", color = muted, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.small,
                label = { Text(if (state.hasGitHubToken) "Token saved; enter a new one to replace" else "Token") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(token)); token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
                if (state.hasGitHubToken) OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(null)) }) { Text("Remove token") }
            }
        }

        Group("PRIVACY") {
            Text(
                "Videos, or frames and audio, go to Gemini with your Google account, in temporary chats. Names of things found go to " +
                    "GitHub and Wikipedia. Your history and sign-in stay on this phone. ${SetupCopy.DATA_NOTE}",
                color = muted, style = MaterialTheme.typography.bodyMedium,
            )
        }

        Group("UPDATES") {
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.CheckForUpdates) }, enabled = !state.checkingUpdate) {
                Text(if (state.checkingUpdate) "Checking…" else "Check for updates")
            }
            when (state.update) {
                UpdateResult.UpToDate -> Text("Up to date")
                UpdateResult.Failed -> Text("Couldn't check. Try again later.")
                else -> Unit
            }
        }

        Group("ABOUT") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Version", color = muted)
                Text("Recon ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { onIntent(SettingsIntent.OpenReleases) }) { Text("Releases on GitHub") }
            TextButton(onClick = { onIntent(SettingsIntent.OpenNotice) }) { Text("Personal-use notice") }
        }
    }
}
