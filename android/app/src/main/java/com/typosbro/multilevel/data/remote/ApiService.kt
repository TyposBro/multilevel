// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/ApiService.kt
package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.AnalyzeExamRequest
import com.typosbro.multilevel.data.remote.models.AnalyzeExamResponse
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.remote.models.AuthResponse
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
import com.typosbro.multilevel.data.remote.models.UserProfileResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


interface ApiService {

    // --- NEW: Social Auth Endpoints ---
    @POST("auth/google-signin")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): Response<AuthResponse>

    // You would add one for Apple here too
    // @POST("auth/apple-signin")
    // suspend fun appleSignIn(@Body request: AppleSignInRequest): Response<AuthResponse>

    // --- Auth Endpoints ---
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("auth/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @DELETE("auth/profile")
    suspend fun deleteProfile(): Response<GenericSuccessResponse>

    // --- Structured Exam Endpoints ---
    @POST("exam/start")
    suspend fun startExam(): Response<ExamStepResponse>

    @POST("exam/step")
    suspend fun getNextExamStep(@Body request: ExamStepRequest): Response<ExamStepResponse>

    @POST("exam/analyze")
    suspend fun analyzeExam(@Body request: AnalyzeExamRequest): Response<AnalyzeExamResponse>

    @GET("exam/history")
    suspend fun getExamHistory(): Response<ExamHistorySummaryResponse>

    @GET("exam/result/{resultId}")
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
}