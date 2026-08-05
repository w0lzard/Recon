package dev.wolzard.recon.core.model

/** The viewer's optional note or question (D23). Cleaned once at Start and again when the prompt is built. */
object UserContext {
    const val MAX_LENGTH = 300

    /** Trimmed and cut to [MAX_LENGTH]; null when nothing is left. */
    fun clean(raw: String?): String? = raw?.trim()?.take(MAX_LENGTH)?.trim()?.takeIf(String::isNotEmpty)
}
