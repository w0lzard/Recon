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
        schema["entities"]!!.jsonArray.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", it.jsonObject["tableName"]!!.jsonPrimitive.content)) }
        fill(db)
        db.version = version
        db.close()
    }

    private fun open() = Room.databaseBuilder(ctx, ReconDb::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).allowMainThreadQueries().build()

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
}
