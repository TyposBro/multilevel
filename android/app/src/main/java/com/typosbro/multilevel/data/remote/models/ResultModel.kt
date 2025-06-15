// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/ResultModel.kt

package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * The full, detailed analysis of a completed exam, fetched by its ID.
 */
data class ExamResultResponse(
    @SerializedName("id") val id: String, // The result_id
    @SerializedName("exam_date") val examDate: Long, // Timestamp of the exam
    @SerializedName("overall_band") val overallBand: Double,
    @SerializedName("criteria") val criteria: List<ScoreCriterion>,
    @SerializedName("transcript") val transcript: List<TranscriptEntry> // The full transcript for review
)

/**
 * Represents the score and feedback for one of the four IELTS criteria.
 */
data class ScoreCriterion(
    @SerializedName("criterion_name") val criterionName: String, // e.g., "Fluency & Coherence"
    @SerializedName("band_score") val bandScore: Double,
    @SerializedName("feedback") val feedback: String, // General feedback for this criterion
    @SerializedName("examples") val examples: List<FeedbackExample>? // Specific examples from user's speech
)

/**
 * A specific example highlighted by the LLM, showing an area for improvement.
 */
data class FeedbackExample(
    @SerializedName("user_quote") val userQuote: String, // The exact thing the user said
    @SerializedName("suggestion") val suggestion: String, // How it could be improved
    @SerializedName("type") val type: String // e.g., "Grammar", "Vocabulary", "Filler Word"
)