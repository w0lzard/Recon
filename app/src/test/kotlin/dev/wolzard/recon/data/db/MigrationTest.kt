package dev.wolzard.recon.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.Verdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * D23/D26: real version-1 and version-2 databases (built from the exported schemas/…/1.json and 2.json, not from
 * today's entity) are opened by Room with MIGRATION_1_2 and MIGRATION_2_3. Room validates the migrated tables against the current entity when it opens, so a column
 * that is missing or has the wrong type fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before fun setUp() { ctx.deleteDatabase(name) }
    @After fun tearDown() { ctx.deleteDatabase(name) }

    private fun createV1(insertRow: Boolean) = create(1) { db ->
        if (insertRow) db.execSQL(
            "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt) " +
                "VALUES ('j1', 'look https://youtu.be/a', 'https://youtu.be/a', NULL, 'DONE', NULL, NULL, NULL, NULL, '{\"a\":1}', '{\"r\":1}', 10, 20)",
        )
    }

    private fun create(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/dev.wolzard.recon.data.db.ReconDb/$version.json").readText()).jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
            db.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["contentSyncTriggers"]?.jsonArray?.forEach { db.execSQL(it.jsonPrimitive.content) } // v7 onwards: the FTS4 index stays in step
            e.jsonObject["indices"]?.jsonArray?.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) } // v5 onwards
        }
        fill(db)
        db.version = version
        db.close()
    }

    private fun open() = Room.databaseBuilder(ctx, ReconDb::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).allowMainThreadQueries().build()

    @Test fun v1RowsKeepTheirDataAndGetNoContext() {
        createV1(insertRow = true)
        val room = open()
        try {
            val job = runBlocking { RoomJobStore(room).observeAll().first() }.single()
            assertEquals("j1", job.id)
            assertEquals("look https://youtu.be/a", job.sharedText)
            assertEquals("https://youtu.be/a", job.url)
            assertEquals("{\"a\":1}", job.analysisJson)
            assertEquals("{\"r\":1}", job.resultJson)
            assertEquals(10L, job.createdAt)
            assertNull(job.userContext)
        } finally {
            room.close()
        }
    }

    @Test fun migratedDatabaseStoresContextForNewJobs() {
        createV1(insertRow = false)
        val room = open()
        try {
            val store = RoomJobStore(room)
            val id = runBlocking { store.create("https://youtu.be/a", null, "  What song is this?  ") }
            assertEquals("What song is this?", runBlocking { store.get(id) }!!.userContext)
        } finally {
            room.close()
        }
    }

    @Test fun v1SchemaHasNoUserContextColumn() { // guards the test itself: it must start from the old shape
        createV1(insertRow = false)
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(jobs)", null).use { c ->
            val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertEquals(false, "userContext" in cols)
        }
        db.close()
    }

    // --- D26: version 2 to 3 adds the link preview columns

    @Test fun v2RowsKeepTheirContextAndGetNoPreview() {
        create(2) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext) " +
                    "VALUES ('j2', 'https://youtu.be/b', 'https://youtu.be/b', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{\"r\":2}', 30, 40, 'What song is this?')",
            )
        }
        val room = open()
        try {
            val job = runBlocking { RoomJobStore(room).get("j2") }!!
            assertEquals("What song is this?", job.userContext)
            assertEquals("{\"r\":2}", job.resultJson)
            assertNull(job.videoTitle); assertNull(job.videoDurationSec); assertNull(job.thumbnailUrl)
        } finally {
            room.close()
        }
    }

    @Test fun migratedV2DatabaseStoresAPreview() {
        create(2) { }
        val room = open()
        try {
            val store = RoomJobStore(room)
            val id = runBlocking { store.create("https://youtu.be/b", null, null) }
            runBlocking { store.update(id) { it.copy(videoTitle = "Glass Harbour", videoDurationSec = 38, thumbnailUrl = "https://i.ytimg.com/vi/b/hq.jpg") } }
            val job = runBlocking { store.get(id) }!!
            assertEquals("Glass Harbour", job.videoTitle)
            assertEquals(38L, job.videoDurationSec)
            assertEquals("https://i.ytimg.com/vi/b/hq.jpg", job.thumbnailUrl)
        } finally {
            room.close()
        }
    }

    // --- D28: version 3 to 4 adds the run record

    @Test fun v3RowsKeepTheirPreviewAndStoreARunRecord() {
        create(3) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl) " +
                    "VALUES ('j3', 'https://youtu.be/c', 'https://youtu.be/c', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{\"r\":3}', 50, 60, NULL, 'Glass Harbour', 38, NULL)",
            )
        }
        val room = open()
        try {
            val store = RoomJobStore(room)
            val job = runBlocking { store.get("j3") }!!
            assertEquals("Glass Harbour", job.videoTitle)
            assertNull(job.startedAt); assertNull(job.downloadBytes); assertNull(job.frameCount); assertNull(job.hasAudio)
            runBlocking { store.update("j3") { it.copy(startedAt = 1, downloadedAt = 2, sampledAt = 3, analysedAt = 4, downloadBytes = 4_400_000, frameCount = 24, hasAudio = true) } }
            val after = runBlocking { store.get("j3") }!!
            assertEquals(listOf<Any?>(1L, 2L, 3L, 4L, 4_400_000L, 24, true), listOf(after.startedAt, after.downloadedAt, after.sampledAt, after.analysedAt, after.downloadBytes, after.frameCount, after.hasAudio))
        } finally {
            room.close()
        }
    }

    @Test fun v2SchemaHasNoPreviewColumns() { // guards the test itself
        create(2) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(jobs)", null).use { c ->
            val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertEquals(true, "userContext" in cols)
            assertEquals(false, "videoTitle" in cols || "thumbnailUrl" in cols || "videoDurationSec" in cols)
        }
        db.close()
    }

    // --- D31: version 4 to 5 adds the normalizedUrl canonical link key column

    @Test fun v4RowsKeepTheirDataAndGetNoNormalizedUrl() {
        create(4) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, userContext, videoTitle, videoDurationSec, thumbnailUrl, startedAt, downloadedAt, sampledAt, analysedAt, downloadBytes, frameCount, hasAudio) " +
                    "VALUES ('j4', 'https://youtu.be/d', 'https://youtu.be/d', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{\"r\":4}', 70, 80, NULL, 'Title', 40, NULL, 1, 2, 3, 4, 1000, 10, 1)",
            )
        }
        val room = open()
        try {
            val store = RoomJobStore(room)
            val job = runBlocking { store.get("j4") }!!
            assertEquals("https://youtu.be/d", job.url)
            assertNull(job.normalizedUrl)
        } finally {
            room.close()
        }
    }

    @Test fun migratedV4DatabaseStoresNormalizedUrlAndQueriesByIt() {
        create(4) { }
        val room = open()
        try {
            val store = RoomJobStore(room)
            val id = runBlocking { store.create("https://youtu.be/d", null, "context", normalizedUrl = "youtube:d") }
            val job = runBlocking { store.get(id) }!!
            assertEquals("youtube:d", job.normalizedUrl)
            val byNormalized = runBlocking { store.findByNormalizedUrl("youtube:d") }
            assertEquals(listOf(job), byNormalized)
        } finally {
            room.close()
        }
    }

    @Test fun v4SchemaHasNoNormalizedUrlColumn() { // guards the test itself
        create(4) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(jobs)", null).use { c ->
            val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertEquals(true, "userContext" in cols)
            assertEquals(true, "startedAt" in cols)
            assertEquals(false, "normalizedUrl" in cols)
        }
        db.close()
    }

    // --- D35: version 5 to 6 adds the failed step and the model's raw reply

    @Test fun v5RowsKeepTheirDataAndStoreTheNewColumns() {
        create(5) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, normalizedUrl) " +
                    "VALUES ('j5', 'https://youtu.be/e', 'https://youtu.be/e', NULL, 'FAILED', 'NETWORK', 'timed out', NULL, NULL, NULL, NULL, 90, 100, 'youtube:e')",
            )
        }
        val room = open()
        try {
            val store = RoomJobStore(room)
            val job = runBlocking { store.get("j5") }!!
            assertEquals("youtube:e", job.normalizedUrl)
            assertEquals(dev.wolzard.recon.core.model.FailureReason.NETWORK, job.failure)
            assertNull(job.failedStep); assertNull(job.rawJson)
            runBlocking { store.update("j5") { it.copy(failedStep = dev.wolzard.recon.core.model.JobState.ANALYSING, rawJson = "{\"x\":1}") } }
            val after = runBlocking { store.get("j5") }!!
            assertEquals(dev.wolzard.recon.core.model.JobState.ANALYSING, after.failedStep)
            assertEquals("{\"x\":1}", after.rawJson)
        } finally {
            room.close()
        }
    }

    @Test fun v5SchemaHasNoFailedStepOrRawJson() { // guards the test itself
        create(5) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(jobs)", null).use { c ->
            val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertEquals(true, "normalizedUrl" in cols)
            assertEquals(false, "failedStep" in cols || "rawJson" in cols)
        }
        db.close()
    }

    // --- D37: version 6 to 7 adds the library tables and the FTS4 name index

    @Test fun v6RowsSurviveAndTheLibraryWorksWithSearchAndCascades() = runBlocking {
        create(6) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt) " +
                    "VALUES ('j6', 'https://youtu.be/f', 'https://youtu.be/f', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{\"r\":6}', 110, 120)",
            )
        }
        val room = open()
        try {
            assertEquals("{\"r\":6}", RoomJobStore(room).get("j6")!!.resultJson)
            val dao = room.entries()
            val id = dao.insert(EntryEntity(canonicalType = "repo", canonicalKey = "github:kmori/stowly", name = "kmori/stowly", summary = "Dotfile manager", primaryLink = null, stars = 4100, status = "Verified", createdAt = 1, updatedAt = 2))
            dao.insertAlias(AliasEntity(entryId = id, alias = "stowly"))
            dao.insertLink(EntryJobEntity(id, "j6", 3))
            assertEquals(listOf(id), dao.observeList(1, emptyList(), "\"dotfile\"*", "dotfile", 10).first().map { it.entry.id })
            assertEquals(listOf(id), dao.observeList(1, emptyList(), "\"stow\"*", "stow", 10).first().map { it.entry.id }) // prefix, by name
            assertEquals(1, dao.observeList(0, listOf("repo"), "", "", 10).first().single().videos)
            assertEquals(emptyList<Long>(), dao.observeList(0, listOf("app"), "", "", 10).first().map { it.entry.id })
            // The index follows edits: the old word stops matching, the new one starts.
            dao.update(dao.byKey("github:kmori/stowly")!!.copy(summary = "Symlink farm"))
            assertEquals(emptyList<Long>(), dao.observeList(1, emptyList(), "\"dotfile\"*", "dotfile", 10).first().map { it.entry.id })
            assertEquals(listOf(id), dao.observeList(1, emptyList(), "\"symlink\"*", "symlink", 10).first().map { it.entry.id })
            // Deleting the video removes the link; with no video left the entry is not listed.
            RoomJobStore(room).delete("j6")
            assertEquals(emptyList<Long>(), dao.observeList(1, emptyList(), "", "", 10).first().map { it.entry.id })
        } finally {
            room.close()
        }
    }

    /** A real v6 database: two done videos on one repo (different links, same canonical key), a failed job and an in-flight job that both carry a result. */
    @Test fun v6BackfillFilesDuplicatesOnceSkipsUnfinishedJobsAndTheIndexFollowsRenameAndDelete() = runBlocking {
        fun finding(name: String, title: String) = Finding(Entity(EntityType.REPO, name, null, Evidence.SPOKEN, "said"), "web", Verdict.CONFIRMED, title, "Dotfile manager", emptyList(), "", "", "https://github.com/kmori/stowly", 4100, Basis.GITHUB_API)
        fun result(f: Finding) = Json.encodeToString(JobResult.serializer(), JobResult(AnalysisResult(summary = "s", confidence = 0.9), listOf(f), emptyList(), null, "A")).replace("'", "''")
        val stowly = result(finding("stowly", "kmori/stowly"))
        create(6) { db ->
            fun job(id: String, url: String, state: String, at: Long) = db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, normalizedUrl) " +
                    "VALUES ('$id', '$url', '$url', NULL, '$state', NULL, NULL, NULL, NULL, NULL, '$stowly', $at, $at, '$url')",
            )
            job("a", "https://youtu.be/a", "DONE", 1)
            job("b", "https://youtu.be/b", "DONE", 2)
            job("failed", "https://youtu.be/f", "FAILED", 3)
            job("busy", "https://youtu.be/r", "ANALYSING", 4)
        }
        val room = open()
        try {
            val store = RoomEntryStore(room) { 100 }
            assertEquals(emptyList<Long>(), store.observeList("", null, 10).first().map { it.id })
            store.backfill()
            val e = store.observeList("", null, 10).first().single()
            assertEquals("github:kmori/stowly", e.key)
            assertEquals(2, e.videos)   // the failed and in-flight jobs are not filed
            assertEquals(listOf("b", "a"), store.observeVideos(e.id).first().map { it.id })
            // Running it again changes nothing.
            store.backfill()
            val again = store.observeList("", null, 10).first().single()
            assertEquals(e, again)
            room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entry_jobs WHERE jobId IN ('failed','busy')").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            // The FTS4 index follows a rename: the old name stops matching, the new one starts. (Checked on the index itself;
            // the alias "kmori-stowly" still finds the entry by search, which is intended.)
            fun hits(word: String) = room.openHelper.readableDatabase.query("SELECT rowid FROM entries_fts WHERE entries_fts MATCH '$word'").use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() }
            assertEquals(listOf(e.id), hits("kmori"))
            val dao = room.entries()
            dao.update(dao.byKey(e.key)!!.copy(name = "Symlinkr"))
            assertEquals(emptyList<Long>(), hits("kmori"))
            assertEquals(listOf(e.id), hits("symlinkr"))
            assertEquals(listOf(e.id), hits("symlink*"))
            assertEquals(listOf(e.id), store.observeList("symlink", null, 10).first().map { it.id })
            // ...and a delete: the entry and its index row are gone.
            room.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = ${e.id}")
            assertEquals(emptyList<Long>(), hits("symlinkr"))
            assertEquals(emptyList<Long>(), store.observeList("symlink", null, 10).first().map { it.id })
        } finally {
            room.close()
        }
    }

    // --- D38: version 7 to 8 adds GitHub facts to entries and the typed entry_links table

    @Test fun v7EntriesKeepTheirDataAndGetEmptyFactsAndNoBadge() = runBlocking {
        create(7) { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, normalizedUrl) " +
                    "VALUES ('j7', 'https://youtu.be/g', 'https://youtu.be/g', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{}', 1, 2, 'https://youtu.be/g')",
            )
            db.execSQL("INSERT INTO entries (id, canonicalType, canonicalKey, name, summary, primaryLink, stars, status, createdAt, updatedAt, needsAttention, attentionReason) VALUES (5, 'repo', 'github:kmori/stowly', 'kmori/stowly', 'Dotfile manager', NULL, 4100, 'Verified', 1, 2, 0, NULL)")
            db.execSQL("INSERT INTO entry_jobs (entryId, jobId, createdAt) VALUES (5, 'j7', 3)")
        }
        val room = open()
        try {
            val dao = room.entries()
            val e = dao.byKey("github:kmori/stowly")!!
            assertEquals(5L, e.id)
            assertEquals("Dotfile manager", e.summary)
            assertEquals(4100, e.stars)
            assertEquals(false, e.isArchived)                       // NOT NULL DEFAULT 0
            assertNull(e.pushedAt); assertNull(e.latestReleaseTag); assertNull(e.latestReleaseAt)
            assertNull(e.licenseSpdx); assertNull(e.language)
            assertNull(e.maturity)                                  // null renders as no badge
            assertEquals(1, room.entries().observeList(1, emptyList(), "", "", 10).first().single().videos)   // link and FTS survived
            assertEquals(listOf(5L), room.openHelper.readableDatabase.query("SELECT rowid FROM entries_fts WHERE entries_fts MATCH 'dotfile'").use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() })
            // entry_links exists, takes rows, refuses a repeat of the same link, and goes with its entry.
            val db = room.openHelper.writableDatabase
            db.execSQL("INSERT INTO entry_links (entryId, url, linkType) VALUES (5, 'https://github.com/kmori/stowly', 'REPO')")
            db.execSQL("INSERT INTO entry_links (entryId, url, linkType) VALUES (5, 'https://docs.stowly.dev', 'DOCS')")
            val dup = runCatching { db.execSQL("INSERT INTO entry_links (entryId, url, linkType) VALUES (5, 'https://docs.stowly.dev', 'DOCS')") }
            assertEquals(true, dup.isFailure)
            db.query("SELECT COUNT(*) FROM entry_links").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            db.execSQL("DELETE FROM entries WHERE id = 5")
            db.query("SELECT COUNT(*) FROM entry_links").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally {
            room.close()
        }
    }

    @Test fun v7SchemaHasNoFactsColumnsOrLinksTable() { // guards the test itself
        create(7) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(entries)", null).use { c -> val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList(); assertEquals(false, "maturity" in cols || "isArchived" in cols) }
        db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'entry_links'", null).use { assertEquals(0, it.count) }
        db.close()
    }

    // --- D39: version 8 to 9 adds the structured entry fields and the search-phrase table with its FTS4 index

    @Test fun v8EntriesKeepTheirDataAndGetEmptyStructuredFieldsAndAWorkingPhraseIndex() = runBlocking {
        create(8) { db ->
            db.execSQL("INSERT INTO entries (id, canonicalType, canonicalKey, name, summary, primaryLink, stars, status, createdAt, updatedAt, needsAttention, attentionReason, maturity, isArchived) VALUES (5, 'repo', 'github:yt-dlp/yt-dlp', 'yt-dlp/yt-dlp', 'Downloader', NULL, 90000, 'Verified', 1, 2, 0, NULL, 'ESTABLISHED', 0)")
        }
        val room = open()
        try {
            val e = room.entries().byKey("github:yt-dlp/yt-dlp")!!
            assertEquals("ESTABLISHED", e.maturity)                 // the version 8 columns survive
            assertNull(e.problemSolved); assertNull(e.keyFeaturesJson); assertNull(e.useCasesJson); assertNull(e.whenToUse); assertNull(e.whenNotToUse)
            val dao = room.entries()
            dao.insertPhrase(SearchPhraseEntity(entryId = 5, phrase = "video download tool"))
            dao.insertPhrase(SearchPhraseEntity(entryId = 5, phrase = "video download tool"))   // a repeat is ignored
            assertEquals(1, dao.phraseCount(5))
            fun hits(q: String) = room.openHelper.readableDatabase.query("SELECT rowid FROM search_phrases_fts WHERE search_phrases_fts MATCH '$q'").use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() }
            assertEquals(1, hits("download*").size)
            assertEquals(0, hits("android*").size)
            room.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = 5")   // the phrase and its index row go with the entry
            assertEquals(0, dao.phraseCount(5)); assertEquals(0, hits("download*").size)
        } finally {
            room.close()
        }
    }

    @Test fun v8SchemaHasNoStructuredFieldsOrPhrases() { // guards the test itself
        create(8) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(entries)", null).use { c -> val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList(); assertEquals(false, "problemSolved" in cols) }
        db.rawQuery("SELECT name FROM sqlite_master WHERE name LIKE '%search_phrases%'", null).use { assertEquals(0, it.count) }
        db.close()
    }

    @Test fun v6SchemaHasNoLibraryTables() { // guards the test itself
        create(6) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("SELECT name FROM sqlite_master WHERE name LIKE 'entr%'", null).use { assertEquals(0, it.count) }
        db.close()
    }
}
