package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.core.model.Job
import kotlinx.coroutines.flow.Flow

/** D37: the library. Reads are paged by [limit]; nothing here calls a model or the network. */
interface EntryStore {
    /** Entries with at least one video, newest activity first. [query] is matched on name, summary and aliases; [types] null = all. */
    fun observeList(query: String, types: Set<EntityType>?, limit: Int): Flow<List<Entry>>
    fun observe(id: Long): Flow<Entry?>
    /** The entry's videos, newest first. */
    fun observeVideos(id: Long): Flow<List<Job>>
    /** Files a finished job's findings under their entries. Safe to repeat. */
    suspend fun linkJob(job: Job)
    /** Files finished jobs that predate the library. Run once. */
    suspend fun backfill()
}
