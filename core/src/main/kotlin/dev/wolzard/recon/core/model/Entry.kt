package dev.wolzard.recon.core.model

/** D37: one thing in the library (a repo, app, book, film...), kept once however many videos mention it. */
data class Entry(
    val id: Long,
    val type: EntityType,
    val key: String,                 // CanonicalKey
    val name: String,
    val summary: String,
    val primaryLink: String?,
    val stars: Int?,
    val status: String,              // the badge word as the job card showed it: Verified, Supported, Unverified, Unclear, Contradicted
    val videos: Int,                 // distinct videos, a re-analysis of the same link counts once
    val createdAt: Long,
    val updatedAt: Long,
    val needsAttention: Boolean = false,
    val attentionReason: String? = null,
    val aliases: List<String> = emptyList(),   // filled for the detail screen only
)
