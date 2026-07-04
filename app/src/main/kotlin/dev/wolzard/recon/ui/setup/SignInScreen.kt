package dev.wolzard.recon.ui.setup

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import dev.wolzard.recon.ui.theme.status
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Google sign-in in a WebView; the session cookies are taken once it lands on gemini.google.com (D22). */
@Composable
fun SignInScreen(vm: SignInViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var paste by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.done) { if (state.done) onDone() }
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
            Text("ACCOUNT RISK", color = warn, style = MaterialTheme.typography.labelSmall)
            Text(SetupCopy.ACCOUNT_RISK, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.checking) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Checking sign-in…") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (paste) {
