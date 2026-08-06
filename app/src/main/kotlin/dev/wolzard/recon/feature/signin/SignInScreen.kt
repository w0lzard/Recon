package dev.wolzard.recon.feature.signin

import dev.wolzard.recon.core.copy.SetupCopy
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import dev.wolzard.recon.designsystem.status
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import dev.wolzard.recon.designsystem.StatusIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Google sign-in in a WebView; the session cookies are taken once it lands on gemini.google.com (D22). */
@Composable
fun SignInScreen(state: SignInState, onIntent: (SignInIntent) -> Unit) {
    var paste by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("STEP 2 OF 2 · ACCOUNT", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text("Sign in to Gemini", style = MaterialTheme.typography.titleLarge)
        Text(SetupCopy.SIGN_IN_EXPLAINER, style = MaterialTheme.typography.bodyLarge)
        val warn = MaterialTheme.colorScheme.status.unverified
        Column(
            Modifier.fillMaxWidth().border(1.dp, warn, MaterialTheme.shapes.medium).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(StatusIcons.Info, contentDescription = null, Modifier.size(14.dp), tint = warn)
                Text("ACCOUNT RISK", color = warn, style = MaterialTheme.typography.labelSmall)
            }
            Text(SetupCopy.ACCOUNT_RISK, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.checking) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Checking sign-in…") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (paste) {
            Text(SetupCopy.PASTE_HELP, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pasted, onValueChange = { pasted = it }, label = { Text("Cookie header") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            )
            Button(onClick = { onIntent(SignInIntent.CookiesReceived(pasted)) }, Modifier.fillMaxWidth().height(48.dp), enabled = pasted.isNotBlank() && !state.checking, shape = MaterialTheme.shapes.small) { Text("Use these cookies") }
            TextButton(onClick = { paste = false }) { Text("Sign in here instead") }
        } else {
            SignInWebView(Modifier.fillMaxWidth().height(560.dp).clip(MaterialTheme.shapes.medium).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium), { onIntent(SignInIntent.CookiesReceived(it)) })
            TextButton(onClick = { paste = true }) { Text("Can't sign in here? Paste cookies") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Google's sign-in page needs JavaScript
@Composable
private fun SignInWebView(modifier: Modifier, onCookies: (String) -> Unit) {
    AndroidView(modifier = modifier, factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!url.startsWith("https://gemini.google.com/")) return
                    CookieManager.getInstance().getCookie("https://gemini.google.com")
                        ?.takeIf { it.contains("__Secure-1PSID=") }?.let(onCookies)
                }
            }
            loadUrl(SetupCopy.SIGN_IN_URL)
        }
    })
}
