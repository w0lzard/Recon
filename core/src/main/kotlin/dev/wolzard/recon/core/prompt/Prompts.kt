package dev.wolzard.recon.core.prompt

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem

/** Prompt text and JSON schemas. Wording is specified in docs/plan.md → "Prompts and JSON schemas". */
object Prompts {

    fun analyze(language: String): String = ANALYZE.replace("{language}", language)

    /** Grounded research (optional researcher, D20). Shape is embedded in the prompt, no responseSchema (D16). */
    fun research(ctx: ResearchContext, items: List<ResearchItem>): String = RESEARCH_HEAD
        .replace("{summary}", ctx.summary) + itemLines(items) + RESEARCH_TAIL.replace("{language}", ctx.language)

    /** Ungrounded default research (D13): answers are labelled "Unverified, from AI knowledge". */
    fun knowledge(ctx: ResearchContext, items: List<ResearchItem>): String = KNOWLEDGE_HEAD
        .replace("{summary}", ctx.summary) + itemLines(items) + KNOWLEDGE_TAIL.replace("{language}", ctx.language)

    private fun itemLines(items: List<ResearchItem>): String = items.mapIndexed { i, it ->
        val e = it.entity
        val q = e.qualifier?.let { " — $it" }.orEmpty()
        val line = "$i. [${e.type.wire}] ${e.name}$q — the video says: \"${e.saidInVideo}\""
        val ref = it.reference?.let { r -> "\nReference (${refLabel(it)}): $r" }.orEmpty()
        line + ref
    }.joinToString("\n", postfix = "\n")

    private fun refLabel(item: ResearchItem) = when (item.fixed?.basis) {
        Basis.WIKIPEDIA -> "Wikipedia extract"
        else -> "GitHub README"
    }

    private val ANALYZE = """
You are analysing a short social-media video for a viewer who wants to know what it is about and to look up the specific things it mentions.

