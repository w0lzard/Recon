package dev.wolzard.recon.data.media

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FrameSamplerTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dir = File(ctx.cacheDir, "jobs/sampler-test").apply { deleteRecursively(); mkdirs() }

    @After fun clean() { dir.deleteRecursively() }

