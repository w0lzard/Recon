package dev.wolzard.recon.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Verdict { CONFIRMED, BEST_MATCH, NOT_FOUND, NOT_CHECKED, SUPPORTED, DISPUTED, UNCLEAR }

/** What a verdict rests on; drives the UI label. AI_KNOWLEDGE = "Unverified, from AI knowledge". */
enum class Basis { GITHUB_API, WIKIPEDIA, WEB_SOURCES, AI_KNOWLEDGE, VIDEO_ONLY }

@Serializable
data class GhRepo(
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
)

@Serializable
data class Finding(
    val entity: Entity,
    val researcher: String,        // "github" | "wikipedia" | "web" | "knowledge" | "none"
    val verdict: Verdict,          // claims: SUPPORTED/DISPUTED/UNCLEAR; others: CONFIRMED/BEST_MATCH/NOT_FOUND/NOT_CHECKED
    val title: String,             // canonical name found (repo: "owner/name")
    val whatItIs: String,
    val keyFacts: List<String>,    // ≤ 5
    val howToTry: String,          // type-specific next step; may be ""
    val whyInVideo: String,
    val primaryLink: String?,      // API data only: GitHub html_url or Wikipedia page URL, or null
    val stars: Int? = null,        // repos only
    val basis: Basis,
    val candidates: List<GhRepo> = emptyList(),   // repos: 2–3 fuzzy GitHub matches for the user to pick
)

@Serializable
data class Source(val title: String, val uri: String)   // from groundingMetadata.groundingChunks[].web

/** [language] = display name for prompts ("Hindi"); [languageTag] = ISO code for Wikipedia ("hi"). */
data class ResearchContext(val summary: String, val language: String, val languageTag: String = "en")

/** One item of the single research call. [fixed] = already resolved by GitHub/Wikipedia: verdict and link are kept. */
data class ResearchItem(val entity: Entity, val reference: String? = null, val fixed: Finding? = null)

enum class VerdictCap { WEB, KNOWLEDGE }
