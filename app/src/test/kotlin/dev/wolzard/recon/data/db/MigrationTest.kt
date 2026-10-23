package dev.wolzard.recon.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            e.jsonObject["indices"]?.jsonArray?.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) } // v5 onwards
        }
        fill(db)
        db.version = version
        db.close()
    }

    private fun open() = Room.databaseBuilder(ctx, ReconDb::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()

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

    @Test fun v6SchemaHasNoLibraryTables() { // guards the test itself
        create(6) { }
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("SELECT name FROM sqlite_master WHERE name LIKE 'entr%'", null).use { assertEquals(0, it.count) }
        db.close()
    }
}
