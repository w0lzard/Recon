package dev.wolzard.recon.core.library

import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Finding
import java.util.Locale

/**
 * D37: the identity of a library entry. People and claims never become entries: a person is not looked up or kept
 * from what a video shows, and a claim is a verdict on one video, not a thing to collect.
 */
object CanonicalKey {
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    const val GITHUB = "github:"
    const val MAX_NAME = 200

    fun slug(s: String): String = s.lowercase(Locale.ROOT).replace(NOT_ALNUM, "-").trim('-').take(MAX_NAME)

    /** True for types that can be kept in the library. */
    fun keeps(t: EntityType) = t != EntityType.PERSON && t != EntityType.CLAIM

    /** `github:owner/repo` for a repo GitHub confirmed, else `type:slug`; null = not kept or no usable name. */
    fun of(f: Finding): String? {
        val type = f.entity.type
        if (!keeps(type)) return null
        if (type == EntityType.REPO) {
            val parts = f.title.trim().split('/')
            if (parts.size == 2 && parts.all { it.isNotBlank() && !it.any(Char::isWhitespace) }) return GITHUB + f.title.trim().lowercase(Locale.ROOT)
        }
        val name = slug(f.title.ifBlank { f.entity.name })
        return if (name.isEmpty()) null else "${type.wire}:$name"
    }

    /** Names this finding is also known by: its own name, the title, and a repo's bare name. Slugs, never empty. */
    fun aliases(f: Finding): List<String> =
        listOf(f.entity.name, f.title, f.title.substringAfter('/')).map(::slug).filter { it.isNotEmpty() }.distinct()
}
