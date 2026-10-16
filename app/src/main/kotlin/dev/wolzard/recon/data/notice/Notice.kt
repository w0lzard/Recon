package dev.wolzard.recon.data.notice

import android.content.SharedPreferences

/** Raise when the notice text changes in a way users must re-accept. */
const val NOTICE_VERSION = 5 // 2: Gemini web sign-in replaced the API key (D22); 3: account-ban risk spelled out (D25); 4: link preview call (D26); 5: design rows (Recon UI 1b)

/** T16: the accepted notice version, in prefs. [version] is a parameter only so tests can bump it. */
class Notice(private val prefs: SharedPreferences, private val version: Int = NOTICE_VERSION) {
    val needed: Boolean get() = prefs.getInt(ACCEPTED, 0) < version
    fun accept() { prefs.edit().putInt(ACCEPTED, version).apply() }

    companion object {
        private const val ACCEPTED = "acceptedVersion"
        const val PREFS = "notice"
        /** Bold lead and the rest of each notice row, in the design's order; the download/terms row is Recon's own (kept from v4). */
        val TEXT = listOf(
            "Video goes to Google Gemini." to "Frames and audio are sent through your own signed-in Google account.",
            "You're responsible for what you share." to "Recon downloads a temporary copy, deleted after analysis, and asks the " +
                "platform for the title, length and thumbnail. Instagram, TikTok, X and others may forbid downloading in their terms.",
            "Unofficial." to "Not made by Google. Access can stop working or be blocked at any time.",
            "Use a secondary Google account." to "Automated use may put the signed-in account at risk.",
            "Results can be wrong." to "Check the sources before you act on anything.",
            "Not professional advice." to "Health, legal and financial output is information only.",
            "Outside the Play Store." to "Install updates only from the official GitHub releases.",
        )
    }
}
