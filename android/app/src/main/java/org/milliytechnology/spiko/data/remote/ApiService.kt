package org.milliytechnology.spiko.data.remote

import org.milliytechnology.spiko.data.remote.models.AnalyzeRequest
import org.milliytechnology.spiko.data.remote.models.ApiWord
import org.milliytechnology.spiko.data.remote.models.AuthResponse
import org.milliytechnology.spiko.data.remote.models.CreatePaymentRequest
import org.milliytechnology.spiko.data.remote.models.CreatePaymentResponse
import org.milliytechnology.spiko.data.remote.models.ExamResultResponse
import org.milliytechnology.spiko.data.remote.models.GenericSuccessResponse
import org.milliytechnology.spiko.data.remote.models.GoogleSignInRequest
import org.milliytechnology.spiko.data.remote.models.MultilevelExamResponse
import org.milliytechnology.spiko.data.remote.models.OneTimeTokenRequest
import org.milliytechnology.spiko.data.remote.models.SubscriptionResponse
import org.milliytechnology.spiko.data.remote.models.UserProfileResponse
import org.milliytechnology.spiko.data.remote.models.VerifyPurchaseRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
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

    @GET("exam/multilevel/new")
    suspend fun getNewMultilevelExam(): Response<MultilevelExamResponse>

    // --- MODIFIED: Point to the new V2 endpoint ---
    @POST("exam/multilevel/v2/analyze")
    suspend fun analyzeMultilevelExam(@Body request: AnalyzeRequest): Response<ExamResultResponse>


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