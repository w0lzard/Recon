package dev.wolzard.recon.data.gemini

/** The Google Cookie header kept in Secret.GEMINI_COOKIES (D22, D25). */
object CookieHeader {
    const val SESSION = "__Secure-1PSID"

    sealed interface Parsed {
        /** [header] is normalised to "a=b; c=d". */
        data class Ok(val header: String) : Parsed
        data object Empty : Parsed
        /** [name] is the cookie name (never its value) where reading stopped, or null if the part had no name. */
        data class Malformed(val name: String?) : Parsed
        data object NoSession : Parsed
    }

    private val NAME = Regex("""[!#$%&'*+.^_`|~0-9A-Za-z-]+""")
    private val VALUE = Regex("""[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*""") // RFC 6265 cookie-octet

    /**
     * Reads a Cookie header as copied from a browser's developer tools ("Cookie: a=b; c=d", the value alone, or
     * one pair per line). Nothing is saved or sent unless this is [Parsed.Ok] and carries a [SESSION] value.
     */
    fun parse(raw: String): Parsed {
        val text = raw.trim().let { if (it.startsWith("cookie:", ignoreCase = true)) it.substring(7) else it }
        val parts = text.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Parsed.Empty
        val pairs = parts.map { part ->
            val eq = part.indexOf('=')
            val name = part.substring(0, eq.coerceAtLeast(0)).trim()
            if (eq <= 0 || !NAME.matches(name)) return Parsed.Malformed(name.takeIf { NAME.matches(it) })
            val value = part.substring(eq + 1).trim().removeSurrounding("\"")
            if (!VALUE.matches(value)) return Parsed.Malformed(name)
            name to value
        }
        if (pairs.none { (n, v) -> n == SESSION && v.isNotEmpty() }) return Parsed.NoSession
        return Parsed.Ok(pairs.joinToString("; ") { (n, v) -> "$n=$v" })
    }

    // Google's session cookie names (SID, __Secure-1PSID, SAPISID, NID, AEC, SOCS...) and any __Secure-/__Host- cookie.
    private val SECRET_PAIR = Regex("""((?:__Secure-|__Host-)[\w-]+|[\w-]*SID[\w-]*|NID|AEC|SOCS)=[^;\s,"]+""")
    private val HEADER_LINE = Regex("""(?i)((?:set-)?cookie:)[^\r\n]*""")

    /** Text safe for a log, crash report or exception message: Cookie headers and session cookie values become "<redacted>". */
    fun redact(text: String): String = text
        .replace(HEADER_LINE) { "${it.groupValues[1]} <redacted>" }
        .replace(SECRET_PAIR) { "${it.groupValues[1]}=<redacted>" }
}
