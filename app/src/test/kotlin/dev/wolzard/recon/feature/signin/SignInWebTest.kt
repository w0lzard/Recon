package dev.wolzard.recon.feature.signin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInWebTest {
    @Test fun googleSignInAndGeminiHostsAreAllowed() {
        listOf(
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fapp",
            "https://gemini.google.com/app",
            "https://consent.google.com/ml?continue=x",
            "https://accounts.youtube.com/accounts/CheckConnection",
            "https://accounts.google.co.in/accounts/SetSID",
            "https://accounts.google.com.br/accounts/SetSID",
            "https://accounts.google.de/accounts/SetSID",
            "https://ACCOUNTS.GOOGLE.COM/x",
            "https://gemini.google.com:443/app",
        ).forEach { assertTrue(it, SignInWeb.allowed(it)) }
    }

    @Test fun everythingElseIsBlocked() {
        listOf(
            "http://accounts.google.com/",                    // not https
            "https://evil.com/",
            "https://accounts.google.com.evil.com/",          // suffix trick
            "https://evil.com/?u=https://accounts.google.com/",
            "https://accounts.google.com@evil.com/",          // userinfo trick
            "https://user@accounts.google.com/",
            "https://gemini.google.com:8443/app",
            "https://support.google.com/accounts",
            "https://accounts.google.evil/",                  // not a 2-letter country code
            "file:///data/data/dev.wolzard.recon/shared_prefs/secrets.xml",
            "content://dev.wolzard.recon/x",
            "javascript:alert(1)",
            "intent://scan/#Intent;scheme=zxing;end",
            "",
            "not a url",
        ).forEach { assertFalse(it, SignInWeb.allowed(it)) }
    }

    @Test fun onlyWebLinksGoToTheBrowser() {
        assertTrue(SignInWeb.external("https://support.google.com/accounts"))
        assertFalse(SignInWeb.external("intent://scan/#Intent;end"))
        assertFalse(SignInWeb.external("file:///sdcard/x"))
        assertFalse(SignInWeb.external("javascript:alert(1)"))
    }
}
