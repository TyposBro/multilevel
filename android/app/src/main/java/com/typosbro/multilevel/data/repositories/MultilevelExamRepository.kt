// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/MultilevelExamRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.AnalyzeExamResponse
import com.typosbro.multilevel.data.remote.models.MultilevelAnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamHistorySummaryResponse
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.MultilevelExamResultResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultilevelExamRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getNewExam(): RepositoryResult<MultilevelExamResponse> =
        safeApiCall { apiService.getNewMultilevelExam() }

    suspend fun analyzeExam(request: MultilevelAnalyzeRequest): RepositoryResult<AnalyzeExamResponse> =
        safeApiCall { apiService.analyzeMultilevelExam(request) }


    // You would also add history and result details functions here
    suspend fun getExamHistory(): RepositoryResult<MultilevelExamHistorySummaryResponse> =
        safeApiCall { apiService.getMultilevelExamHistory() }

    suspend fun getExamResultDetails(examId: String): RepositoryResult<MultilevelExamResultResponse> =
        safeApiCall { apiService.getMultilevelExamResult(examId) }
}