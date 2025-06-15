// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ResultModels.kt
import com.google.gson.annotations.SerializedName
import com.typosbro.multilevel.data.repositories.Result
// Add this to data/remote/models/ResultModels.kt
data class ExamHistorySummaryResponse(
    @SerializedName("history") val history: List<ExamResultSummary>
)

data class ExamResultSummary(
    @SerializedName("id") val id: String,
    @SerializedName("exam_date") val examDate: Long,
    @SerializedName("overall_band") val overallBand: Double
)


// Add this to data/repositories/ChatRepository.kt
suspend fun getExamHistorySummary(): Result<ExamHistorySummaryResponse> {
    return try {
        // NOTE: You need to create a new endpoint, e.g., GET /api/exam/history
        // val response = apiService.getExamHistory()
        // Result.Success(response)

        // --- Mock data for now so the UI works ---
        Result.Success(ExamHistorySummaryResponse(history = listOf(
            ExamResultSummary("1", System.currentTimeMillis() - 86400000L * 5, 6.0),
            ExamResultSummary("2", System.currentTimeMillis() - 86400000L * 3, 6.5),
            ExamResultSummary("3", System.currentTimeMillis() - 86400000L * 1, 6.5),
        )))

    } catch (e: Exception) {
        Result.Error("Failed to fetch history: ${e.message}")
    }
}