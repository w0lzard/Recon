package dev.wolzard.recon.data.notice

import android.content.SharedPreferences

/** Raise when the notice text changes in a way users must re-accept. */
const val NOTICE_VERSION = 4 // 2: Gemini web sign-in replaced the API key (D22); 3: account-ban risk spelled out (D25); 4: link preview call (D26)

/** T16: the accepted notice version, in prefs. [version] is a parameter only so tests can bump it. */
class Notice(private val prefs: SharedPreferences, private val version: Int = NOTICE_VERSION) {
    val needed: Boolean get() = prefs.getInt(ACCEPTED, 0) < version
    fun accept() { prefs.edit().putInt(ACCEPTED, version).apply() }

    companion object {
        private const val ACCEPTED = "acceptedVersion"
        const val PREFS = "notice"
        val TEXT = listOf(
            "Recon is a personal tool. To analyse a video it downloads a temporary copy, then deletes it when the analysis " +
                "finishes. When you share a link, it first asks the platform for the video's title, length and thumbnail to show " +
                "them. Instagram, TikTok, X and other platforms may forbid downloading in their terms of service. You are " +
                "responsible for what you share with Recon. Only share content you're allowed to process.",
            "Saving videos is strictly for your own personal use.",
            "Recon uses your own Google account through Gemini web (gemini.google.com), not an official API. Videos and " +
                "screenshots are sent there with your account. Google's terms apply.",
            "Google doesn't support apps using Gemini web and may limit, suspend or ban the account. The account-ban risk is " +
                "yours. Sign in with a secondary Google account, not the one you rely on.",
        )
    }
}
