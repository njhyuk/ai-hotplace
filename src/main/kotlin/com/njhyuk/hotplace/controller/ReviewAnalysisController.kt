package com.njhyuk.hotplace.controller

import com.njhyuk.hotplace.service.WebScraperService
import com.njhyuk.hotplace.service.OpenAIService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import com.fasterxml.jackson.databind.ObjectMapper

@RestController
class ReviewAnalysisController(
    private val webScraperService: WebScraperService,
    private val openAIService: OpenAIService,
    private val objectMapper: ObjectMapper
) {
    @PostMapping("/api/reviews/analyze")
    fun analyzeReview(@RequestBody request: AnalyzeReviewRequest): SseEmitter {
        val emitter = SseEmitter()
        
        CoroutineScope(Dispatchers.IO).async {
            try {
                val scrapedContent = withContext(Dispatchers.IO) {
                    webScraperService.scrapeWebPage(request.url)
                }
                
                val response = withContext(Dispatchers.IO) {
                    openAIService.analyzeContent(scrapedContent)
                }

                val result = AnalyzeReviewResponse(
                    storeName = response.storeName,
                    positives = response.positives,
                    negatives = response.negatives,
                    summary = response.summary,
                )
                
                emitter.send(SseEmitter.event()
                    .name("result")
                    .data(objectMapper.writeValueAsString(result)))
                emitter.complete()
            } catch (e: Exception) {
                try {
                    val errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다."
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(mapOf("message" to errorMessage))))
                    emitter.complete()
                } catch (ex: IOException) {
                    emitter.completeWithError(ex)
                }
            }
        }

        return emitter
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
