package dev.wolzard.recon.core.model

import kotlinx.serialization.Serializable

enum class JobState { QUEUED, WAITING_QUOTA, DOWNLOADING, ANALYSING, RESEARCHING, DONE, FAILED }

enum class FailureReason {
    UNSUPPORTED_LINK, DOWNLOAD_FAILED, VIDEO_UNREADABLE, QUOTA_EXCEEDED,
    API_KEY_MISSING, API_KEY_INVALID, NETWORK, MODEL_OUTPUT_INVALID, MODEL_BUSY, UNKNOWN,
}

data class Job(
    val id: String,
    val sharedText: String,
    val url: String? = null,
    val localUri: String? = null,
    val state: JobState = JobState.QUEUED,
    val failure: FailureReason? = null,
    val failureDetail: String? = null,
    val retryAtEpochMs: Long? = null,
    val progress: Float? = null,
    val analysisJson: String? = null,   // saved before research, so Retry can resume without a new analysis
    val resultJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val userContext: String? = null,    // D23: optional viewer note/question, ≤ 300 chars, kept for Retry and "Try sharper video"
    // D26: link preview from Add context, for Home, the Job target card and the result header; display-only.
    val videoTitle: String? = null,
    val videoDurationSec: Long? = null,
    val thumbnailUrl: String? = null,       // https only (checked before it is stored and again before it is loaded)
)

/** Persisted as JSON in JobEntity.resultJson. */
@Serializable
data class JobResult(
    val analysis: AnalysisResult,
    val findings: List<Finding>,
    val sources: List<Source>,                       // per job (D17), grounded researcher only
    val searchSuggestionsHtml: String?,              // groundingMetadata.searchEntryPoint.renderedContent
    val path: String,                                // "youtube-url" | "A" | "B"
    val models: Map<String, String> = emptyMap(),    // "analyse"/"research" → model id that answered
    val degraded: Map<String, Long?> = emptyMap(),   // researcher id or "budget" → retryAt epoch ms (null = unknown)
)

class AnalyzerException(
    val reason: FailureReason,
    val retryAtEpochMs: Long? = null,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause)
