package dev.wolzard.recon.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.core.copy.SetupCopy
import dev.wolzard.recon.data.update.UpdateResult
import dev.wolzard.recon.designsystem.Symbols
import dev.wolzard.recon.designsystem.UpdateBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Mono section label over a hairline, as in design 1l (ACCOUNT, UPDATES, ABOUT). Rows below carry their own hairlines. */
@Composable
private fun Section(label: String) {
    Text(label, Modifier.padding(top = 24.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One ListItem-style row: title, optional mono detail, optional trailing content, hairline under it. */
@Composable
private fun SettingRow(
    title: String, detail: String? = null, onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke(this)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** "SHA-256 4F:2A:…" in four lines of eight pairs, as in the design. */
internal fun certLines(sha: String): String = "SHA-256 " + sha.split(":").chunked(8).joinToString(":\n") { it.joinToString(":") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = { onIntent(SettingsIntent.Back) }) { Icon(Symbols.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            state.available?.let { u ->
                UpdateBanner(u, onOpen = { onIntent(SettingsIntent.OpenUpdate(it)) }, onDismiss = { onIntent(SettingsIntent.DismissUpdate(u.version)) }, Modifier.padding(top = 8.dp))
            }

            Section("ACCOUNT")
            if (state.signedIn) {
                val email = state.email
                SettingRow(
                    email ?: "Google account", "Signed in · Google",
                    leading = {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                            Text((email?.first() ?: 'G').uppercaseChar().toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    trailing = {
                        TextButton(onClick = { onIntent(SettingsIntent.SignOut) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Sign out") }
                    },
                )
            } else SettingRow(
                "Not signed in", "Needed for Gemini",
                leading = {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Symbols.AccountCircle, contentDescription = null, Modifier.size(22.dp), tint = muted)
                    }
                },
                trailing = { TextButton(onClick = { onIntent(SettingsIntent.SignIn) }) { Text("Sign in") } },
            )

            Section("UPDATES")
            val last = state.lastChecked?.let { "Last checked ${hhmm.format(Instant.ofEpochMilli(it))}" }
            SettingRow(
                "Check for updates",
                when {
                    state.checkingUpdate -> "Checking…"
                    state.update == UpdateResult.UpToDate -> listOfNotNull("Up to date", last).joinToString(" · ")
                    state.update == UpdateResult.Failed -> "Couldn't check. Try again later."
                    else -> last ?: "Never checked"
                },
                onClick = { if (!state.checkingUpdate) onIntent(SettingsIntent.CheckForUpdates) },
                leading = { Icon(Symbols.Sync, contentDescription = null, tint = muted) },
            )

            Section("ABOUT")
            SettingRow("Version", trailing = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = muted, style = MaterialTheme.typography.bodySmall) })
            state.certSha256?.let { sha ->
                SettingRow("Signing certificate", certLines(sha), trailing = {
                    IconButton(onClick = { copy(ctx, sha) }, Modifier.align(Alignment.Top)) { Icon(Symbols.ContentCopy, "Copy fingerprint", tint = muted) }
                })
            }
            SettingRow("Source code", "github.com/${BuildConfig.RELEASES_REPO}", onClick = { onIntent(SettingsIntent.OpenSource) }, trailing = {
                Icon(Symbols.OpenInNew, contentDescription = "Opens in browser", Modifier.size(20.dp), tint = muted)
            })
            SettingRow("Show first-run notice", onClick = { onIntent(SettingsIntent.OpenNotice) })

            // Not in design 1l; kept in the same row style so nothing the app did before is lost.
            Section("GITHUB")
            GitHubToken(state.hasGitHubToken, onIntent)

            Section("PRIVACY")
            Text(
                "Videos, or frames and audio, go to Gemini with your Google account, in temporary chats. Names of things found go to " +
                    "GitHub and Wikipedia. Your history and sign-in stay on this phone. ${SetupCopy.DATA_NOTE}\n\n${SetupCopy.ACCOUNT_RISK}",
                Modifier.padding(vertical = 12.dp), color = muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GitHubToken(saved: Boolean, onIntent: (SettingsIntent) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Optional. Raises GitHub's lookup limit; a token with no scopes is enough.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = token, onValueChange = { token = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.small,
            label = { Text(if (saved) "Token saved; enter a new one to replace" else "Token") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(token)); token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
            if (saved) OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(SettingsIntent.SaveGitHubToken(null)) }) { Text("Remove token") }
        }
    }
}

private fun copy(ctx: Context, sha: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Signing certificate SHA-256", sha))
}
