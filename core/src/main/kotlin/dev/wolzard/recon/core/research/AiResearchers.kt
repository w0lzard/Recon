package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.json.ResultParser
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.model.VerdictCap
import dev.wolzard.recon.core.port.AiResearcher
import dev.wolzard.recon.core.port.LanguageModel
import dev.wolzard.recon.core.port.ResearchOutput
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.prompt.Prompts

/** Default (D13): ungrounded, "Unverified, from AI knowledge", verdicts capped at BEST_MATCH / UNCLEAR. */
class KnowledgeResearcher(private val lm: LanguageModel) : AiResearcher {
    override val id = "knowledge"

    override suspend fun research(items: List<ResearchItem>, ctx: ResearchContext): ResearchOutput = try {
        val answer = lm.generate(Prompts.knowledge(ctx, items), schema = null, googleSearch = false)
        ResearchOutput(ResultParser.parseResearch(answer.text, items, VerdictCap.KNOWLEDGE), model = answer.model)
    } catch (e: AnalyzerException) {
        throw ResearchUnavailable(e.retryAtEpochMs, e) // research problems never fail a job
    }
}

/** Optional (D20): grounded with Google Search, only when the key's capability test passed. */
class WebSearchResearcher(private val lm: LanguageModel) : AiResearcher {
    override val id = "web"

    override suspend fun research(items: List<ResearchItem>, ctx: ResearchContext): ResearchOutput = try {
        val answer = lm.generate(Prompts.research(ctx, items), schema = null, googleSearch = true)
        val findings = ResultParser.parseResearch(answer.text, items, VerdictCap.WEB).map { f ->
            // CONFIRMED/SUPPORTED need at least one grounding source (fallback table).
            if (answer.sources.isNotEmpty() || f.basis != dev.wolzard.recon.core.model.Basis.WEB_SOURCES) f
            else when (f.verdict) {
                Verdict.CONFIRMED -> f.copy(verdict = Verdict.BEST_MATCH)
                Verdict.SUPPORTED -> f.copy(verdict = Verdict.UNCLEAR)
                else -> f
            }
        }
        ResearchOutput(findings, answer.sources, answer.searchSuggestionsHtml, model = answer.model)
    } catch (e: AnalyzerException) {
        throw ResearchUnavailable(e.retryAtEpochMs, e)
    }
}
