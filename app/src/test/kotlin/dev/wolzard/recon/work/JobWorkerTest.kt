package dev.wolzard.recon.work

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class JobWorkerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ReconDb
    private lateinit var store: JobStore
    private val seenStates = mutableListOf<JobState>()
