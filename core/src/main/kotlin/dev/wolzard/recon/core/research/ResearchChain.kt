package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Source
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.AiResearcher
import dev.wolzard.recon.core.port.GroundingCapability
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.port.Researcher

data class ResearchOutcome(
    val findings: List<Finding>,
    val sources: List<Source> = emptyList(),
    val searchSuggestionsHtml: String? = null,
    val degraded: Map<String, Long?> = emptyMap(),
    val model: String? = null,
)

/**
 * D13: GitHub and Wikipedia (keyless, no AI), then **at most one** AI call: grounded web when the key's capability
 * is AVAILABLE, else knowledge. Problems never fail the job; they become `degraded` + NOT_CHECKED leftovers.
 */
class ResearchChain(
    private val github: Researcher,
    private val wikipedia: Researcher,
    private val knowledge: AiResearcher,
    private val web: AiResearcher?,
    private val grounding: () -> GroundingCapability,
) {
    suspend fun run(entities: List<Entity>, ctx: ResearchContext, budgetLeft: Boolean): ResearchOutcome {
        if (entities.isEmpty()) return ResearchOutcome(emptyList())
        val researched = entities.take(MAX_RESEARCHED)
        val resolved = LinkedHashMap<Entity, Finding>()
        val references = mutableMapOf<Entity, String>()
        val degraded = mutableMapOf<String, Long?>()

        for (r in listOf(github, wikipedia)) {
            val todo = researched.filter { it !in resolved && r.accepts(it.type) }
            if (todo.isEmpty()) continue
