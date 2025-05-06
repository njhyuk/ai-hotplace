package com.njhyuk.hotplace.controller

import com.njhyuk.hotplace.service.WebScraperService
import com.njhyuk.hotplace.service.OpenAIService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewAnalysisController(
    private val webScraperService: WebScraperService,
    private val openAIService: OpenAIService
) {
    @PostMapping("/api/reviews/analyze")
    fun analyzeReview(@RequestBody request: AnalyzeReviewRequest): AnalyzeReviewResponse {
        val scrapedContent = webScraperService.scrapeWebPage(request.url)
        val response = openAIService.analyzeContent(scrapedContent)

        return AnalyzeReviewResponse(
            storeName = response.storeName,
            positives = response.positives,
            negatives = response.negatives,
            summary = response.summary,
        )
    }
}

data class AnalyzeReviewRequest(
    val url: String,
)

data class AnalyzeReviewResponse(
    val storeName: String,
    val positives: List<String>,
    val negatives: List<String>,
    val summary: String,
)
