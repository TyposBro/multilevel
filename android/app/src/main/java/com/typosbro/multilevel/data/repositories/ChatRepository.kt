package com.typosbro.multilevel.data.repositories

import ExamHistorySummaryResponse
import ExamResultSummary
import android.util.Log
import com.google.gson.Gson
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.RetrofitClient // For BASE_URL_FOR_SSE
import com.typosbro.multilevel.data.remote.models.AnalyzeExamRequest
import com.typosbro.multilevel.data.remote.models.AnalyzeExamResponse
import com.typosbro.multilevel.data.remote.models.ChatHistoryResponse
import com.typosbro.multilevel.data.remote.models.ChatListResponse
import com.typosbro.multilevel.data.remote.models.CreateChatRequest
import com.typosbro.multilevel.data.remote.models.CreateChatResponse
import com.typosbro.multilevel.data.remote.models.DeleteChatResponse
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.SendMessageRequest // Ensure this is updated if sending lang_code/config_key
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.remote.models.UpdateTitleRequest
import com.typosbro.multilevel.data.remote.models.UpdateTitleResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest // Aliasing to avoid conflict with API models if any
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse // Aliasing
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.IOException // OkHttp Callback can throw this

// Sealed class for stream events (ensure this is defined, e.g., in this file or a separate one)
sealed class ChatStreamEvent {
    data class TextChunk(val text: String) : ChatStreamEvent()
    data class InputIdsChunk(val sentence: String, val ids: List<Int>) : ChatStreamEvent()
    data class PreprocessWarning(val message: String, val sentenceText: String?) : ChatStreamEvent()
    data class PreprocessError(val message: String, val sentenceText: String?) : ChatStreamEvent()
    data class StreamError(val message: String, val details: String?) : ChatStreamEvent()
    data class StreamEnd(val chatId: String?, val message: String?) : ChatStreamEvent()
}

