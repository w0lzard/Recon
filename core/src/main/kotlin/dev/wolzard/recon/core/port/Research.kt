package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Source

interface Researcher {
    val id: String                                   // "github" | "wikipedia" | "web" | "knowledge"
    fun accepts(type: EntityType): Boolean
    /**
     * Returns findings only for entities it resolved; the rest pass to the next researcher.
     * @throws ResearchUnavailable when it can't run at all (quota, rate limit, offline).
     */
    suspend fun research(entities: List<Entity>, ctx: ResearchContext): ResearchOutput
}

/** [references] = text for the AI researcher to explain resolved items from (README, Wikipedia extract). */
data class ResearchOutput(
    val findings: List<Finding>,
    val sources: List<Source> = emptyList(),
    val searchSuggestionsHtml: String? = null,
    val references: Map<Entity, String> = emptyMap(),
    val model: String? = null,                        // AI researchers: the model that answered
)

class ResearchUnavailable(val retryAtEpochMs: Long?, cause: Throwable? = null) : Exception(cause)

interface GitHubClient {
    suspend fun repo(owner: String, name: String): GhRepo?      // null = 404. @throws GitHubRateLimited, IOException
    suspend fun readme(owner: String, name: String): String?
    suspend fun search(query: String): List<GhRepo>             // `q=<query> in:name`, ≤ 10 results
}

class GitHubRateLimited(val resetEpochMs: Long?) : Exception("GitHub rate limit")

/** The job's single research call (D9, D13): knowledge (default) or grounded web. */
interface AiResearcher {
    val id: String                                   // "knowledge" | "web"
    /** @throws ResearchUnavailable */
    suspend fun research(items: List<ResearchItem>, ctx: ResearchContext): ResearchOutput
}

/** Keyless Wikipedia REST (T10). */
interface WikipediaClient {
    suspend fun search(lang: String, query: String): List<WikiHit>        // ≤ 3
    suspend fun summary(lang: String, key: String): WikiSummary?
}
data class WikiHit(val key: String, val title: String)
data class WikiSummary(val title: String, val extract: String, val url: String?, val disambiguation: Boolean)
