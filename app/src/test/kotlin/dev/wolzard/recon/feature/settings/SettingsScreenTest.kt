package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.gemini.GeminiSession
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.feature.signin.SignInViewModelTest.FakeSecrets
import androidx.compose.ui.test.performClick
import dev.wolzard.recon.data.update.UpdateInfo
import org.junit.Assert.assertEquals
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
        compose.onNodeWithText("Not signed in").assertExists()
        compose.onNodeWithText("Sign in").assertExists()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test fun signedInOffersSignOut() {
        show(FakeSecrets().also { GeminiSession.save(it, "__Secure-1PSID=x") })
        compose.onNodeWithText("Signed in", substring = true).assertExists()
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test fun updateBannerDownloadsAndPutsOff() {
        val intents = mutableListOf<SettingsIntent>()
        val info = UpdateInfo("1.4.0", "https://github.com/w0lzard/recon/releases/download/v1.4.0/a.apk", "https://github.com/w0lzard/recon/releases/tag/v1.4.0")
        compose.setContent { SettingsScreen(SettingsState(available = info)) { intents += it } }
        compose.onNodeWithText("Update available").assertExists()
        compose.onNodeWithText("v1.4.0").assertExists()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("Later").performClick()
        assertEquals(listOf(SettingsIntent.OpenUpdate(info.apkUrl), SettingsIntent.DismissUpdate("1.4.0")), intents)
    }

    @Test fun signedInShowsTheAddressOrSaysGoogleAccount() {
        compose.setContent { SettingsScreen(SettingsState(signedIn = true, email = "recon.side@gmail.com")) {} }
        compose.onNodeWithText("recon.side@gmail.com").assertExists()
        compose.onNodeWithText("Signed in · Google").assertExists()
    }
}
