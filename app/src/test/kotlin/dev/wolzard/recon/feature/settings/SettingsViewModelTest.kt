package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.feature.signin.SignInViewModelTest.FakeSecrets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    private val secrets = FakeSecrets()
    private val vm = SettingsViewModel(secrets)

    @Test fun startsFromWhatIsStored() {
        secrets.put(Secret.GEMINI_COOKIES, "__Secure-1PSID=x")
        val state = SettingsViewModel(secrets).state.value
        assertTrue(state.signedIn)
        assertFalse(state.hasGitHubToken)
    }

    @Test fun savingATokenTrimsItAndUpdatesState() {
        vm.onIntent(SettingsIntent.SaveGitHubToken("  ghp_x  "))
        assertEquals("ghp_x", secrets.get(Secret.GITHUB_TOKEN))
        assertTrue(vm.state.value.hasGitHubToken)
        vm.onIntent(SettingsIntent.SaveGitHubToken(null))
        assertNull(secrets.get(Secret.GITHUB_TOKEN))
        assertFalse(vm.state.value.hasGitHubToken)
    }

    @Test fun signOutForgetsCookiesAndAsksTheRouteToClearTheWebView() {
        secrets.put(Secret.GEMINI_COOKIES, "__Secure-1PSID=x")
        val signedIn = SettingsViewModel(secrets)
        signedIn.onIntent(SettingsIntent.SignOut)
        assertNull(secrets.get(Secret.GEMINI_COOKIES))
        assertFalse(signedIn.state.value.signedIn)
        assertEquals(SettingsEffect.ClearWebViewCookies, runBlocking { signedIn.effects.first() })
    }

    @Test fun navigationIntentsBecomeEffects() = runBlocking {
        vm.onIntent(SettingsIntent.SignIn)
        vm.onIntent(SettingsIntent.OpenNotice)
        assertEquals(SettingsEffect.NavigateToSignIn, vm.effects.first())
        assertEquals(SettingsEffect.NavigateToNotice, vm.effects.first())
    }
}
