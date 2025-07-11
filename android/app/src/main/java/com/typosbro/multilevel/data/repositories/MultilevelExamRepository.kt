// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/MultilevelExamRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.local.MultilevelExamResultDao
import com.typosbro.multilevel.data.local.MultilevelExamResultEntity
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.MultilevelAnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultilevelExamRepository @Inject constructor(
    private val apiService: ApiService,
    private val multilevelExamResultDao: MultilevelExamResultDao
) {
    suspend fun getNewExam(): RepositoryResult<MultilevelExamResponse> =
        safeApiCall { apiService.getNewMultilevelExam() }

    suspend fun analyzeExam(request: MultilevelAnalyzeRequest): RepositoryResult<String> {
        return when (val apiResult = safeApiCall { apiService.analyzeMultilevelExam(request) }) {
            is RepositoryResult.Success -> {
                val resultResponse = apiResult.data
                multilevelExamResultDao.insert(
                    MultilevelExamResultEntity.fromResponse(
                        resultResponse
                    )
                )
                RepositoryResult.Success(resultResponse.id)
            }

            is RepositoryResult.Error -> apiResult
        }
    }


    fun getLocalHistorySummary(): Flow<List<MultilevelExamResultEntity>> =
        multilevelExamResultDao.getHistorySummary()

    fun getLocalResultDetails(examId: String): Flow<MultilevelExamResultEntity?> =
        multilevelExamResultDao.getResultById(examId)
}