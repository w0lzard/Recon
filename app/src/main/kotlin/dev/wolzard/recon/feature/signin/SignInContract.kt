package dev.wolzard.recon.feature.signin

data class SignInState(val checking: Boolean = false, val error: String? = null)

sealed interface SignInIntent {
    /** A Cookie header, from the sign-in WebView or pasted; checked by CookieHeader.parse before anything is saved. */
    data class CookiesReceived(val raw: String) : SignInIntent
}

sealed interface SignInEffect {
    data object SignedIn : SignInEffect
}
