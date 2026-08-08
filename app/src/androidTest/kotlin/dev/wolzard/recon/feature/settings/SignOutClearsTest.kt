package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.gemini.GeminiSession
import android.content.Context
import android.webkit.CookieManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.data.secret.KeystoreSecretStore
import dev.wolzard.recon.designsystem.ReconTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D25: Settings → Sign out on a device forgets the saved cookies and empties the WebView's own cookie jar. */
@RunWith(AndroidJUnit4::class)
class SignOutClearsTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun signOutClearsSecretStoreAndWebViewCookies() {
        val secrets = KeystoreSecretStore(ctx)
        GeminiSession.save(secrets, "__Secure-1PSID=test-not-real")
        val cookies = CookieManager.getInstance()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookies.setCookie("https://gemini.google.com", "__Secure-1PSID=test-not-real; Secure")
            cookies.setCookie("https://accounts.google.com", "SID=test-not-real; Secure")
        }
        compose.waitUntil(5_000) { cookies.getCookie("https://gemini.google.com")?.contains("__Secure-1PSID") == true }

        val vm = SettingsViewModel(secrets)
        compose.setContent { ReconTheme { SettingsRoute(vm, onNotice = {}, onSignIn = {}) } }
        compose.onNodeWithText("Sign out").performScrollTo().performClick()

        compose.waitUntil(5_000) { cookies.getCookie("https://gemini.google.com") == null && cookies.getCookie("https://accounts.google.com") == null }
        assertNull(secrets.get(Secret.GEMINI_COOKIES))
        assertNull(secrets.get(Secret.GEMINI_VERIFIED))
        assertTrue(compose.onNodeWithText("Not signed in.").fetchSemanticsNode().layoutInfo.isPlaced)
    }
}
