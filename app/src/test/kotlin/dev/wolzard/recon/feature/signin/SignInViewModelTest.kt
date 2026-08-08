package dev.wolzard.recon.feature.signin

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import kotlinx.coroutines.CompletableDeferred
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.core.copy.SetupCopy
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiWebClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    class FakeSecrets : SecretStore {
        val map = mutableMapOf<Secret, String>()
        override fun get(key: Secret) = map[key]
        override fun put(key: Secret, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun acceptedCookiesAreSavedWithoutCookiePrefix() {
        val secrets = FakeSecrets()
        val vm = SignInViewModel(secrets) { it }
        vm.onIntent(SignInIntent.CookiesReceived("Cookie: NID=1; __Secure-1PSID=abc; __Secure-1PSIDTS=t "))
        assertEquals(SignInEffect.SignedIn, runBlocking { vm.effects.first() })
        assertEquals("NID=1; __Secure-1PSID=abc; __Secure-1PSIDTS=t", secrets.map[Secret.GEMINI_COOKIES])
        assertTrue(GeminiSession.signedIn(secrets))
    }

    @Test fun rejectedCookiesAreForgotten() {
        val secrets = FakeSecrets()
        val vm = SignInViewModel(secrets) { throw AnalyzerException(FailureReason.API_KEY_INVALID) }
        vm.onIntent(SignInIntent.CookiesReceived("__Secure-1PSID=abc"))
        assertEquals(SetupCopy.signInFailed(FailureReason.API_KEY_INVALID), vm.state.value.error)
        assertNull(secrets.map[Secret.GEMINI_COOKIES])
    }

    @Test fun pasteWithoutSessionCookieIsRefusedWithoutACheck() {
        var checked = false
        val vm = SignInViewModel(FakeSecrets()) { checked = true; it }
        vm.onIntent(SignInIntent.CookiesReceived("NID=1; SID=2"))
        assertEquals(SetupCopy.NO_SESSION_COOKIE, vm.state.value.error)
        assertEquals(false, checked)
    }

    @Test fun eachFailedCheckHasItsOwnMessage() {
        fun errorFor(e: AnalyzerException): String? {
            val vm = SignInViewModel(FakeSecrets()) { throw e }
            vm.onIntent(SignInIntent.CookiesReceived("__Secure-1PSID=abc"))
            return vm.state.value.error
        }
        val rejected = errorFor(AnalyzerException(FailureReason.API_KEY_INVALID))
        val network = errorFor(AnalyzerException(FailureReason.NETWORK))
        val challenge = errorFor(AnalyzerException(FailureReason.API_KEY_INVALID, message = GeminiWebClient.GOOGLE_CHALLENGE))
        assertEquals(SetupCopy.GOOGLE_CHALLENGE, challenge)
        assertEquals(SetupCopy.signInFailed(FailureReason.NETWORK), network)
        assertEquals(3, setOf(rejected, network, challenge).size)
    }

    @Test fun malformedPasteIsRefusedBeforeSavingOrChecking() {
        var checked = false
        val secrets = FakeSecrets()
        val vm = SignInViewModel(secrets) { checked = true; it }
        vm.onIntent(SignInIntent.CookiesReceived("__Secure-1PSID=two words"))
        assertEquals(SetupCopy.pasteMalformed("__Secure-1PSID"), vm.state.value.error)
        vm.onIntent(SignInIntent.CookiesReceived(" "))
        assertEquals(SetupCopy.PASTE_EMPTY, vm.state.value.error)
        assertEquals(false, checked)
        assertNull(secrets.map[Secret.GEMINI_COOKIES])
    }

    @Test fun killBeforeTheCheckFinishesLeavesTheAppSignedOut() {
        val secrets = FakeSecrets()
        val never = CompletableDeferred<String>()
        val vm = SignInViewModel(secrets) { never.await() }
        vm.onIntent(SignInIntent.CookiesReceived("__Secure-1PSID=abc"))
        assertTrue(vm.state.value.checking)
        assertNull(secrets.map[Secret.GEMINI_COOKIES]) // the process dies here: nothing was saved
        assertFalse(GeminiSession.atLaunch(secrets))
    }

    @Test fun launchDropsCookiesThatWereNeverMarkedVerified() {
        val secrets = FakeSecrets().apply { put(Secret.GEMINI_COOKIES, "__Secure-1PSID=abc") } // killed between the two writes, or a pre-D25 save
        assertFalse(GeminiSession.atLaunch(secrets))
        assertTrue(secrets.map.isEmpty())
        GeminiSession.save(secrets, "__Secure-1PSID=abc")
        assertTrue(GeminiSession.atLaunch(secrets))
        assertEquals("__Secure-1PSID=abc", secrets.map[Secret.GEMINI_COOKIES])
    }

    @Test fun cookiesRotatedDuringTheCheckAreTheOnesSaved() {
        val secrets = FakeSecrets()
        val vm = SignInViewModel(secrets) { "$it; __Secure-1PSIDTS=new" }
        vm.onIntent(SignInIntent.CookiesReceived("__Secure-1PSID=abc"))
        assertEquals("__Secure-1PSID=abc; __Secure-1PSIDTS=new", secrets.map[Secret.GEMINI_COOKIES])
    }
}
