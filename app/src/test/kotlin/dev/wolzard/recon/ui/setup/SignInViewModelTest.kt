package dev.wolzard.recon.ui.setup

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val vm = SignInViewModel(secrets) {}
        vm.onCookies("Cookie: NID=1; __Secure-1PSID=abc; __Secure-1PSIDTS=t ")
        assertTrue(vm.state.value.done)
        assertEquals("NID=1; __Secure-1PSID=abc; __Secure-1PSIDTS=t", secrets.map[Secret.GEMINI_COOKIES])
    }

    @Test fun rejectedCookiesAreForgotten() {
        val secrets = FakeSecrets()
        val vm = SignInViewModel(secrets) { throw AnalyzerException(FailureReason.API_KEY_INVALID) }
        vm.onCookies("__Secure-1PSID=abc")
        assertEquals(SetupCopy.signInFailed(FailureReason.API_KEY_INVALID), vm.state.value.error)
        assertNull(secrets.map[Secret.GEMINI_COOKIES])
    }

    @Test fun pasteWithoutSessionCookieIsRefusedWithoutACheck() {
        var checked = false
        val vm = SignInViewModel(FakeSecrets()) { checked = true }
        vm.onCookies("NID=1; SID=2")
        assertEquals(SetupCopy.NO_SESSION_COOKIE, vm.state.value.error)
        assertEquals(false, checked)
    }
}
