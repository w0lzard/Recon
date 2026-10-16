package dev.wolzard.recon.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.media.VideoSaveOption
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.job.JobScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QualityPickerTest {
    @get:Rule val compose = createComposeRule()

    private val sampleJob = Job(
        id = "test_job",
        sharedText = "https://youtu.be/sample",
        url = "https://youtu.be/sample",
        state = JobState.DONE,
        createdAt = 0L,
        updatedAt = 0L,
    )
    private val sampleResult = JobResult(
        analysis = AnalysisResult(emptyList(), "Summary", 0.9),
        findings = emptyList(),
        sources = emptyList(),
        searchSuggestionsHtml = null,
        path = "youtube-url",
    )

    @Test
    fun pickerDisplaysThreePresetsAnd4kUnavailableWhenSourceIs1080p() {
        val options = listOf(
            VideoSaveOption(
                presetName = "480p",
                resolutionLabel = "480p",
                realHeight = 480,
                height = 480,
                width = 854,
                vcodec = "h264",
                acodec = "aac",
                formatId = "135+140",
                estimatedSizeBytes = 12_000_000L,
                isDefault = false,
                isEnabled = true,
            ),
            VideoSaveOption(
                presetName = "1080p",
                resolutionLabel = "1080p",
                realHeight = 1080,
                height = 1080,
                width = 1920,
                vcodec = "h264",
                acodec = "aac",
                formatId = "137+140",
                estimatedSizeBytes = 27_000_000L,
                isDefault = true,
                isEnabled = true,
            ),
            VideoSaveOption(
                presetName = "4K",
                resolutionLabel = "4K",
                realHeight = 1080,
                height = 2160,
                width = 3840,
                vcodec = "vp9",
                acodec = "opus",
                formatId = "",
                estimatedSizeBytes = 0L,
                isDefault = false,
                isEnabled = false,
                disabledReason = "4K not available, best is 1080p",
                note = "Large file, may play poorly on older phones",
            ),
        )

        compose.setContent {
            ReconTheme {
                JobScreen(
                    job = sampleJob,
                    result = sampleResult,
                    now = 0L,
                    isProbingFormats = false,
                    saveOptions = options,
                    saveError = null,
                    onIntent = {},
                )
            }
        }

        // Verify Dialog Title
        compose.onNodeWithText("Choose quality").assertIsDisplayed()

        // Verify presets
        compose.onNodeWithText("480p").assertIsDisplayed()
        compose.onNodeWithText("1080p").assertIsDisplayed()
        compose.onNodeWithText("4K").assertIsDisplayed()

        // Verify disabled reason for 4K
        compose.onNodeWithText("4K not available, best is 1080p").assertIsDisplayed()
    }

    @Test
    fun pickerDisplaysBelowPresetRealResolutionAnd4kNoteWhenEnabled() {
        val options = listOf(
            VideoSaveOption(
                presetName = "480p",
                resolutionLabel = "360p",
                realHeight = 360,
                height = 480,
                width = 640,
                vcodec = "h264",
                acodec = "aac",
                formatId = "18",
                estimatedSizeBytes = 5_000_000L,
                isDefault = false,
                isEnabled = true,
            ),
            VideoSaveOption(
                presetName = "1080p",
                resolutionLabel = "720p",
                realHeight = 720,
                height = 1080,
                width = 1280,
                vcodec = "h264",
                acodec = "aac",
                formatId = "136+140",
                estimatedSizeBytes = 18_000_000L,
                isDefault = true,
                isEnabled = true,
            ),
            VideoSaveOption(
                presetName = "4K",
                resolutionLabel = "4K",
                realHeight = 2160,
                height = 2160,
                width = 3840,
                vcodec = "vp9",
                acodec = "opus",
                formatId = "313+251",
                estimatedSizeBytes = 100_000_000L,
                isDefault = false,
                isEnabled = true,
                note = "Large file, may play poorly on older phones",
            ),
        )

        compose.setContent {
            ReconTheme {
                JobScreen(
                    job = sampleJob,
                    result = sampleResult,
                    now = 0L,
                    isProbingFormats = false,
                    saveOptions = options,
                    saveError = null,
                    onIntent = {},
                )
            }
        }

        // 1080p preset shows real resolution 720p because source is below preset
        compose.onNodeWithText("720p").assertIsDisplayed()
        compose.onNodeWithText("360p").assertIsDisplayed()

        // Click 4K option to select it
        compose.onNodeWithText("4K").performClick()

        // 4K note should now be displayed
        compose.onNodeWithText("Large file, may play poorly on older phones").assertIsDisplayed()
    }
}
