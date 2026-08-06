package dev.wolzard.recon.core.json

import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Answer
import dev.wolzard.recon.core.model.AnswerStatus
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.ClaimDomain
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
    private const val MAX_ANSWER_SOURCES = 4

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val ownerRe = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val repoNameRe = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val githubPrefixRe = Regex("^(?:https?://)?(?:www\\.)?github\\.com/", RegexOption.IGNORE_CASE)
    private val urlRe = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

    /** [allowAnswer] = false drops any `answer` the model volunteers when the viewer asked nothing (D23). */
    fun parseAnalysis(raw: String, allowAnswer: Boolean = true): AnalysisResult {
        val o = parseObject(raw)
        val summary = o.str("summary") ?: invalid("summary missing")
        val entities = (o["entities"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::entity) }
            .distinctBy { Triple(it.type, it.qualifier?.lowercase(), it.name.lowercase()) }
            .take(MAX_ENTITIES)
        val onScreen = (o["onScreenText"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_ON_SCREEN_LEN) }
            .take(MAX_ON_SCREEN)
        val confidence = (o["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val answer = if (allowAnswer) answer(o["answer"]) else null
        return AnalysisResult(entities, summary.take(MAX_STRING), confidence.coerceIn(0.0, 1.0), onScreen, answer)
    }

    /**
     * A malformed or empty answer is dropped, not a failure: the analysis itself is still good.
     * "Verified" needs at least one source; without one it is shown as Unverified. An unknown status is Unclear.
     */
    private fun answer(el: JsonElement?): Answer? {
        val o = el as? JsonObject ?: return null
        val text = clean(o.str("text")).takeIf(String::isNotEmpty) ?: return null
        val sources = (o["sources"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::clean)?.takeIf(String::isNotEmpty) }
            .take(MAX_ANSWER_SOURCES)
        val status = when (o.str("status")?.trim()?.lowercase()) {
            "verified" -> if (sources.isEmpty()) AnswerStatus.UNVERIFIED else AnswerStatus.VERIFIED
            "unverified" -> AnswerStatus.UNVERIFIED
            else -> AnswerStatus.UNCLEAR
        }
        val confidence = (o["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        return Answer(text, status, confidence.coerceIn(0.0, 1.0), sources)
    }

    private fun entity(o: JsonObject): Entity? {
        var type = EntityType.fromWire(o.str("type"))
        var name = o.str("name")?.trim().orEmpty()
        var qualifier = o.str("qualifier")?.trim()?.takeIf(String::isNotEmpty)
        if (type == EntityType.REPO) {
            if (githubPrefixRe.containsMatchIn(name)) {
                val parts = name.replace(githubPrefixRe, "").trimEnd('/').split('/')
                if (parts.size >= 2) { qualifier = parts[0]; name = parts[1] }
            }
            if (qualifier != null && !ownerRe.matches(qualifier)) qualifier = null
            if (!repoNameRe.matches(name)) type = EntityType.OTHER
        }
        if (name.isBlank()) return null
        return Entity(
            type = type,
            name = name.take(MAX_STRING),
            qualifier = qualifier?.take(MAX_STRING),
            evidence = Evidence.fromWire(o.str("evidence")),
            saidInVideo = o.str("saidInVideo")?.trim().orEmpty().take(MAX_STRING),
            domain = if (type == EntityType.CLAIM) ClaimDomain.fromWire(o.str("domain")) else null,
        )
    }

    /**
     * Parses the single research call's `{"items":[…]}`. Items are keyed by `index` into [items];
     * out-of-range indexes are dropped, the first occurrence wins, missing items are not returned.
     */
    fun parseResearch(raw: String, items: List<ResearchItem>, cap: VerdictCap): List<Finding> {
        val arr = parseObject(raw)["items"] as? JsonArray ?: invalid("items missing")
        val byIndex = LinkedHashMap<Int, JsonObject>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val i = (o["index"] as? JsonPrimitive)?.intOrNull ?: continue
            if (i in items.indices && i !in byIndex) byIndex[i] = o
        }
        return byIndex.keys.sorted().map { i -> finding(items[i], byIndex.getValue(i), cap) }
    }

    private fun finding(item: ResearchItem, o: JsonObject, cap: VerdictCap): Finding {
        val whatItIs = clean(o.str("whatItIs"))
        val keyFacts = (o["keyFacts"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::clean)?.takeIf(String::isNotEmpty) }
            .take(MAX_FACTS)
        val howToTry = clean(o.str("howToTry"))
        val whyInVideo = clean(o.str("whyInVideo"))
        item.fixed?.let { f ->
            // Verdict, title, link and basis come from GitHub/Wikipedia; the model only supplies text.
            return f.copy(
                whatItIs = whatItIs.ifEmpty { f.whatItIs },
                keyFacts = keyFacts.ifEmpty { f.keyFacts },
                howToTry = howToTry.ifEmpty { f.howToTry },
                whyInVideo = whyInVideo.ifEmpty { f.whyInVideo },
            )
        }
        val knowledge = cap == VerdictCap.KNOWLEDGE
        // Without sources there is nothing to name, so knowledge answers carry none whatever the model wrote.
        val source = if (knowledge) null else clean(o.str("source")).takeIf { it.isNotEmpty() }
        var verdict = verdict(item.entity.type, o.str("verdict"), cap)
        // D24: a health, legal or financial claim is Supported or Disputed only on a named source.
        if (item.entity.type == EntityType.CLAIM && item.entity.domain?.sensitive == true && source == null) verdict = Verdict.UNCLEAR
        return Finding(
            entity = item.entity,
            researcher = if (knowledge) "knowledge" else "web",
            verdict = verdict,
            title = clean(o.str("title")).ifEmpty { item.entity.name },
            whatItIs = whatItIs,
            keyFacts = keyFacts,
            howToTry = howToTry,
            whyInVideo = whyInVideo,
            primaryLink = null,
            basis = if (knowledge) Basis.AI_KNOWLEDGE else Basis.WEB_SOURCES,
            source = source,
        )
    }

    private fun verdict(type: EntityType, raw: String?, cap: VerdictCap): Verdict {
        val v = when (raw?.trim()?.lowercase()) {
            "confirmed" -> Verdict.CONFIRMED
            "best_match" -> Verdict.BEST_MATCH
            "not_found" -> Verdict.NOT_FOUND
            "supported" -> Verdict.SUPPORTED
            "disputed" -> Verdict.DISPUTED
            "unclear" -> Verdict.UNCLEAR
            else -> null
        }
        val claimVerdicts = setOf(Verdict.SUPPORTED, Verdict.DISPUTED, Verdict.UNCLEAR)
        val mapped = if (type == EntityType.CLAIM) {
            v?.takeIf { it in claimVerdicts } ?: Verdict.UNCLEAR
        } else {
            v?.takeIf { it !in claimVerdicts } ?: Verdict.BEST_MATCH
        }
        if (cap == VerdictCap.WEB) return mapped
        // Without sources nothing is certain: knowledge answers are capped at BEST_MATCH / UNCLEAR.
        return when (mapped) {
            Verdict.CONFIRMED -> Verdict.BEST_MATCH
            Verdict.SUPPORTED, Verdict.DISPUTED -> Verdict.UNCLEAR
            else -> mapped
        }
    }

    private fun clean(s: String?): String =
        s.orEmpty().replace(urlRe, "").replace(Regex("[ \\t]{2,}"), " ").trim().take(MAX_STRING)

    /** Strips ``` fences and any prose around the outermost JSON object. */
    private fun parseObject(raw: String): JsonObject {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) invalid("no JSON object")
        val el: JsonElement = try {
            json.parseToJsonElement(raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw AnalyzerException(FailureReason.MODEL_OUTPUT_INVALID, message = "unparseable JSON", cause = e)
        }
        return el as? JsonObject ?: invalid("not an object")
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun invalid(msg: String): Nothing = throw AnalyzerException(FailureReason.MODEL_OUTPUT_INVALID, message = msg)
}
