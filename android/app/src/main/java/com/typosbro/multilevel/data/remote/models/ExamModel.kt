// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/ExamModel.kt
package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a single entry in the conversation transcript.
 * Used for sending the full transcript for final analysis.
 */
data class TranscriptEntry(
    @SerializedName("speaker") val speaker: String, // "Examiner" or "User"
    @SerializedName("text") val text: String
)

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

// --- Models for sending the final analysis request ---

data class AnalyzeRequest(
    val transcript: List<TranscriptEntry>, // Can reuse from IELTS models
    val examContent: MultilevelExamResponse?, // Send the full content for context
    val practicePart: String? = null // e.g., "FULL", "P1_1"
)

// --- Models for displaying the multilevel results ---

data class ExamResultResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("totalScore") val totalScore: Int,
    @SerializedName("feedbackBreakdown") val feedbackBreakdown: List<FeedbackBreakdown>,
    @SerializedName("transcript") val transcript: List<TranscriptEntry>,
    @SerializedName("createdAt") val createdAt: String
)

data class FeedbackBreakdown(
    @SerializedName("part") val part: String,
    @SerializedName("score") val score: Int,
    @SerializedName("feedback") val feedback: String
)