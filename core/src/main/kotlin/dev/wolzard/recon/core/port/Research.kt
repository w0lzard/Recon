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
