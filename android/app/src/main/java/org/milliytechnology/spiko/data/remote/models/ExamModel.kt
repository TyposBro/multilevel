// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/ExamModel.kt
package org.milliytechnology.spiko.data.remote.models

import com.google.gson.annotations.SerializedName


/**
 * Represents the Part 2 Cue Card topic and points.
 */
data class CueCard(
    @SerializedName("topic") val topic: String,
    @SerializedName("points") val points: List<String>
)


data class MultilevelExamResponse(
    @SerializedName("part1_1") val part1_1: List<Part1_1Question>,
    @SerializedName("part1_2") val part1_2: Part1_2Set,
    @SerializedName("part2") val part2: Part2Set,
    @SerializedName("part3") val part3: Part3Topic
)

data class Part1_1Question(
    @SerializedName("_id") val id: String,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("audioUrl") val audioUrl: String
)

data class Part1_2Set(
    @SerializedName("_id") val id: String,
    @SerializedName("image1Url") val image1Url: String,
    @SerializedName("image2Url") val image2Url: String,
    @SerializedName("questions") val questions: List<QuestionAudio>
)

data class Part2Set(
    @SerializedName("_id") val id: String,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("questions") val questions: List<QuestionAudio>
)

data class Part3Topic(
    @SerializedName("_id") val id: String,
    @SerializedName("topic") val topic: String,
    @SerializedName("forPoints") val forPoints: List<String>,
    @SerializedName("againstPoints") val againstPoints: List<String>
)

data class QuestionAudio(
    @SerializedName("text") val text: String,
    @SerializedName("audioUrl") val audioUrl: String
)

/**
 * Defines the request body for the v2 analysis endpoint.
 * It's simpler than the v1 request, omitting the large 'examContent' object.
 */
data class AnalyzeRequest(
    val transcript: List<TranscriptEntry>,
    val practicePart: String?,
    val language: String? = "en" // Add language, default to English
)

/**
 * Represents the full response from the v2 analysis API.
 * This structure is saved locally in the Room database.
 */
data class ExamResultResponse(
    @SerializedName("_id") val id: String,
    val userId: String,
    val totalScore: Int,
    val feedbackBreakdown: List<FeedbackBreakdown>,
    val transcript: List<TranscriptEntry>,
    val createdAt: String,
    val language: String? = null // Field from v2 response
)

/**
 * Represents the feedback for a single part of the exam.
 * It's designed to be backward-compatible with v1 results.
 */
data class FeedbackBreakdown(
    val part: String,
    val score: Int,
    // v2 fields:
    val overallFeedback: String?,
    val detailedBreakdown: DetailedBreakdown?,
    // v1 field (kept for backward compatibility):
    val feedback: String?
)

/**
 * Contains the detailed, criteria-based feedback from the v2 API.
 */
data class DetailedBreakdown(
    val fluencyAndCoherence: FeedbackCriterion,
    val lexicalResource: FeedbackCriterion,
    val grammaticalRangeAndAccuracy: FeedbackCriterion,
    val taskAchievement: FeedbackCriterion
)

/**
 * A single feedback criterion, including positive remarks, suggestions, and an example.
 */
data class FeedbackCriterion(
    val positive: String,
    val suggestion: String,
    val example: String
)

/**
 * Represents a single entry in the conversation transcript.
 */
data class TranscriptEntry(
    val speaker: String,
    val text: String
)

