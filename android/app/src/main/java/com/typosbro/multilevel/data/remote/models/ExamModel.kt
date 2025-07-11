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
 * The request body sent to the server to get the next examiner question/prompt.
 */
data class ExamStepRequest(
    @SerializedName("part") val part: Int,
    @SerializedName("userInput") val userInput: String?,
    @SerializedName("transcriptContext") val transcriptContext: String,
    @SerializedName("questionCountInPart") val questionCountInPart: Int
)

/**
 * The response from the server for each step of the exam.
 */
data class ExamStepResponse(
    @SerializedName("examiner_line") val examinerLine: String,
    @SerializedName("next_part") val nextPart: Int, // The part we are now in (server is source of truth)
    @SerializedName("cue_card") val cueCard: CueCard?, // Nullable, only present for Part 2
    @SerializedName("is_final_question") val isFinalQuestion: Boolean = false,
    @SerializedName("input_ids") val inputIds: List<Int>? // For client-side TTS
)

/**
 * Represents the Part 2 Cue Card topic and points.
 */
data class CueCard(
    @SerializedName("topic") val topic: String,
    @SerializedName("points") val points: List<String>
)

/**
 * The request body sent to the server to begin the final analysis.
 * Contains the complete, structured transcript.
 */
data class AnalyzeExamRequest(
    @SerializedName("transcript") val transcript: List<TranscriptEntry>
)