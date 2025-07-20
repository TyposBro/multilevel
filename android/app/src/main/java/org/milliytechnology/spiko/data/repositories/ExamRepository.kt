package org.milliytechnology.spiko.data.repositories

import kotlinx.coroutines.flow.Flow
import org.milliytechnology.spiko.data.local.ExamResultDao
import org.milliytechnology.spiko.data.local.ExamResultEntity
import org.milliytechnology.spiko.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.models.AnalyzeRequest
import org.milliytechnology.spiko.data.remote.models.MultilevelExamResponse
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.remote.models.safeApiCall
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
                // CORRECTLY save the result with the specific part that was practiced.
                examResultDao.insert(
                    ExamResultEntity.fromResponse(
                        response = resultResponse,
                        practicedPart = request.practicePart ?: "FULL"
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