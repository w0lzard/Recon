package dev.wolzard.recon.data.notice

import android.content.SharedPreferences

/** Raise when the notice text changes in a way users must re-accept. */
const val NOTICE_VERSION = 2 // 2: Gemini web sign-in replaced the API key (D22)

/** T16: the accepted notice version, in prefs. [version] is a parameter only so tests can bump it. */
class Notice(private val prefs: SharedPreferences, private val version: Int = NOTICE_VERSION) {
    val needed: Boolean get() = prefs.getInt(ACCEPTED, 0) < version
    fun accept() { prefs.edit().putInt(ACCEPTED, version).apply() }

    companion object {
        private const val ACCEPTED = "acceptedVersion"
        const val PREFS = "notice"
        val TEXT = listOf(
            "Recon is a personal tool. To analyse a video it downloads a temporary copy, then deletes it when the analysis " +
                "finishes. Instagram, TikTok, X and other platforms may forbid downloading in their terms of service. You are " +
                "responsible for what you share with Recon. Only share content you're allowed to process.",
            "Videos and screenshots are sent to Gemini with your Google account, through its website rather than an official API. Google's terms apply.",
        )
    }
}
