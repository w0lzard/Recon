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

@Entity(
    tableName = "jobs",
    indices = [androidx.room.Index(value = ["normalizedUrl"])],
)
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
    val normalizedUrl: String? = null,  // D31
    val failedStep: JobState? = null,   // D35: the step that was running when the job failed
    val rawJson: String? = null,        // D35: the model's reply, as received
)

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<JobEntity>>
    @Query("SELECT $LIGHT_JOB FROM jobs ORDER BY createdAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<JobEntity>>
    @Query("SELECT COUNT(*) FROM jobs") fun observeCount(): Flow<Int>
    @Query("SELECT * FROM jobs WHERE id = :id") fun observe(id: String): Flow<JobEntity?>
    @Query("SELECT * FROM jobs WHERE id = :id") suspend fun get(id: String): JobEntity?
    @Query("SELECT * FROM jobs WHERE state IN (:states)") suspend fun inStates(states: List<JobState>): List<JobEntity>
    @Query("SELECT * FROM jobs WHERE normalizedUrl = :normalizedUrl ORDER BY createdAt DESC") suspend fun findByNormalizedUrl(normalizedUrl: String): List<JobEntity>
    @Insert suspend fun insert(job: JobEntity)
    @Update suspend fun update(job: JobEntity)
    @Query("DELETE FROM jobs WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [JobEntity::class, EntryEntity::class, AliasEntity::class, EntryJobEntity::class, EntryFts::class], version = 7, exportSchema = true)
abstract class ReconDb : RoomDatabase() {
    abstract fun jobs(): JobDao
    abstract fun entries(): EntryDao
}

/** Every job column except the saved analysis and the raw model reply, which list rows never read (the raw reply can be 100 KB). */
internal const val LIGHT_JOB = "id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, NULL AS analysisJson, resultJson, " +
    "createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl, startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio, " +
    "normalizedUrl, failedStep, NULL AS rawJson"

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

/** D31: jobs gain the normalizedUrl canonical link key for duplicate checking. Existing rows keep NULL. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN normalizedUrl TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_normalizedUrl ON jobs(normalizedUrl)")
    }
}

/** D35: jobs gain the step that failed and the model's raw reply. Existing rows keep NULL = not known. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN failedStep TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN rawJson TEXT")
    }
}

/** D37: the library tables and their FTS4 index. Existing rows are untouched; the library is filled by the backfill and by finished jobs. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `canonicalType` TEXT NOT NULL, `canonicalKey` TEXT NOT NULL, `name` TEXT NOT NULL, `summary` TEXT NOT NULL, `primaryLink` TEXT, `stars` INTEGER, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `needsAttention` INTEGER NOT NULL DEFAULT 0, `attentionReason` TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_canonicalKey` ON `entries` (`canonicalKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_needsAttention` ON `entries` (`needsAttention`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `entry_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `alias` TEXT NOT NULL, FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_aliases_entryId` ON `entry_aliases` (`entryId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entry_aliases_alias` ON `entry_aliases` (`alias`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `entry_jobs` (`entryId` INTEGER NOT NULL, `jobId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `jobId`), FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`jobId`) REFERENCES `jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_jobs_jobId` ON `entry_jobs` (`jobId`)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `entries_fts` USING FTS4(`name` TEXT NOT NULL, `summary` TEXT NOT NULL, tokenize=unicode61, content=`entries`)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_BEFORE_UPDATE BEFORE UPDATE ON `entries` BEGIN DELETE FROM `entries_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_BEFORE_DELETE BEFORE DELETE ON `entries` BEGIN DELETE FROM `entries_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_AFTER_UPDATE AFTER UPDATE ON `entries` BEGIN INSERT INTO `entries_fts`(`docid`, `name`, `summary`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`summary`); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_AFTER_INSERT AFTER INSERT ON `entries` BEGIN INSERT INTO `entries_fts`(`docid`, `name`, `summary`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`summary`); END")
    }
}

fun JobEntity.toJob() = Job(
    id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl,
    startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio, normalizedUrl, failedStep, rawJson,
)
fun Job.toEntity() = JobEntity(
    id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl,
    startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio, normalizedUrl, failedStep, rawJson,
)
