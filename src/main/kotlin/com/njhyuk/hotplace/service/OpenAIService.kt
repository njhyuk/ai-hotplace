package com.njhyuk.hotplace.service

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Service
class OpenAIService(
    @Value("\${openai.api.key}")
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    private val openAI = OpenAI(token = apiKey)

    suspend fun analyzeContent(content: String): ReviewAnalyze = withContext(Dispatchers.IO) {
        val messages = listOf(
            ChatMessage(
                role = ChatRole.System,
                content = """
                    당신은 리뷰 분석기 입니다. 제공받은 리뷰들을 종합하여 맛집여부를 판단해야 합니다.
                    아래의 답변 형식 외에는 하지 말아주세요.
                    {"storeName":"상점명","positives":["긍정적 의견1","긍정적 의견2"],"negatives":["부정적 의견1","부정적 의견2"],"summary":"음식 온도와 서비스, 식자재 다양성, 고객 응대 측면에서 여러 부정적 의견이 나타남. 전반적으로 맛집으로 판단하기 어렵다."}
                """.trimIndent()
            ),
            ChatMessage(
                role = ChatRole.User,
                content = content
            )
        )

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            messages = messages
        )

        val response = openAI.chatCompletion(chatCompletionRequest).choices.first().message.content!!
        return@withContext objectMapper.readValue(response, ReviewAnalyze::class.java)
    }
}

data class ReviewAnalyze(
    val storeName: String,
    val positives: List<String>,
    val negatives: List<String>,
    val summary: String,
)
