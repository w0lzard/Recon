package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.gemini.GeminiSession
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.feature.signin.SignInViewModelTest.FakeSecrets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(secrets: FakeSecrets) {
        val state = SettingsViewModel(secrets).state.value
        compose.setContent { SettingsScreen(state) {} }
    }

    @Test fun signedOutOffersSignIn() {
        show(FakeSecrets())
        compose.onNodeWithText("Not signed in.").assertExists()
        compose.onNodeWithText("Sign in").assertExists()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test fun signedInOffersSignOut() {
        show(FakeSecrets().also { GeminiSession.save(it, "__Secure-1PSID=x") })
        compose.onNodeWithText("Signed in", substring = true).assertExists()
        compose.onNodeWithText("Sign out").assertExists()
    }
}
