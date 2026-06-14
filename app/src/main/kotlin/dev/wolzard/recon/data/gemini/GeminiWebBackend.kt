package dev.wolzard.recon.data.gemini

import dev.wolzard.recon.core.json.ResultParser
import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.port.Analysis
import dev.wolzard.recon.core.port.LanguageModel
import dev.wolzard.recon.core.port.ModelAnswer
import dev.wolzard.recon.core.port.VideoAnalyzer
import dev.wolzard.recon.core.prompt.Prompts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The only model backend (D22): Gemini web with the user's signed-in account.
 * The web app picks the model and has no response schema, so the schema goes into the prompt text.
 */
class GeminiWebBackend(private val web: GeminiWebClient) : VideoAnalyzer, LanguageModel {

    // ponytail: YouTube links are downloaded like any other video; passing the URL to the web app is untested.
    override val supportsYouTubeUrl = false

    override suspend fun analyze(input: AnalysisInput, language: String): Analysis {
        val files = when (input) {
            is AnalysisInput.YouTubeUrl -> emptyList()
            is AnalysisInput.VideoFile -> listOf(input.file to input.mimeType)
            is AnalysisInput.FramesAndAudio -> input.frames.map { it.file to "image/jpeg" } + listOfNotNull(input.audio?.let { it to "audio/mp4" })
        }
        val uploaded = coroutineScope { files.map { (f, mime) -> async { web.upload(f, mime) to f.name } }.awaitAll() }
        val prompt = Prompts.analyze(language) + "\n\nJSON schema:\n" + Prompts.ANALYZE_SCHEMA +
            ((input as? AnalysisInput.YouTubeUrl)?.let { "\n\nVideo: ${it.url}" }.orEmpty())
        return Analysis(ResultParser.parseAnalysis(web.generate(prompt, uploaded)), MODEL)
    }

    /** [googleSearch] is ignored: the web app decides on its own when to search, and returns no sources here. */
    override suspend fun generate(prompt: String, schema: String?, googleSearch: Boolean): ModelAnswer {
        val text = web.generate(prompt + schema?.let { "\n\nRespond only with JSON matching this schema:\n$it" }.orEmpty())
        return ModelAnswer(text, sources = emptyList(), searchSuggestionsHtml = null, model = MODEL)
    }

    companion object { const val MODEL = "gemini-web" }
}
