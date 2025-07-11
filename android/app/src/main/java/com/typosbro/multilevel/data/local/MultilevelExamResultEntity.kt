package com.typosbro.multilevel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.typosbro.multilevel.data.remote.models.FeedbackBreakdown
import com.typosbro.multilevel.data.remote.models.MultilevelExamResultResponse
import com.typosbro.multilevel.data.remote.models.TranscriptEntry

@Entity(tableName = "multilevel_exam_results")
data class MultilevelExamResultEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val totalScore: Int,
    val feedbackBreakdown: List<FeedbackBreakdown>,
    val transcript: List<TranscriptEntry>,
    val practicedPart: String,
    val createdAt: String
) {
    fun toResponse(): MultilevelExamResultResponse {
        return MultilevelExamResultResponse(
            id = this.id,
            userId = this.userId,
            totalScore = this.totalScore,
            feedbackBreakdown = this.feedbackBreakdown,
            transcript = this.transcript,
            createdAt = this.createdAt
        )
    }

    companion object {
        fun fromResponse(
            response: MultilevelExamResultResponse,
            practicedPart: String = "FULL"
        ): MultilevelExamResultEntity {
            return MultilevelExamResultEntity(
                id = response.id,
                userId = response.userId,
                totalScore = response.totalScore,
                feedbackBreakdown = response.feedbackBreakdown,
                transcript = response.transcript,
                practicedPart = practicedPart, // Determine this based on request or response
                createdAt = response.createdAt
            )
        }
    }
}