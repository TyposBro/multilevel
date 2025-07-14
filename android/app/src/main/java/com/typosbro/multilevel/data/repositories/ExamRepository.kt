// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/MultilevelExamRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.local.ExamResultDao
import com.typosbro.multilevel.data.local.ExamResultEntity
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.AnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val apiService: ApiService,
    private val examResultDao: ExamResultDao
) {
    suspend fun getNewExam(): RepositoryResult<MultilevelExamResponse> =
        safeApiCall { apiService.getNewMultilevelExam() }

    suspend fun analyzeExam(request: AnalyzeRequest): RepositoryResult<String> {
        return when (val apiResult = safeApiCall { apiService.analyzeMultilevelExam(request) }) {
            is RepositoryResult.Success -> {
                val resultResponse = apiResult.data
                examResultDao.insert(
                    ExamResultEntity.fromResponse(
                        resultResponse
                    )
                )
                RepositoryResult.Success(resultResponse.id)
            }

            is RepositoryResult.Error -> apiResult
        }
    }


    fun getLocalHistorySummary(): Flow<List<ExamResultEntity>> =
        examResultDao.getHistorySummary()

    fun getLocalResultDetails(examId: String): Flow<ExamResultEntity?> =
        examResultDao.getResultById(examId)
}