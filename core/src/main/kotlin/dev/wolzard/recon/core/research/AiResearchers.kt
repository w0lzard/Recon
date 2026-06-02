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
