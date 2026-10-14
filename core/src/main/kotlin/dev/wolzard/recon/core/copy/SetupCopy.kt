package dev.wolzard.recon.core.copy

import dev.wolzard.recon.core.model.FailureReason

/** User-facing text for sign-in and Settings (one place, testable). */
object SetupCopy {
    const val DATA_NOTE = "Google may keep what you send to Gemini for up to 72 hours, even in temporary chats."

    // --- Gemini web sign-in (D22)
    const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fapp"
    // Design Recon UI 1c: the intro sentence (how the sign-in is stored included) and the account-risk card.
    const val SIGN_IN_INTRO = "Recon runs each analysis with Gemini on your Google account. You sign in on Google's own page; " +
        "Recon keeps the session on this phone only."
    const val RISK_SHORT = "Google may flag automated use and can suspend or ban the account. Sign in with a secondary account, not your main one."
    const val ACCOUNT_RISK = "Gemini web is not an official API. Google doesn't support apps using it and may limit, " +
        "suspend or ban the account. That risk is yours, not Recon's. Use a secondary Google account, not your main one."
    const val PASTE_HELP = "If Google won't sign you in here, open gemini.google.com in a desktop browser, copy the Cookie " +
        "header of any request to gemini.google.com from the developer tools, and paste it below."
    const val NO_SESSION_COOKIE = "That doesn't contain the __Secure-1PSID cookie. Copy the whole Cookie header."
    const val PASTE_EMPTY = "Paste the Cookie header first."
    fun pasteMalformed(name: String?): String = "That isn't a Cookie header" + (name?.let { " (stopped at \"$it\")" } ?: "") +
        ". It should look like NAME=value; NAME2=value2, copied as one piece."

    const val GOOGLE_CHALLENGE = "Google wants you to confirm something first (a consent or \"verify it's you\" page). " +
        "Sign in here and finish that page, or do it in a browser and paste the new cookies."

    fun signInFailed(reason: FailureReason): String = when (reason) {
        FailureReason.NETWORK -> "Couldn't reach Google. Check your connection and try again."
        else -> "Google didn't accept these cookies; they may be from a signed-out or expired session. Sign in again."
    }
}
