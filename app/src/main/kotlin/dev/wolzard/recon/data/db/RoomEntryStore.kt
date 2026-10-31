package dev.wolzard.recon.data.db

import androidx.room.withTransaction
import dev.wolzard.recon.core.library.CanonicalKey
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.core.model.EntryLink
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.research.LinkClassifier
import dev.wolzard.recon.core.research.LinkType
import dev.wolzard.recon.core.research.Maturity
import dev.wolzard.recon.core.search.NeedMatcher
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.port.EntryStore
import dev.wolzard.recon.feature.job.JobCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class RoomEntryStore(private val db: ReconDb, private val clock: () -> Long = System::currentTimeMillis) : EntryStore {
    private val dao = db.entries()

    override fun observeList(query: String, types: Set<EntityType>?, limit: Int): Flow<List<Entry>> {
        val fts = NeedMatcher.ftsQuery(query)
        if (query.isNotBlank() && fts.isEmpty()) return flowOf(emptyList())
        val all = if (types == null) 1 else 0
        val wires = types.orEmpty().map { it.wire }
        return dao.observeList(all, wires, fts, CanonicalKey.slug(query), limit).map { rows ->
            // Full-text search first (name, summary, aliases, search phrases); only a search that finds nothing tries typos.
            val found = if (rows.isEmpty() && fts.isNotEmpty()) typoFallback(query, all, wires, limit) else rows
            found.map { it.toEntry() }
        }
    }

    /** On-device edit-distance match over the names and aliases of the listed entries. */
    private suspend fun typoFallback(query: String, all: Int, wires: List<String>, limit: Int): List<EntryRow> {
        val aliases = dao.aliasIndex().groupBy({ it.entryId }, { it.alias })
        val ids = NeedMatcher.typoMatches(query, dao.nameIndex().map { NeedMatcher.Candidate(it.id, listOf(it.name) + aliases[it.id].orEmpty()) })
        if (ids.isEmpty()) return emptyList()
        val byId = dao.byIds(ids, all, wires).associateBy { it.entry.id }
        return ids.mapNotNull(byId::get).take(limit)
    }

    override fun observe(id: Long): Flow<Entry?> =
        combine(dao.observe(id), dao.observeAliases(id), dao.observeEntryLinks(id)) { row, aliases, links ->
            row?.toEntry(aliases, links.mapNotNull { l -> LinkType.entries.firstOrNull { it.name == l.linkType }?.let { EntryLink(l.url, it) } })
        }

    override fun observeVideos(id: Long): Flow<List<Job>> = dao.observeVideos(id).map { rows -> rows.map { it.toJob() } }

    override suspend fun linkJob(job: Job) {
        val result = job.resultJson?.let { runCatching { json.decodeFromString(JobResult.serializer(), it) }.getOrNull() } ?: return
        db.withTransaction { result.findings.forEach { link(job.id, it) } }
    }

    override suspend fun backfill() {
        dao.unlinkedDoneJobs().forEach { linkJob(it.toJob()) }
    }

    /**
     * Same key, or the same type under a name this library already knows, attaches. A repo is only ever identified by
     * its key (two owners can share a repo name), and a name shared across types is a guess: those become a new entry
     * flagged for the owner, never an automatic merge.
     */
    private suspend fun link(jobId: String, f: Finding) {
        val key = CanonicalKey.of(f) ?: return
        val aliases = CanonicalKey.aliases(f)
        val now = clock()
        val status = JobCopy.verdictChip(f).first
        var entry = dao.byKey(key)
        var flag: String? = null
        if (entry == null) {
            val hit = aliases.firstNotNullOfOrNull { dao.byAlias(it) }
            if (hit != null) {
                if (hit.canonicalType == f.entity.type.wire && f.entity.type != EntityType.REPO) entry = hit else flag = "Possible duplicate of ${hit.name}"
            }
        }
        val id = if (entry != null) {
            dao.update(entry.copy(
                updatedAt = now, status = status, stars = f.stars ?: entry.stars,
                primaryLink = entry.primaryLink ?: f.primaryLink.safe(), summary = entry.summary.ifBlank { summaryOf(f) },
            ).withFacts(f).withFields(f))
            entry.id
        } else {
            dao.insert(EntryEntity(
                canonicalType = f.entity.type.wire, canonicalKey = key, name = f.title.ifBlank { f.entity.name }.take(MAX_NAME), summary = summaryOf(f),
                primaryLink = f.primaryLink.safe(), stars = f.stars, status = status, createdAt = now, updatedAt = now,
                needsAttention = flag != null, attentionReason = flag,
            ).withFacts(f).withFields(f))
        }
        aliases.forEach { dao.insertAlias(AliasEntity(entryId = id, alias = it)) }
        f.entity.searchPhrases.take((MAX_PHRASES_PER_ENTRY - dao.phraseCount(id)).coerceAtLeast(0)).forEach { dao.insertPhrase(SearchPhraseEntity(entryId = id, phrase = it)) }   // an alias another entry owns is left with that entry
        dao.insertLink(EntryJobEntity(id, jobId, now))
        f.primaryLink.safe()?.let { url -> LinkClassifier.classify(url)?.let { dao.insertEntryLink(EntryLinkEntity(entryId = id, url = url, linkType = it.name)) } }
    }

    /** What the model said about the entity replaces the stored text when it said something; an empty field never wipes an earlier one. */
    private fun EntryEntity.withFields(f: Finding): EntryEntity = f.entity.let { e ->
        copy(
            problemSolved = e.problemSolved ?: problemSolved, keyFeaturesJson = e.keyFeatures.toJsonOrNull() ?: keyFeaturesJson,
            useCasesJson = e.useCases.toJsonOrNull() ?: useCasesJson, whenToUse = e.whenToUse ?: whenToUse, whenNotToUse = e.whenNotToUse ?: whenNotToUse,
        )
    }

    /** GitHub facts from this finding replace the stored ones; a finding without any (not a GitHub hit, or an AI guess) leaves them alone. */
    private fun EntryEntity.withFacts(f: Finding): EntryEntity {
        val facts = f.repoFacts ?: return this
        return copy(
            pushedAt = facts.pushedAt, isArchived = facts.archived, licenseSpdx = facts.licenseSpdx, language = facts.language,
            latestReleaseTag = facts.releaseTag ?: latestReleaseTag, latestReleaseAt = if (facts.releaseTag != null) facts.releaseAt else latestReleaseAt,
            maturity = facts.maturity.takeIf { it != Maturity.UNKNOWN }?.name,
        )
    }

    private fun summaryOf(f: Finding) = f.whatItIs.ifBlank { f.entity.saidInVideo }.take(MAX_SUMMARY)
    private fun String?.safe() = this?.takeIf { it.startsWith("https://") }

    private companion object {
        val WORD = Regex("[\\p{L}\\p{N}]+")
        val json = Json { ignoreUnknownKeys = true }
        const val MAX_NAME = 200
        const val MAX_PHRASES_PER_ENTRY = 24
        const val MAX_SUMMARY = 600
    }
}
