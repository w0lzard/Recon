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
            .mapNotNull { (it as? JsonObject)?.let(::entity) }
            .distinctBy { Triple(it.type, it.qualifier?.lowercase(), it.name.lowercase()) }
            .take(MAX_ENTITIES)
        val onScreen = (o["onScreenText"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_ON_SCREEN_LEN) }
            .take(MAX_ON_SCREEN)
        val confidence = (o["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        return AnalysisResult(entities, summary.take(MAX_STRING), confidence.coerceIn(0.0, 1.0), onScreen)
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
        return Finding(
            entity = item.entity,
            researcher = if (knowledge) "knowledge" else "web",
            verdict = verdict(item.entity.type, o.str("verdict"), cap),
            title = clean(o.str("title")).ifEmpty { item.entity.name },
            whatItIs = whatItIs,
            keyFacts = keyFacts,
            howToTry = howToTry,
            whyInVideo = whyInVideo,
            primaryLink = null,
            basis = if (knowledge) Basis.AI_KNOWLEDGE else Basis.WEB_SOURCES,
        )
    }

    private fun verdict(type: EntityType, raw: String?, cap: VerdictCap): Verdict {
        val v = when (raw?.trim()?.lowercase()) {
            "confirmed" -> Verdict.CONFIRMED
            "best_match" -> Verdict.BEST_MATCH
            "not_found" -> Verdict.NOT_FOUND
