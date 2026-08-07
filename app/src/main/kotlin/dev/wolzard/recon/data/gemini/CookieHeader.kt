package dev.wolzard.recon.data.gemini

/** The Google Cookie header kept in Secret.GEMINI_COOKIES (D22, D25). */
object CookieHeader {
    // Google's session cookie names (SID, __Secure-1PSID, SAPISID, NID, AEC, SOCS...) and any __Secure-/__Host- cookie.
    private val SECRET_PAIR = Regex("""((?:__Secure-|__Host-)[\w-]+|[\w-]*SID[\w-]*|NID|AEC|SOCS)=[^;\s,"]+""")
    private val HEADER_LINE = Regex("""(?i)((?:set-)?cookie:)[^\r\n]*""")

    /** Text safe for a log, crash report or exception message: Cookie headers and session cookie values become "<redacted>". */
    fun redact(text: String): String = text
        .replace(HEADER_LINE) { "${it.groupValues[1]} <redacted>" }
        .replace(SECRET_PAIR) { "${it.groupValues[1]}=<redacted>" }
}
