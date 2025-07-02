// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/ApiService.kt
package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.AnalyzeExamRequest
import com.typosbro.multilevel.data.remote.models.AnalyzeExamResponse
import com.typosbro.multilevel.data.remote.models.ApiWord
import com.typosbro.multilevel.data.remote.models.AuthResponse
import com.typosbro.multilevel.data.remote.models.CreatePaymentRequest
import com.typosbro.multilevel.data.remote.models.CreatePaymentResponse
import com.typosbro.multilevel.data.remote.models.ExamHistorySummaryResponse
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.GenericSuccessResponse
import com.typosbro.multilevel.data.remote.models.GoogleSignInRequest
import com.typosbro.multilevel.data.remote.models.MultilevelAnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamHistorySummaryResponse
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.MultilevelExamResultResponse
import com.typosbro.multilevel.data.remote.models.OneTimeTokenRequest
import com.typosbro.multilevel.data.remote.models.SubscriptionResponse
import com.typosbro.multilevel.data.remote.models.UserProfileResponse
import com.typosbro.multilevel.data.remote.models.VerifyPurchaseRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {

    // --- Social Auth Endpoints ---
    @POST("auth/google-signin")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): Response<AuthResponse>

    // NEW: Endpoint for verifying the deep link token
    @POST("auth/verify-telegram-token")
    suspend fun verifyTelegramToken(@Body request: OneTimeTokenRequest): Response<AuthResponse>

    // You would add one for Apple here too
    // @POST("auth/apple-signin")
    // suspend fun appleSignIn(@Body request: AppleSignInRequest): Response<AuthResponse>


    @GET("auth/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @DELETE("auth/profile")
    suspend fun deleteProfile(): Response<GenericSuccessResponse>

    // --- Structured Exam Endpoints ---
    @POST("exam/ielts/start")
    suspend fun startExam(): Response<ExamStepResponse>

    @POST("exam/ielts/step")
    suspend fun getNextExamStep(@Body request: ExamStepRequest): Response<ExamStepResponse>

    @POST("exam/ielts/analyze")
    suspend fun analyzeExam(@Body request: AnalyzeExamRequest): Response<AnalyzeExamResponse>

    @GET("exam/ielts/history")
    suspend fun getExamHistory(): Response<ExamHistorySummaryResponse>

    @GET("exam/ielts/result/{resultId}")
    suspend fun getExamResult(@Path("resultId") resultId: String): Response<ExamResultResponse>

    // --- NEW: Multilevel Exam Endpoints ---
    @GET("exam/multilevel/new")
    suspend fun getNewMultilevelExam(): Response<MultilevelExamResponse>

    @POST("exam/multilevel/analyze")
    suspend fun analyzeMultilevelExam(@Body request: MultilevelAnalyzeRequest): Response<AnalyzeExamResponse> // Response can be reused

    @GET("exam/multilevel/history")
    suspend fun getMultilevelExamHistory(): Response<MultilevelExamHistorySummaryResponse>

    @GET("exam/multilevel/result/{resultId}")
    suspend fun getMultilevelExamResult(@Path("resultId") resultId: String): Response<MultilevelExamResultResponse>


    //    NEW: WORDBANK Endpoints
    @GET("wordbank/levels")
    suspend fun getWordLevels(): Response<List<String>>

    @GET("wordbank/topics")
    suspend fun getWordTopics(@Query("level") level: String): Response<List<String>>

    @GET("wordbank/words")
    suspend fun getWords(
        @Query("level") level: String,
        @Query("topic") topic: String
    ): Response<List<ApiWord>>

    @POST("subscriptions/verify-purchase")
    suspend fun verifyPurchase(@Body request: VerifyPurchaseRequest): Response<SubscriptionResponse>

    @POST("payment/create")
    suspend fun createPayment(@Body request: CreatePaymentRequest): Response<CreatePaymentResponse>
}