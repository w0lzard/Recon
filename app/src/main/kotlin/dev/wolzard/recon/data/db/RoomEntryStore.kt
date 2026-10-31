package dev.wolzard.recon.data.db

import androidx.room.withTransaction
import dev.wolzard.recon.core.library.CanonicalKey
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.core.model.Finding
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
        val tokens = WORD.findAll(query).map { it.value }.toList()
        if (query.isNotBlank() && tokens.isEmpty()) return flowOf(emptyList())
        // Every word must match, the last as a prefix too. FTS4 has no quoted prefix ("w"*), so the last word is bare w*;
        // that still makes a typed OR / NEAR a plain word, since only the exact keyword is an operator.
        val fts = tokens.mapIndexed { i, t -> if (i == tokens.lastIndex) "$t*" else "\"$t\"" }.joinToString(" ")
        return dao.observeList(if (types == null) 1 else 0, types.orEmpty().map { it.wire }, fts, CanonicalKey.slug(query), limit).map { rows -> rows.map { it.toEntry() } }
    }

    override fun observe(id: Long): Flow<Entry?> =
        combine(dao.observe(id), dao.observeAliases(id)) { row, aliases -> row?.toEntry(aliases) }

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
            ))
            entry.id
        } else {
            dao.insert(EntryEntity(
                canonicalType = f.entity.type.wire, canonicalKey = key, name = f.title.ifBlank { f.entity.name }.take(MAX_NAME), summary = summaryOf(f),
                primaryLink = f.primaryLink.safe(), stars = f.stars, status = status, createdAt = now, updatedAt = now,
                needsAttention = flag != null, attentionReason = flag,
            ))
        }
        aliases.forEach { dao.insertAlias(AliasEntity(entryId = id, alias = it)) }   // an alias another entry owns is left with that entry
        dao.insertLink(EntryJobEntity(id, jobId, now))
    }

    private fun summaryOf(f: Finding) = f.whatItIs.ifBlank { f.entity.saidInVideo }.take(MAX_SUMMARY)
    private fun String?.safe() = this?.takeIf { it.startsWith("https://") }

    private companion object {
        val WORD = Regex("[\\p{L}\\p{N}]+")
        val json = Json { ignoreUnknownKeys = true }
        const val MAX_NAME = 200
        const val MAX_SUMMARY = 600
    }
}
