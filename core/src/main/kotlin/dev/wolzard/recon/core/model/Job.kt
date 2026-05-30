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
