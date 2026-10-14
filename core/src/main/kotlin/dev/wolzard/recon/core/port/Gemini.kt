package dev.wolzard.recon.core.port

interface SecretStore {
    fun get(key: Secret): String?
    fun put(key: Secret, value: String?)   // null deletes
}

/**
 * Stored by name. GEMINI_COOKIES = the Cookie header of the user's Gemini web session (D22);
 * GEMINI_VERIFIED = written after those cookies passed the sign-in check, cookies without it are dropped at launch (D25).
 */
enum class Secret { GEMINI_COOKIES, GITHUB_TOKEN, GEMINI_VERIFIED, GEMINI_EMAIL }

enum class GroundingCapability { AVAILABLE, UNAVAILABLE, UNKNOWN }   // D20
