package dev.wolzard.recon.feature.signin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.wolzard.recon.core.copy.SetupCopy
import dev.wolzard.recon.designsystem.StatusIcons
import dev.wolzard.recon.designsystem.status

/** The three steps of sign-in. Kept as one screen so the route, ViewModel and D25 return paths stay as they are. */
internal enum class SignInStep { INTRO, WEB, PASTE }

/**
 * Sign-in (D22, D25; layout D27): an intro with the risk row and two ways in, Google's page full-screen in a WebView,
 * or the paste-cookies fallback. Back from either way in returns to the intro.
 */
@Composable
fun SignInScreen(state: SignInState, signInUrl: String = SetupCopy.SIGN_IN_URL, onIntent: (SignInIntent) -> Unit) {
    var step by rememberSaveable { mutableStateOf(SignInStep.INTRO) }
    BackHandler(enabled = step != SignInStep.INTRO) { step = SignInStep.INTRO }
    when (step) {
        SignInStep.INTRO -> Intro(state, onWeb = { step = SignInStep.WEB }, onPaste = { step = SignInStep.PASTE })
        SignInStep.WEB -> WebStep(state, signInUrl, onIntent, onBack = { step = SignInStep.INTRO }, onPaste = { step = SignInStep.PASTE })
        SignInStep.PASTE -> PasteStep(state, onIntent, onBack = { step = SignInStep.INTRO })
    }
}

@Composable
private fun Intro(state: SignInState, onWeb: () -> Unit, onPaste: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEP 2 OF 2 · ACCOUNT", color = muted, style = MaterialTheme.typography.labelSmall)
        Text("Sign in to Google", style = MaterialTheme.typography.titleLarge)
        Text(SetupCopy.SIGN_IN_INTRO, style = MaterialTheme.typography.bodyLarge)
        RiskRow()
        Status(state)
        Button(onClick = onWeb, Modifier.fillMaxWidth().height(48.dp), enabled = !state.checking, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, Modifier.size(20.dp))
            Text("Sign in with Google", Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onPaste, Modifier.fillMaxWidth().height(48.dp), enabled = !state.checking, shape = MaterialTheme.shapes.small) {
            Text("Paste cookies instead")
        }
        Text(SetupCopy.WEBVIEW_BLOCKED_HELP, color = muted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Fine print: the D25 storage sentence and the full risk wording, unchanged.
        Text(SetupCopy.SIGN_IN_STORED, color = muted, style = MaterialTheme.typography.bodySmall)
        Text(SetupCopy.ACCOUNT_RISK, Modifier.testTag("account_risk"), color = muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** One compact row: warning glyph and the short risk line, in the Unverified ink (D24 status colours). */
@Composable
private fun RiskRow() {
    val warn = MaterialTheme.colorScheme.status.unverified
    Row(
        Modifier.fillMaxWidth().border(1.dp, warn, MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 10.dp).testTag("risk_row"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(StatusIcons.Error, contentDescription = "Account risk", Modifier.size(18.dp), tint = warn)
        Text(SetupCopy.RISK_SHORT, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/** D25 inline states: the check in progress, or why it failed. */
@Composable
private fun Status(state: SignInState, modifier: Modifier = Modifier) {
    if (state.checking) Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Checking sign-in…", style = MaterialTheme.typography.bodyMedium)
    }
    state.error?.let { Text(it, modifier.testTag("sign_in_error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlimBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { action?.invoke() },
        expandedHeight = 48.dp,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
    )
}

/**
 * Google's page fills everything under the bar. imePadding() shrinks the WebView above the keyboard, so the page
 * reflows and its Next button stays visible (SignInKeyboardTest).
 */
@Composable
private fun WebStep(state: SignInState, url: String, onIntent: (SignInIntent) -> Unit, onBack: () -> Unit, onPaste: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        SlimBar("Sign in to Google", onBack) { TextButton(onClick = onPaste) { Text("Paste cookies") } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.checking || state.error != null) Status(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SignInWebView(Modifier.fillMaxWidth().weight(1f).testTag("sign_in_webview"), url) { onIntent(SignInIntent.CookiesReceived(it)) }
    }
}

@Composable
private fun PasteStep(state: SignInState, onIntent: (SignInIntent) -> Unit, onBack: () -> Unit) {
    var pasted by remember { mutableStateOf("") } // not saveable: the cookie never goes into the saved-state Bundle
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().imePadding()) {
        SlimBar("Paste cookies", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(SetupCopy.PASTE_HELP, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pasted, onValueChange = { pasted = it }, label = { Text("Cookie header") }, isError = state.error != null,
                placeholder = { Text("__Secure-1PSID=…; __Secure-1PSIDTS=…") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("cookie_field"), shape = MaterialTheme.shapes.small,
            )
            Status(state)
            Button(
                onClick = { onIntent(SignInIntent.CookiesReceived(pasted)); clearPastedClip(ctx, pasted); pasted = "" },
                Modifier.fillMaxWidth().height(48.dp), enabled = pasted.isNotBlank() && !state.checking, shape = MaterialTheme.shapes.small,
            ) { Text("Use these cookies") }
        }
    }
}

@Composable
private fun SignInWebView(modifier: Modifier, url: String, onCookies: (String) -> Unit) {
    AndroidView(modifier = modifier, factory = { ctx ->
        WebView(ctx).apply {
            SignInWeb.lockDown(this)
            webViewClient = object : SignInWeb.Client(ctx) {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!url.startsWith("https://gemini.google.com/")) return
                    CookieManager.getInstance().getCookie("https://gemini.google.com")
                        ?.takeIf { it.contains("__Secure-1PSID=") }?.let(onCookies)
                }
            }
            loadUrl(url)
        }
    })
}

/** D25: once used, the pasted cookies don't stay on the clipboard for other apps (only if the clip is still ours). */
private fun clearPastedClip(ctx: Context, pasted: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    val clip = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull() ?: return
    if (clip.trim() != pasted.trim()) return
    if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
}