class ChatRepository(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient, // Injected OkHttpClient
    private val tokenProvider: suspend () -> String? // Lambda to get the auth token
) {
    private val gson = Gson()

    // --- Standard Non-Streaming CRUD operations ---

    suspend fun getChatList(): Result<ChatListResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.listChats() }
    }

    suspend fun createNewChat(title: String? = null): Result<CreateChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.createChat(CreateChatRequest(title)) }
    }

    suspend fun deleteChat(chatId: String): Result<DeleteChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.deleteChat(chatId) }
    }

    suspend fun updateChatTitle(chatId: String, newTitle: String): Result<UpdateTitleResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.updateChatTitle(chatId, UpdateTitleRequest(newTitle)) }
    }

    suspend fun getChatHistory(chatId: String): Result<ChatHistoryResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getChatHistory(chatId) }
    }
    // --- NEW METHOD TO FIX THE ERROR ---
    suspend fun getExamHistorySummary(): Result<ExamHistorySummaryResponse> {
        return try {
            // In a real app, you would uncomment this line:
            // val response = apiService.getExamHistory()
            // Result.Success(response)

            // For now, return mock data so the UI can be built and tested.
            // This is the same mock data from the ProgressViewModel, but it belongs here.
            val mockHistory = listOf(
                ExamResultSummary("id_1", System.currentTimeMillis() - 86400000L * 5, 6.0),
                ExamResultSummary("id_2", System.currentTimeMillis() - 86400000L * 3, 6.5),
                ExamResultSummary("id_3", System.currentTimeMillis() - 86400000L * 1, 6.5),
            )
            val mockResponse = ExamHistorySummaryResponse(history = mockHistory)
            Result.Success(mockResponse)

        } catch (e: Exception) {
            Result.Error("Failed to fetch exam history: ${e.message}")
        }
    }

    // --- METHOD FOR THE RESULT DETAIL SCREEN ---
    suspend fun getExamResultDetails(resultId: String): Result<ExamResultResponse> {
        return try {
            // In a real app, you would uncomment this line:
            // val response = apiService.getExamResult(resultId)
            // Result.Success(response)

            // For now, return mock data.
            Result.Error("Result details not implemented yet.")

        } catch (e: Exception) {
            Result.Error("Failed to fetch result details for ID $resultId: ${e.message}")
        }
    }
    // --- Streaming message method ---

    fun sendMessageAndStream(
        chatId: String,
        prompt: String,
        langCode: String? = null,
        configKey: String? = null
    ): Flow<ChatStreamEvent> = callbackFlow {
        // Create the request body for the POST request
        // Ensure SendMessageRequest can handle these optional parameters
        val sendMessagePayload = SendMessageRequest(
            prompt = prompt,
            lang_code = langCode,
            config_key = configKey
        )
        val requestBodyJson = gson.toJson(sendMessagePayload)
        val requestBody = requestBodyJson.toRequestBody(MEDIA_TYPE_JSON)

        val currentToken = tokenProvider()
        if (currentToken == null) {
            trySend(ChatStreamEvent.StreamError("Authentication token not available.", null))
            close(IllegalStateException("Auth token missing")) // Close with an exception
            return@callbackFlow
        }

        val url = "${RetrofitClient.BASE_URL}chat/${chatId}/message"
        val request = OkHttpRequest.Builder()
            .url(url)
            .header("Authorization", "Bearer $currentToken")
            .header("Accept", "text/event-stream") // Crucial for SSE
            .header("Cache-Control", "no-cache")   // Crucial for SSE
            .post(requestBody)
            .build()

        Log.d("ChatRepository", "Starting SSE request to: $url")

        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: OkHttpResponse) {
                Log.d("ChatRepositorySSE", "SSE Connection opened. Response: ${response.code}")
                if (!response.isSuccessful) {
                    // Handle non-2xx responses that might occur before stream starts
                    val errorBody = response.body?.string() ?: "Unknown server error"
                    Log.e("ChatRepositorySSE", "SSE Connection opened with error: ${response.code} - $errorBody")
                    trySend(ChatStreamEvent.StreamError("Server error on connection: ${response.code}", errorBody))
                    close(IOException("Server returned error code ${response.code} on SSE open"))
                }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d("ChatRepositorySSE", "SSE Event: type=$type, data=$data")
                try {
                    when (type) {
                        "text_chunk" -> {
                            val json = JSONObject(data)
                            trySend(ChatStreamEvent.TextChunk(json.getString("text")))
                        }
                        "input_ids_chunk" -> {
                            val json = JSONObject(data)
                            val sentenceText = json.getString("sentence")
                            val idsArray = json.getJSONArray("input_ids")
                            val idsList = List(idsArray.length()) { i -> idsArray.getInt(i) }
                            trySend(ChatStreamEvent.InputIdsChunk(sentenceText, idsList))
                        }
                        "preprocess_warning" -> {
                            val json = JSONObject(data)
                            trySend(ChatStreamEvent.PreprocessWarning(json.getString("message"), json.optString("sentenceText")))
                        }
                        "preprocess_error" -> {
                            val json = JSONObject(data)
                            trySend(ChatStreamEvent.PreprocessError(json.getString("message"), json.optString("sentenceText")))
                        }
                        "stream_end" -> {
                            val json = JSONObject(data)
                            trySend(ChatStreamEvent.StreamEnd(json.optString("chatId"), json.optString("message")))
                            close() // Normal stream completion, close the flow
                        }
                        "error" -> { // Backend explicitly sends an error event within the stream
                            val json = JSONObject(data)
                            trySend(ChatStreamEvent.StreamError(json.getString("message"), json.optString("details")))
                            close(IOException("Server sent error event: ${json.getString("message")}")) // Close flow on server-sent error
                        }
                        null -> { // This can happen for comment lines or empty keep-alive pings
                            Log.d("ChatRepositorySSE", "Received event with null type (likely keep-alive or comment)")
                        }
                        else -> {
                            Log.w("ChatRepositorySSE", "Unknown SSE event type: $type")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepositorySSE", "Error parsing SSE data: $data for type: $type", e)
                    // Don't close the stream for a single bad event, but report it
                    trySend(ChatStreamEvent.StreamError("Client parsing error for an event.", e.message))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("ChatRepositorySSE", "SSE Connection explicitly closed by server")
                close() // Close the flow
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: OkHttpResponse?) {
                val errorMsg = t?.message ?: "Unknown SSE failure"
                Log.e("ChatRepositorySSE", "SSE Connection failure: $errorMsg. Response code: ${response?.code}", t)
                trySend(ChatStreamEvent.StreamError("Connection failure: $errorMsg", response?.message))
                close(t ?: IOException("Unknown SSE failure")) // Close the flow with the error
            }
        }

        // Use the OkHttpClient instance passed to the repository
        // Ensure EventSourceFactory is configured with appropriate read timeouts for SSE
        val eventSourceFactory = EventSources.createFactory(okHttpClient)
        val eventSource = eventSourceFactory.newEventSource(request = request, listener = eventSourceListener)

        // Ensure the EventSource is cancelled when the flow is cancelled/collector stops
        awaitClose {
            Log.d("ChatRepositorySSE", "SSE Flow closing/collector stopped, cancelling EventSource.")
            eventSource.cancel()
        }
    }

    // --- Helper for standard API calls (SafeApiCall) ---
    // Keep your existing safeApiCall implementation or use one like this:
    private suspend fun <T : Any> safeApiCall(call: suspend () -> retrofit2.Response<T>): Result<T> {
        return try {
            val response = call.invoke()
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.Success(it)
                } ?: Result.Error("Response body is null", response.code())
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("SafeApiCall", "API Error: ${response.code()} - $errorBody")
                // Try to parse a structured error if your backend sends one
                try {
                    val errorResponse = gson.fromJson(errorBody, com.typosbro.multilevel.data.remote.models.ErrorResponse::class.java)
                    Result.Error(errorResponse.message, response.code())
                } catch (e: Exception) {
                    Result.Error(errorBody, response.code())
                }
            }
        } catch (e: Exception) {
            Log.e("SafeApiCall", "Network/Conversion Error: ${e.message}", e)
            Result.Error(e.message ?: "Network error", null)
        }
    }

    suspend fun getInitialExamQuestion(): Result<ExamStepResponse> {
        return try {
            // NOTE: You need to create a new endpoint, e.g., POST /api/exam/start
            val response = apiService.startExam() // Assuming you create this in ApiService
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error("Failed to start exam: ${e.message}")
        }
    }

    suspend fun getNextExamStep(request: ExamStepRequest): Result<ExamStepResponse> {
        return try {
            // NOTE: You need to create a new endpoint, e.g., POST /api/exam/step
            val response = apiService.postExamStep(request) // Assuming you create this in ApiService
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error("Failed to get next exam step: ${e.message}")
        }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): Result<AnalyzeExamResponse> {
        return try {
            val request = AnalyzeExamRequest(transcript = transcript)
            // NOTE: You need to create a new endpoint, e.g., POST /api/exam/analyze
            val response = apiService.analyzeExam(request)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error("Failed to analyze exam: ${e.message}")
        }
    }

    companion object {
        // Define common MediaType (consider moving to a constants file)
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}

