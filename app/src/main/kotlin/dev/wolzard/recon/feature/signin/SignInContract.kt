package dev.wolzard.recon.feature.signin

data class SignInState(val checking: Boolean = false, val error: String? = null)

sealed interface SignInIntent {
    /** A Cookie header value, from the sign-in WebView or pasted; a leading "Cookie:" is dropped. */
    data class CookiesReceived(val raw: String) : SignInIntent
}

sealed interface SignInEffect {
    data object SignedIn : SignInEffect
}
