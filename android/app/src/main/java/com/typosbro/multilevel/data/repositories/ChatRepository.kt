// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.AnalyzeExamRequest
import com.typosbro.multilevel.data.remote.models.AnalyzeExamResponse
import com.typosbro.multilevel.data.remote.models.ExamHistorySummaryResponse
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getInitialExamQuestion(): RepositoryResult<ExamStepResponse> =
        safeApiCall { apiService.startExam() }

    suspend fun getNextExamStep(request: ExamStepRequest): RepositoryResult<ExamStepResponse> {
        return safeApiCall { apiService.getNextExamStep(request) }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): RepositoryResult<AnalyzeExamResponse> {
        val request = AnalyzeExamRequest(transcript)
        return safeApiCall { apiService.analyzeExam(request) }
    }

    suspend fun getExamHistorySummary(): RepositoryResult<ExamHistorySummaryResponse> =
        safeApiCall { apiService.getExamHistory() }

    suspend fun getExamResultDetails(resultId: String): RepositoryResult<ExamResultResponse> =
        safeApiCall { apiService.getExamResult(resultId) }
}