package dev.wolzard.recon.data.db

import androidx.room.withTransaction
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomJobStore(private val db: ReconDb, private val clock: () -> Long = System::currentTimeMillis) : JobStore {
    private val dao = db.jobs()

    override fun observeAll(): Flow<List<Job>> = dao.observeAll().map { list -> list.map { it.toJob() } }
    override fun observe(id: String): Flow<Job?> = dao.observe(id).map { it?.toJob() }
    override suspend fun get(id: String): Job? = dao.get(id)?.toJob()

    override suspend fun create(sharedText: String, localUri: String?): String {
        val now = clock()
        val id = UUID.randomUUID().toString()
        dao.insert(Job(id = id, sharedText = sharedText, localUri = localUri, createdAt = now, updatedAt = now).toEntity())
        return id
    }

    override suspend fun update(id: String, transform: (Job) -> Job) = db.withTransaction {
        val current = dao.get(id)?.toJob() ?: return@withTransaction
        dao.update(transform(current).copy(id = current.id, createdAt = current.createdAt, updatedAt = clock()).toEntity())
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun findRunning(): List<Job> = dao.inStates(RUNNING).map { it.toJob() }

    private companion object { val RUNNING = listOf(JobState.DOWNLOADING, JobState.ANALYSING, JobState.RESEARCHING) }
}
