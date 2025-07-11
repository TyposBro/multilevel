// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.local.IeltsExamResultDao
import com.typosbro.multilevel.data.local.IeltsExamResultEntity
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.AnalyzeExamRequest
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService,
    private val ieltsExamResultDao: IeltsExamResultDao
) {

    suspend fun getInitialExamQuestion(): RepositoryResult<ExamStepResponse> =
        safeApiCall { apiService.startExam() }

    suspend fun getNextExamStep(request: ExamStepRequest): RepositoryResult<ExamStepResponse> {
        return safeApiCall { apiService.getNextExamStep(request) }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): RepositoryResult<String> {
        val request = AnalyzeExamRequest(transcript)
        return when (val apiResult = safeApiCall { apiService.analyzeExam(request) }) {
            is RepositoryResult.Success -> {
                val resultResponse = apiResult.data
                ieltsExamResultDao.insert(IeltsExamResultEntity.fromResponse(resultResponse))
                RepositoryResult.Success(resultResponse.id)
            }

            is RepositoryResult.Error -> {
                apiResult
            }
        }
    }

    fun getLocalHistorySummary(): Flow<List<IeltsExamResultEntity>> {
        return ieltsExamResultDao.getHistorySummary()
    }

    fun getLocalResultDetails(resultId: String): Flow<IeltsExamResultEntity?> =
        ieltsExamResultDao.getResultById(resultId)
}