package dev.wolzard.recon.core.port

import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Source

interface VideoAnalyzer {
    val supportsYouTubeUrl: Boolean
    /** One logical call (D9): ≤ 2 HTTP attempts across ranked models. @throws AnalyzerException */
    suspend fun analyze(input: AnalysisInput, language: String): Analysis
}

data class Analysis(val result: AnalysisResult, val model: String)   // model = the one that answered

interface LanguageModel {
    /**
     * @param schema JSON schema sent as responseSchema; null = none.
     * @throws AnalyzerException
     */
    suspend fun generate(prompt: String, schema: String?, googleSearch: Boolean): ModelAnswer
}

data class ModelAnswer(val text: String, val sources: List<Source>, val searchSuggestionsHtml: String?, val model: String)
