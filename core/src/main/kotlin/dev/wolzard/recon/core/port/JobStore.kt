package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.Job
import kotlinx.coroutines.flow.Flow

interface JobStore {
    fun observeAll(): Flow<List<Job>>          // newest first
    fun observe(id: String): Flow<Job?>
    suspend fun get(id: String): Job?
    suspend fun create(sharedText: String, localUri: String?, userContext: String? = null): String
    suspend fun update(id: String, transform: (Job) -> Job)
    suspend fun delete(id: String)
    suspend fun findRunning(): List<Job>       // DOWNLOADING / ANALYSING / RESEARCHING
}
