package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.gemini.GeminiSession
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
        GeminiSession.save(secrets, "__Secure-1PSID=x")
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
        GeminiSession.save(secrets, "__Secure-1PSID=x")
        val signedIn = SettingsViewModel(secrets)
        signedIn.onIntent(SettingsIntent.SignOut)
        assertNull(secrets.get(Secret.GEMINI_COOKIES))
        assertNull(secrets.get(Secret.GEMINI_VERIFIED))
        assertFalse(signedIn.state.value.signedIn)
        assertEquals(SettingsEffect.ClearWebViewCookies, runBlocking { signedIn.effects.first() })
    }

    @Test fun navigationIntentsBecomeEffects() = runBlocking {
        vm.onIntent(SettingsIntent.SignIn)
        vm.onIntent(SettingsIntent.OpenNotice)
        assertEquals(SettingsEffect.NavigateToSignIn, vm.effects.first())
        assertEquals(SettingsEffect.NavigateToNotice, vm.effects.first())
    }

    @Test fun emailShowsOnlyWhileSignedInAndSignOutForgetsIt() {
        secrets.put(Secret.GEMINI_EMAIL, "recon.side@gmail.com")
        assertNull(SettingsViewModel(secrets).state.value.email) // address without a verified sign-in
        GeminiSession.save(secrets, "__Secure-1PSID=x")
        val signedIn = SettingsViewModel(secrets)
        assertEquals("recon.side@gmail.com", signedIn.state.value.email)
        signedIn.onIntent(SettingsIntent.SignOut)
        assertNull(secrets.get(Secret.GEMINI_EMAIL))
        assertNull(signedIn.state.value.email)
    }

    @Test fun certificateIsPassedThroughAndPrintedInFourLines() {
        val sha = fingerprint("cert".toByteArray())
        assertEquals(sha, SettingsViewModel(secrets, certSha256 = sha).state.value.certSha256)
        assertEquals(32, sha.split(":").size)
        val lines = certLines(sha).lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].startsWith("SHA-256 ") && lines[0].endsWith(":"))
        assertEquals(sha, certLines(sha).removePrefix("SHA-256 ").replace("\n", ""))
    }

    @Test fun ytDlpUpdatesAreOffUntilTurnedOn() {
        val saved = mutableListOf<Boolean>()
        val vm = SettingsViewModel(secrets, setYtDlpAutoUpdate = { saved += it })
        assertFalse(vm.state.value.ytDlpAutoUpdate)
        vm.onIntent(SettingsIntent.SetYtDlpAutoUpdate(true))
        assertTrue(vm.state.value.ytDlpAutoUpdate)
        assertEquals(listOf(true), saved)
    }
}
