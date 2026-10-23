package dev.wolzard.recon.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entry
import kotlinx.coroutines.flow.Flow

/** D37: the library. INTEGER key so the FTS index keeps pointing at the right row (a TEXT key's rowid can change on VACUUM). */
@Entity(
    tableName = "entries",
    indices = [Index(value = ["canonicalKey"], unique = true), Index(value = ["needsAttention"])],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalType: String,
    val canonicalKey: String,
    val name: String,
    val summary: String,
    val primaryLink: String?,
    val stars: Int?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val needsAttention: Boolean = false,
    val attentionReason: String? = null,
)

/** Slugs this entry is also known by; unique across the library, so one alias points at one entry. */
@Entity(
    tableName = "entry_aliases",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId"), Index(value = ["alias"], unique = true)],
)
data class AliasEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entryId: Long, val alias: String)

/** Which videos mention the entry. Deleting a job or an entry removes the link. */
@Entity(
    tableName = "entry_jobs",
    primaryKeys = ["entryId", "jobId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("jobId")],
)
data class EntryJobEntity(val entryId: Long, val jobId: String, val createdAt: Long)

@Entity(tableName = "entries_fts")
@Fts4(contentEntity = EntryEntity::class, tokenizer = androidx.room.FtsOptions.TOKENIZER_UNICODE61)
data class EntryFts(val name: String, val summary: String)

data class EntryRow(@Embedded val entry: EntryEntity, val videos: Int) {
    fun toEntry(aliases: List<String> = emptyList()) = with(entry) {
        Entry(id, EntityType.fromWire(canonicalType), canonicalKey, name, summary, primaryLink, stars, status, videos, createdAt, updatedAt, needsAttention, attentionReason, aliases)
    }
}

private const val VIDEOS = "(SELECT COUNT(DISTINCT COALESCE(j.normalizedUrl, j.id)) FROM entry_jobs ej JOIN jobs j ON j.id = ej.jobId WHERE ej.entryId = e.id) AS videos"

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE canonicalKey = :key") suspend fun byKey(key: String): EntryEntity?
    @Query("SELECT e.* FROM entries e JOIN entry_aliases a ON a.entryId = e.id WHERE a.alias = :alias") suspend fun byAlias(alias: String): EntryEntity?
    @Insert suspend fun insert(e: EntryEntity): Long
    @Update suspend fun update(e: EntryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlias(a: AliasEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLink(l: EntryJobEntity): Long

    /**
     * Entries with at least one video, newest activity first. [fts] is a ready MATCH expression, "" for no search;
     * [prefix] is the same text as an alias slug. [all] = 1 ignores [types].
     */
    @Query(
        "SELECT e.*, $VIDEOS FROM entries e " +
            "WHERE (:all = 1 OR e.canonicalType IN (:types)) " +
            "AND (:fts = '' OR e.id IN (SELECT rowid FROM entries_fts WHERE entries_fts MATCH :fts) OR e.id IN (SELECT entryId FROM entry_aliases WHERE alias LIKE :prefix || '%')) " +
            "AND EXISTS (SELECT 1 FROM entry_jobs WHERE entryId = e.id) " +
            "ORDER BY e.updatedAt DESC, e.id DESC LIMIT :limit",
    )
    fun observeList(all: Int, types: List<String>, fts: String, prefix: String, limit: Int): Flow<List<EntryRow>>

    @Query("SELECT e.*, $VIDEOS FROM entries e WHERE e.id = :id") fun observe(id: Long): Flow<EntryRow?>
    @Query("SELECT alias FROM entry_aliases WHERE entryId = :id ORDER BY alias") fun observeAliases(id: Long): Flow<List<String>>

    /** The entry's videos, newest first, without the columns list rows never read. */
    @Query("SELECT $LIGHT_JOB FROM jobs WHERE id IN (SELECT jobId FROM entry_jobs WHERE entryId = :id) ORDER BY createdAt DESC")
    fun observeVideos(id: Long): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE state = 'DONE' AND resultJson IS NOT NULL AND id NOT IN (SELECT jobId FROM entry_jobs)")
    suspend fun unlinkedDoneJobs(): List<JobEntity>
}
