package dev.wolzard.recon.core.model

import dev.wolzard.recon.core.research.LinkType
import dev.wolzard.recon.core.research.Maturity

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
    // D38: GitHub facts for repos; null / false when never fetched. No badge for a null maturity or UNKNOWN.
    val pushedAt: Long? = null,
    val latestReleaseTag: String? = null,
    val latestReleaseAt: Long? = null,
    val archived: Boolean = false,
    val license: String? = null,
    val language: String? = null,
    val maturity: Maturity? = null,
    val links: List<EntryLink> = emptyList(),  // filled for the detail screen only
    // D39: what the model said about it, cleaned by ResultParser. Null / empty when the video did not say.
    val problemSolved: String? = null,
    val keyFeatures: List<String> = emptyList(),
    val useCases: List<String> = emptyList(),
    val whenToUse: String? = null,
    val whenNotToUse: String? = null,
)

data class EntryLink(val url: String, val type: LinkType)
