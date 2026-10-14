package dev.wolzard.recon.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val sharedText: String,
    val url: String?,
    val localUri: String?,
    val state: JobState,
    val failure: FailureReason?,
    val failureDetail: String?,
    val retryAtEpochMs: Long?,
    val progress: Float?,
    val analysisJson: String?,   // D7: JSON text columns, read whole, never queried inside
    val resultJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val userContext: String? = null,   // D23
    val videoTitle: String? = null,     // D26: link preview
    val videoDurationSec: Long? = null,
    val thumbnailUrl: String? = null,
    val startedAt: Long? = null,        // D28: run record
    val downloadedAt: Long? = null,
    val sampledAt: Long? = null,
    val analysedAt: Long? = null,
    val downloadBytes: Long? = null,
    val frameCount: Int? = null,
    val hasAudio: Boolean? = null,
)

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<JobEntity>>
    @Query("SELECT * FROM jobs WHERE id = :id") fun observe(id: String): Flow<JobEntity?>
    @Query("SELECT * FROM jobs WHERE id = :id") suspend fun get(id: String): JobEntity?
    @Query("SELECT * FROM jobs WHERE state IN (:states)") suspend fun inStates(states: List<JobState>): List<JobEntity>
    @Insert suspend fun insert(job: JobEntity)
    @Update suspend fun update(job: JobEntity)
    @Query("DELETE FROM jobs WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [JobEntity::class], version = 4, exportSchema = true)
abstract class ReconDb : RoomDatabase() {
    abstract fun jobs(): JobDao
}

/** D23: jobs gain the viewer's optional note. Existing rows keep NULL = no context. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("ALTER TABLE jobs ADD COLUMN userContext TEXT")
}

/** D26: jobs gain the link preview shown on Home and the Job screen. Existing rows keep NULL = no preview. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN videoTitle TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN videoDurationSec INTEGER")
        db.execSQL("ALTER TABLE jobs ADD COLUMN thumbnailUrl TEXT")
    }
}

/** D28: jobs gain the run record (step times, download size, frames). Existing rows keep NULL = not known. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("startedAt", "downloadedAt", "sampledAt", "analysedAt", "downloadBytes", "frameCount", "hasAudio")
            .forEach { db.execSQL("ALTER TABLE jobs ADD COLUMN $it INTEGER") }
    }
}

fun JobEntity.toJob() = Job(
    id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl,
    startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio,
)
fun Job.toEntity() = JobEntity(
    id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl,
    startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio,
)
