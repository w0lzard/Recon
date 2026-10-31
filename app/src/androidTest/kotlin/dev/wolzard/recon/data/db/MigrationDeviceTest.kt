package dev.wolzard.recon.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D38: the 7→8 migration on the device's own SQLite (the JVM MigrationTest runs on Robolectric's). A real version-7
 * database is built from the exported schema 7.json (packaged as a test asset), filled, and opened by Room with every migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-device-test.db"

    @Before fun setUp() { ctx.deleteDatabase(name) }
    @After fun tearDown() { ctx.deleteDatabase(name) }

    private fun createV7(fill: (SQLiteDatabase) -> Unit) {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("dev.wolzard.recon.data.db.ReconDb/7.json").bufferedReader().readText()
        val schema = Json.parseToJsonElement(json).jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val table = e.jsonObject["tableName"]!!.jsonPrimitive.content
            db.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["contentSyncTriggers"]?.jsonArray?.forEach { db.execSQL(it.jsonPrimitive.content) }
            e.jsonObject["indices"]?.jsonArray?.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        fill(db)
        db.version = 7
        db.close()
    }

    @Test fun v7LibraryMigratesToV8WithEmptyFactsAndAWorkingLinksTable() = runBlocking {
        createV7 { db ->
            db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, normalizedUrl) " +
                    "VALUES ('j7', 'https://youtu.be/g', 'https://youtu.be/g', NULL, 'DONE', NULL, NULL, NULL, NULL, NULL, '{}', 1, 2, 'https://youtu.be/g')",
            )
            db.execSQL("INSERT INTO entries (id, canonicalType, canonicalKey, name, summary, primaryLink, stars, status, createdAt, updatedAt, needsAttention, attentionReason) VALUES (5, 'repo', 'github:kmori/stowly', 'kmori/stowly', 'Dotfile manager', NULL, 4100, 'Verified', 1, 2, 0, NULL)")
            db.execSQL("INSERT INTO entry_jobs (entryId, jobId, createdAt) VALUES (5, 'j7', 3)")
        }
        val room = Room.databaseBuilder(ctx, ReconDb::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val e = room.entries().byKey("github:kmori/stowly")!!
            assertEquals(5L, e.id); assertEquals(4100, e.stars)
            assertEquals(false, e.isArchived)
            assertNull(e.pushedAt); assertNull(e.latestReleaseTag); assertNull(e.licenseSpdx); assertNull(e.language); assertNull(e.maturity)
            assertEquals(1, room.entries().observeList(1, emptyList(), "dotfile*", "dotfile", 10).first().single().videos)   // the FTS4 index survived
            val db = room.openHelper.writableDatabase
            db.execSQL("INSERT INTO entry_links (entryId, url, linkType) VALUES (5, 'https://github.com/kmori/stowly', 'REPO')")
            assertTrue(runCatching { db.execSQL("INSERT INTO entry_links (entryId, url, linkType) VALUES (5, 'https://github.com/kmori/stowly', 'REPO')") }.isFailure)
            db.execSQL("DELETE FROM entries WHERE id = 5")
            db.query("SELECT COUNT(*) FROM entry_links").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally {
            room.close()
        }
    }
}
