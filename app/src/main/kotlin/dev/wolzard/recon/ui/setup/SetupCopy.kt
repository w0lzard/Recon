package dev.wolzard.recon.ui.setup

import dev.wolzard.recon.core.model.FailureReason

/** User-facing text for sign-in and Settings (one place, testable). */
object SetupCopy {
    const val DATA_NOTE = "Google may keep what you send to Gemini for up to 72 hours, even in temporary chats."

    // --- Gemini web sign-in (D22)
    const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fapp"
    const val SIGN_IN_EXPLAINER = "Recon uses Gemini with your Google account, the same way gemini.google.com does. " +
        "Your sign-in is stored encrypted on this phone and only sent to Google."
    const val ACCOUNT_RISK = "Recon talks to Gemini through its website, not an official API. Google doesn't support this " +
        "and may limit or suspend accounts that use it. Use an account you can afford to lose access to."
    const val PASTE_HELP = "If Google won't sign you in here, open gemini.google.com in a desktop browser, copy the Cookie " +
        "header of any request to gemini.google.com from the developer tools, and paste it below."
    const val NO_SESSION_COOKIE = "That doesn't contain the __Secure-1PSID cookie. Copy the whole Cookie header."

    fun signInFailed(reason: FailureReason): String = when (reason) {
        FailureReason.NETWORK -> "Couldn't reach Google. Check your connection and try again."
        else -> "Gemini didn't accept this sign-in. Sign in again."
    }
}
