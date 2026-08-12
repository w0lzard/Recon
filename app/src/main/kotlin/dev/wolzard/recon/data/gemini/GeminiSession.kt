package dev.wolzard.recon.data.gemini

import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore

/** Whether the saved Gemini cookies count as a sign-in (D25): only cookies that passed the check, marked afterwards. */
object GeminiSession {
    fun signedIn(secrets: SecretStore) = secrets.get(Secret.GEMINI_VERIFIED) != null && secrets.get(Secret.GEMINI_COOKIES) != null

    /** After a passing check: cookies first, marker second, so a kill in between leaves unmarked cookies that [atLaunch] drops. */
    fun save(secrets: SecretStore, cookies: String) {
        secrets.put(Secret.GEMINI_COOKIES, cookies)
        secrets.put(Secret.GEMINI_VERIFIED, "1")
    }

    fun clear(secrets: SecretStore) {
        secrets.put(Secret.GEMINI_VERIFIED, null)
        secrets.put(Secret.GEMINI_COOKIES, null)
    }

    /** App launch: cookies without the marker (killed mid-save, or saved by a build before D25) are signed out. */
    fun atLaunch(secrets: SecretStore): Boolean = signedIn(secrets).also { if (!it) clear(secrets) }
}
