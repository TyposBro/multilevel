package com.typosbro.multilevel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.typosbro.multilevel.data.remote.models.Criterion
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.remote.models.TranscriptEntry

@Entity(tableName = "ielts_exam_results")
data class IeltsExamResultEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val overallBand: Double,
    val criteria: List<Criterion>,
    val transcript: List<TranscriptEntry>,
    val createdAt: String
) {
    fun toResponse(): ExamResultResponse {
        return ExamResultResponse(
            id = this.id,
            userId = this.userId,
            overallBand = this.overallBand,
            criteria = this.criteria,
            transcript = this.transcript,
            createdAt = this.createdAt
        )
    }

    companion object {
        fun fromResponse(response: ExamResultResponse): IeltsExamResultEntity {
            return IeltsExamResultEntity(
                id = response.id,
                userId = response.userId,
                overallBand = response.overallBand,
                criteria = response.criteria,
                transcript = response.transcript,
                createdAt = response.createdAt
            )
        }
    }
}