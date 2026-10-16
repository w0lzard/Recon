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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import dev.wolzard.recon.designsystem.Symbols
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.wolzard.recon.core.copy.SetupCopy
import dev.wolzard.recon.designsystem.status

/** The two steps of sign-in. Kept as one screen so the route, ViewModel and D25 return paths stay as they are. */
internal enum class SignInStep { INTRO, WEB }

/**
 * Sign-in (D22, D25; flow D27, look design 1c): an intro with the risk card, "Continue with Google" opening Google's page
 * full-screen in a WebView, and the paste-cookies fallback inline under "Advanced". Back from the page returns to the intro.
 * [onBack] null = no back arrow (first run, nothing to go back to).
 */
@Composable
fun SignInScreen(state: SignInState, signInUrl: String = SetupCopy.SIGN_IN_URL, onBack: (() -> Unit)? = null, onIntent: (SignInIntent) -> Unit) {
    var step by rememberSaveable { mutableStateOf(SignInStep.INTRO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step != SignInStep.INTRO) { step = SignInStep.INTRO }
    when (step) {
        SignInStep.INTRO -> Intro(state, advanced, { advanced = it }, onBack, onIntent, onWeb = { step = SignInStep.WEB })
        SignInStep.WEB -> WebStep(state, signInUrl, onIntent, onBack = { step = SignInStep.INTRO }, onPaste = { advanced = true; step = SignInStep.INTRO })
    }
}

@Composable
private fun Intro(state: SignInState, advanced: Boolean, onAdvanced: (Boolean) -> Unit, onBack: (() -> Unit)?, onIntent: (SignInIntent) -> Unit, onWeb: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            onBack?.let { IconButton(onClick = it) { Icon(Symbols.ArrowBack, "Back") } }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
            Text("STEP 2 OF 2 · ACCOUNT", color = muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(28.dp))
            Text("Sign in to Google", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp))
            Spacer(Modifier.height(20.dp))
            Text(SetupCopy.SIGN_IN_INTRO, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
            Spacer(Modifier.height(20.dp))
            RiskCard()
            Status(state, Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(20.dp))
            Button(onClick = onWeb, Modifier.fillMaxWidth().height(48.dp), enabled = !state.checking, shape = MaterialTheme.shapes.small) {
                Icon(Symbols.AccountCircle, contentDescription = null, Modifier.size(20.dp))
                Text("Continue with Google", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onAdvanced(!advanced) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Symbols.ExpandMore, contentDescription = null, Modifier.size(20.dp).rotate(if (advanced) 180f else 0f), tint = muted)
                Text("Advanced: paste cookie header", color = muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
            }
            AnimatedVisibility(advanced, enter = fadeIn(), exit = fadeOut()) { Paste(state, onIntent) }
        }
    }
}

/** Design 1c: hairline card in the Unverified ink, warning glyph, ACCOUNT RISK label and the short risk line. */
@Composable
private fun RiskCard() {
    val warn = MaterialTheme.colorScheme.status.unverified
    Row(
        Modifier.fillMaxWidth().border(1.dp, warn, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 14.dp).testTag("risk_row"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Symbols.Warning, contentDescription = null, Modifier.padding(top = 2.dp).size(20.dp), tint = warn)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ACCOUNT RISK", color = warn, style = MaterialTheme.typography.labelSmall)
            Text(SetupCopy.RISK_SHORT, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The paste-cookies fallback, inline under "Advanced" (was its own step in D27). */
@Composable
private fun Paste(state: SignInState, onIntent: (SignInIntent) -> Unit) {
    var pasted by remember { mutableStateOf("") } // not saveable: the cookie never goes into the saved-state Bundle
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(SetupCopy.PASTE_HELP, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pasted, onValueChange = { pasted = it }, label = { Text("Cookie header") }, isError = state.error != null,
            placeholder = { Text("__Secure-1PSID=…; __Secure-1PSIDTS=…") }, minLines = 3,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("cookie_field"), shape = MaterialTheme.shapes.small,
        )
        OutlinedButton(
            onClick = { onIntent(SignInIntent.CookiesReceived(pasted)); clearPastedClip(ctx, pasted); pasted = "" },
            Modifier.fillMaxWidth().height(48.dp), enabled = pasted.isNotBlank() && !state.checking, shape = MaterialTheme.shapes.small,
        ) { Text("Use these cookies") }
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
        navigationIcon = { IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, "Back") } },
        actions = { action?.invoke() },
        expandedHeight = 48.dp,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
    )
}

/**
 * Google's page fills everything under the bar. imePadding() shrinks the WebView to the space above the keyboard
 * (the Scaffold already padded for the navigation bar, so that inset is consumed first, or it would count twice).
 * Google's Next button sits in the page flow under the field, not pinned, so when the keyboard opens the focused field
 * is scrolled to the top of the WebView, which brings Next into view (SignInKeyboardTest, and the live page on recon30).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebStep(state: SignInState, url: String, onIntent: (SignInIntent) -> Unit, onBack: () -> Unit, onPaste: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, web) {
        if (!imeVisible) return@LaunchedEffect
        delay(350) // let the WebView finish shrinking with the keyboard animation
        web?.evaluateJavascript(SCROLL_FOCUSED_TO_TOP, null)
    }
    Column(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars).imePadding()) {
        SlimBar("Sign in to Google", onBack) {
            // Lime text only on the dark bar; on the light bar it would be ~1.5:1, so it takes the body ink there.
            val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
            TextButton(onClick = onPaste, colors = ButtonDefaults.textButtonColors(contentColor = if (light) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)) {
                Text("Paste cookies")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.checking || state.error != null) Status(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SignInWebView(Modifier.fillMaxWidth().weight(1f).testTag("sign_in_webview"), url, onCreated = { web = it }) { onIntent(SignInIntent.CookiesReceived(it)) }
    }
}

// A fixed scroll, nothing read or written: the focused field moves to the top so what follows it (Next) is visible.
private const val SCROLL_FOCUSED_TO_TOP =
    "(function(){var e=document.activeElement;if(e&&e!==document.body){var r=e.getBoundingClientRect();window.scrollBy(0,r.top-16);}})()"

@Composable
private fun SignInWebView(modifier: Modifier, url: String, onCreated: (WebView) -> Unit = {}, onCookies: (String) -> Unit) {
    AndroidView(modifier = modifier, factory = { ctx ->
        WebView(ctx).apply {
            onCreated(this)
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
