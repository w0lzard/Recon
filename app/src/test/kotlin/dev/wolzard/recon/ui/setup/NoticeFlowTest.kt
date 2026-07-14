package dev.wolzard.recon.ui.setup

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import dev.wolzard.recon.Home
import dev.wolzard.recon.SignIn
import dev.wolzard.recon.Notice as NoticeRoute
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.startDestination
import dev.wolzard.recon.work.ShareGate
import dev.wolzard.recon.work.ShareIntake
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoticeFlowTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = ctx.getSharedPreferences("notice-test", Context.MODE_PRIVATE)
    private lateinit var db: ReconDb
    private lateinit var store: RoomJobStore
    private lateinit var intake: ShareIntake

    @Before fun setUp() {
        prefs.edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        db = Room.inMemoryDatabaseBuilder(ctx, ReconDb::class.java).allowMainThreadQueries().build()
        store = RoomJobStore(db)
        intake = ShareIntake(ctx, store, ContentUriFetcher(ctx))
    }
    @After fun close() = db.close()

    private fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    private fun jobs() = runBlocking { store.observeAll().first() }

    // --- gating

    @Test fun freshInstallShowsNoticeBeforeSignIn() {
        assertTrue(Notice(prefs).needed)
        assertEquals(NoticeRoute, startDestination(noticeNeeded = true, signedIn = false))
        assertEquals(NoticeRoute, startDestination(noticeNeeded = true, signedIn = true))
    }

    @Test fun afterAcceptNextLaunchSkipsIt() {
        Notice(prefs).accept()
        assertFalse(Notice(prefs).needed)
        assertEquals(SignIn, startDestination(noticeNeeded = false, signedIn = false))
        assertEquals(Home, startDestination(noticeNeeded = false, signedIn = true))
    }

    @Test fun bumpingNoticeVersionShowsItAgain() {
        Notice(prefs).accept()
        assertTrue(Notice(prefs, version = NOTICE_VERSION + 1).needed)
    }

    @Test fun screenShowsTextAndOnlyIUnderstand() {
        var accepted = false
        compose.setContent { NoticeScreen { accepted = true } }
