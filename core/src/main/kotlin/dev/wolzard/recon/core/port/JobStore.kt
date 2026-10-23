package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.Job
import kotlinx.coroutines.flow.Flow

interface JobStore {
    fun observeAll(): Flow<List<Job>>          // newest first
    /** The newest [limit] jobs, without the saved analysis and raw model reply: for list screens, which page by growing [limit]. */
    fun observeRecent(limit: Int): Flow<List<Job>>
    fun observeCount(): Flow<Int>
    fun observe(id: String): Flow<Job?>
    suspend fun get(id: String): Job?
    suspend fun create(sharedText: String, localUri: String?, userContext: String? = null, normalizedUrl: String? = null): String
    suspend fun update(id: String, transform: (Job) -> Job)
    suspend fun delete(id: String)
    suspend fun findRunning(): List<Job>       // DOWNLOADING / ANALYSING / RESEARCHING
    suspend fun findByNormalizedUrl(normalizedUrl: String): List<Job>
}
