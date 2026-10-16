package dev.wolzard.recon.work

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import dev.wolzard.recon.data.media.YtDlpRunner
import kotlinx.coroutines.CancellationException
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class SaveVideoWorkerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val fakeRunner = FakeSaveYtDlpRunner()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        startKoin {
            modules(module {
                single<YtDlpRunner> { fakeRunner }
                single { Notifier(ctx) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun missingInputsYieldsFailure() {
        runBlocking {
            val worker = TestListenableWorkerBuilder<SaveVideoWorker>(ctx).build()
            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        }
    }

    @Test
    fun successfulSaveStreamsToTargetUriAndCleansTempDir() {
        runBlocking {
            val targetFile = File.createTempFile("target_video", ".mp4")
            val targetUri = Uri.fromFile(targetFile)

            val input = workDataOf(
                SaveVideoWorker.KEY_JOB_ID to "job_123",
                SaveVideoWorker.KEY_URL to "https://youtu.be/sample",
                SaveVideoWorker.KEY_FORMAT_ID to "137+140",
                SaveVideoWorker.KEY_TITLE to "Sample Video",
                SaveVideoWorker.KEY_TARGET_URI to targetUri.toString(),
                SaveVideoWorker.KEY_REAL_HEIGHT to 1080,
            )

            fakeRunner.onRun = { destDir ->
                File(destDir, "output.mp4").writeText("dummy merged video content")
                "${destDir.path}/output.mp4"
            }

            val worker = TestListenableWorkerBuilder<SaveVideoWorker>(ctx).setInputData(input).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(workDataOf(
                SaveVideoWorker.KEY_SAVED_URI to targetUri.toString(),
                SaveVideoWorker.KEY_DESTINATION to "Movies/Recon",
                SaveVideoWorker.KEY_RESOLUTION to "1080p",
            )), result)

            assertEquals("dummy merged video content", targetFile.readText())

            // Ensure temp directory in cacheDir is completely cleaned up
            val tempDir = File(ctx.cacheDir, "save_video/job_123")
            assertFalse("Temp dir must be deleted on completion", tempDir.exists())

            targetFile.delete()
        }
    }

    @Test
    fun failureCleansUpTempDir() {
        runBlocking {
            val input = workDataOf(
                SaveVideoWorker.KEY_JOB_ID to "job_fail",
                SaveVideoWorker.KEY_URL to "https://youtu.be/sample",
                SaveVideoWorker.KEY_FORMAT_ID to "137+140",
                SaveVideoWorker.KEY_TITLE to "Failing Video",
            )

            fakeRunner.onRun = { throw IllegalStateException("Download failed") }

            val worker = TestListenableWorkerBuilder<SaveVideoWorker>(ctx).setInputData(input).build()
            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            val tempDir = File(ctx.cacheDir, "save_video/job_fail")
            assertFalse("Temp dir must be deleted on failure", tempDir.exists())
        }
    }

    @Test
    fun cancellationCleansUpTempDir() {
        runBlocking {
            val input = workDataOf(
                SaveVideoWorker.KEY_JOB_ID to "job_cancel",
                SaveVideoWorker.KEY_URL to "https://youtu.be/sample",
                SaveVideoWorker.KEY_FORMAT_ID to "137+140",
                SaveVideoWorker.KEY_TITLE to "Cancel Video",
            )

            fakeRunner.onRun = { throw CancellationException("Worker was cancelled") }

            val worker = TestListenableWorkerBuilder<SaveVideoWorker>(ctx).setInputData(input).build()
            var caughtCancellation = false
            try {
                worker.doWork()
            } catch (e: CancellationException) {
                caughtCancellation = true
            }

            assertTrue("CancellationException must be rethrown", caughtCancellation)
            val tempDir = File(ctx.cacheDir, "save_video/job_cancel")
            assertFalse("Temp dir must be deleted on cancellation", tempDir.exists())
        }
    }

    private class FakeSaveYtDlpRunner : YtDlpRunner {
        var onRun: ((destDir: File) -> String)? = null

        override suspend fun run(
            url: String,
            args: List<String>,
            processId: String,
            onProgress: (Float) -> Unit
        ): String {
            val destIndex = args.indexOf("-o")
            val destDir = if (destIndex >= 0 && destIndex + 1 < args.size) {
                File(args[destIndex + 1]).parentFile ?: File(".")
            } else File(".")

            onProgress(0.5f)
            return onRun?.invoke(destDir) ?: "${destDir.path}/output.mp4"
        }
    }
}
