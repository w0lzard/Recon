package dev.wolzard.recon.feature.notice

import dev.wolzard.recon.data.notice.NOTICE_VERSION
import dev.wolzard.recon.data.notice.Notice
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
import dev.wolzard.recon.navigation.HomeDestination
import dev.wolzard.recon.navigation.SignInDestination
import dev.wolzard.recon.navigation.NoticeDestination
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.navigation.startDestination
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
        assertEquals(NoticeDestination, startDestination(noticeNeeded = true, signedIn = false))
        assertEquals(NoticeDestination, startDestination(noticeNeeded = true, signedIn = true))
    }

    @Test fun afterAcceptNextLaunchSkipsIt() {
        Notice(prefs).accept()
        assertFalse(Notice(prefs).needed)
        assertEquals(SignInDestination, startDestination(noticeNeeded = false, signedIn = false))
        assertEquals(HomeDestination, startDestination(noticeNeeded = false, signedIn = true))
    }

    @Test fun bumpingNoticeVersionShowsItAgain() {
        Notice(prefs).accept()
        assertTrue(Notice(prefs, version = NOTICE_VERSION + 1).needed)
    }

    @Test fun screenShowsTextAndOnlyIUnderstand() {
        var accepted = false
        compose.setContent { NoticeScreen { accepted = true } }
        compose.onNodeWithText("Recon is a personal tool", substring = true).assertExists()
        compose.onNodeWithText("Google's terms apply", substring = true).assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("I understand").performClick()
        assertTrue(accepted)
    }

    // --- pending share

    @Test fun shareBeforeAcceptCreatesNoJobUntilAccept() = runBlocking {
        val gate = ShareGate(Notice(prefs), intake::accept)
        assertNull(gate.share(share("look https://www.instagram.com/reel/abc/")))
        assertTrue(jobs().isEmpty())

        val id = gate.accepted()!!
        assertEquals("look https://www.instagram.com/reel/abc/", store.get(id)!!.sharedText)
        assertNull(gate.accepted()) // released once
        assertEquals(1, jobs().size)
    }

    @Test fun afterAcceptSharesGoStraightThrough() = runBlocking {
        Notice(prefs).accept()
        val gate = ShareGate(Notice(prefs), intake::accept)
        assertTrue(gate.share(share("https://youtu.be/a")) != null)
    }

    @Test fun heldShareIsValidatedOnlyWhenReleased() = runBlocking {
        val gate = ShareGate(Notice(prefs), intake::accept)
        gate.share(share("javascript:alert(1) file:///data/data/dev.wolzard.recon/databases/recon.db intent://x#Intent;end"))
        val id = gate.accepted()
        // Released through the same intake as any share: no web link survives, so RunJob fails it as UNSUPPORTED_LINK.
        val job = store.get(id!!)!!
        assertNull(job.url)
        assertNull(LinkRouter.extractUrl(job.sharedText))
        // A non-share intent held before accept is dropped by the intake.
        gate.share(Intent(Intent.ACTION_VIEW))
        assertNull(gate.accepted())
    }
}
