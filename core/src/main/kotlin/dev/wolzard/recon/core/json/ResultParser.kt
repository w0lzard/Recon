package dev.wolzard.recon.core.json

import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.model.VerdictCap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Lenient parsing + validation of model JSON. Everything model-written is untrusted input. */
object ResultParser {
    private const val MAX_ENTITIES = 8
    private const val MAX_ON_SCREEN = 20
    private const val MAX_ON_SCREEN_LEN = 200
    private const val MAX_STRING = 500
    private const val MAX_FACTS = 5

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val ownerRe = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val repoNameRe = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val githubPrefixRe = Regex("^(?:https?://)?(?:www\\.)?github\\.com/", RegexOption.IGNORE_CASE)
    private val urlRe = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

    fun parseAnalysis(raw: String): AnalysisResult {
        val o = parseObject(raw)
        val summary = o.str("summary") ?: invalid("summary missing")
        val entities = (o["entities"] as? JsonArray).orEmpty()
