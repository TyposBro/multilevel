// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/ResultModel.kt

package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

data class ExamHistorySummaryResponse(
    @SerializedName("history") val history: List<ExamResultSummary>
)



/**
 * A summary of a single past exam result, used in the ProgressScreen list.
 */
data class ExamResultSummary(
    @SerializedName("id") val id: String,
    @SerializedName("examDate") val examDate: Long,
    @SerializedName("overallBand") val overallBand: Double
)

/**
 * The full, detailed response for a single exam result.
 * This is the primary model for the Exam Result Details screen.
 */
data class ExamResultResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("overallBand") val overallBand: Double,
    @SerializedName("criteria") val criteria: List<Criterion>,
    @SerializedName("transcript") val transcript: List<TranscriptEntry>,
    @SerializedName("createdAt") val createdAt: String
)

/**
 * Represents the feedback for one of the four IELTS criteria.
 */
data class Criterion(
    @SerializedName("criterionName") val criterionName: String,
    @SerializedName("bandScore") val bandScore: Double,
    @SerializedName("feedback") val feedback: String,
    @SerializedName("examples") val examples: List<FeedbackExample>? // Nullable for safety
)

/**
 * Represents a specific example of feedback, with a user quote and a suggestion.
 */
data class FeedbackExample(
    @SerializedName("userQuote") val userQuote: String,
    @SerializedName("suggestion") val suggestion: String,
    @SerializedName("type") val type: String // e.g., "Fluency", "Vocabulary"
)