// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/MultilevelExamModel.kt
package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

// --- Data models for the entire exam package ---

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

data class MultilevelAnalyzeRequest(
    val transcript: List<TranscriptEntry>, // Can reuse from IELTS models
    val examContentIds: ExamContentIds,
    val practicePart: String? = null
)

data class ExamContentIds(
    @SerializedName("part1_1") val part1_1: List<String>,
    @SerializedName("part1_2") val part1_2: String,
    @SerializedName("part2") val part2: String,
    @SerializedName("part3") val part3: String
)

// --- Models for displaying the multilevel results ---

data class MultilevelExamResultResponse(
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

data class MultilevelExamHistorySummaryResponse(
    val history: List<MultilevelExamResultSummary>
)

data class MultilevelExamResultSummary(
    val id: String,
    val examDate: Long,
    val totalScore: Int
)