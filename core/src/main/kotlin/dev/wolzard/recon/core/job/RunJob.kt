package dev.wolzard.recon.core.job

import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.link.Route
import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.port.Analysis
import dev.wolzard.recon.core.port.FetchQuality
import dev.wolzard.recon.core.port.FetchedMedia
import dev.wolzard.recon.core.port.FrameSampler
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.LinkResolver
import dev.wolzard.recon.core.port.MediaFetcher
import dev.wolzard.recon.core.port.VideoAnalyzer
import dev.wolzard.recon.core.research.ResearchChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Fetchers(val link: MediaFetcher, val sharedFile: MediaFetcher)
data class Language(val display: String, val tag: String)
data class Timeouts(
    val pathA: Duration = 150.seconds,
    val pathB: Duration = 120.seconds,
    val maxPathABytes: Long = 50L * 1024 * 1024,
)

enum class UserAction {
    RUN,             // first run, Retry, or resume after process death
