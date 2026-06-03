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
            try {
                val out = r.research(todo, ctx)
                out.findings.forEach { resolved[it.entity] = it }
                references += out.references
            } catch (e: ResearchUnavailable) {
                degraded[r.id] = e.retryAtEpochMs
            }
        }

        var sources = emptyList<Source>()
        var suggestions: String? = null
        var model: String? = null
        // Candidate pickers (no link yet) are left to the user; everything else goes to the one AI call.
        val aiItems = researched.filter { resolved[it]?.candidates?.isNotEmpty() != true }
            .map { ResearchItem(it, references[it], resolved[it]) }
        if (aiItems.isNotEmpty()) {
            if (!budgetLeft) {
                if (aiItems.any { it.fixed == null }) degraded["budget"] = null
            } else {
                val ai = if (grounding() == GroundingCapability.AVAILABLE && web != null) web else knowledge
                try {
                    val out = ai.research(aiItems, ctx)
                    out.findings.forEach { resolved[it.entity] = it }
                    sources = out.sources
                    suggestions = out.searchSuggestionsHtml
                    model = out.model
                } catch (e: ResearchUnavailable) {
                    degraded[ai.id] = e.retryAtEpochMs
                }
            }
        }

        val findings = entities.map { e -> resolved[e] ?: notChecked(e) }
        return ResearchOutcome(findings, sources, suggestions, degraded, model)
    }

    private fun notChecked(e: Entity) = Finding(
        entity = e, researcher = "none", verdict = Verdict.NOT_CHECKED, title = e.name, whatItIs = e.saidInVideo,
        keyFacts = emptyList(), howToTry = "", whyInVideo = "", primaryLink = null, basis = Basis.VIDEO_ONLY,
    )

    private companion object { const val MAX_RESEARCHED = 6 }
}
