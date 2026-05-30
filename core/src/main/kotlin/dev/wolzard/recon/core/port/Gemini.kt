package dev.wolzard.recon.core.port

interface SecretStore {
    fun get(key: Secret): String?
    fun put(key: Secret, value: String?)   // null deletes
}

/** Stored by name. GEMINI_COOKIES = the Cookie header of the user's Gemini web session (D22). */
enum class Secret { GEMINI_COOKIES, GITHUB_TOKEN }

enum class GroundingCapability { AVAILABLE, UNAVAILABLE, UNKNOWN }   // D20
