package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.IeltsExamResultEntity
import com.typosbro.multilevel.data.local.MultilevelExamResultEntity
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.MultilevelExamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// An enum to identify the exam type in a type-safe way
enum class ExamType {
    IELTS,
    MULTILEVEL
}

// A unified data class to represent a summary for any exam type in the UI
// UPDATED: Added practicePart to help with filtering
data class GenericExamResultSummary(
    val id: String,
    val examDate: Long,
    val score: Double,
    val scoreLabel: String, // e.g., "Overall Band" or "Total Score"
    val type: ExamType,
    val practicePart: String = "FULL" // Default for IELTS or full exams
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: ExamType = ExamType.MULTILEVEL,
    val ieltsHistory: List<GenericExamResultSummary> = emptyList(),
    // UPDATED: multilevelHistory is now a map to group results by part
    val multilevelHistory: Map<String, List<GenericExamResultSummary>> = emptyMap(),
    // UPDATED: State to hold the selected sub-category for Multilevel
    val selectedMultilevelPart: String = "FULL"
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val ieltsRepository: ChatRepository, // Renamed for clarity
    private val multilevelExamRepository: MultilevelExamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    // A helper map to define max scores for the chart
    private val multilevelMaxScores = mapOf(
        "FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0
    )

    fun selectTab(type: ExamType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    // NEW function to handle sub-category selection
    fun selectMultilevelPart(part: String) {
        _uiState.update {
            it.copy(selectedMultilevelPart = part)
        }
    }

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                ieltsRepository.getLocalHistorySummary(),
                multilevelExamRepository.getLocalHistorySummary()
            ) { ieltsResults, multilevelResults ->
                // *** FIX: Call the renamed functions ***
                val ieltsHistory = ieltsResults.toIeltsGenericSummary()
                val multilevelHistoryMap =
                    multilevelResults.toMultilevelGenericSummary().groupBy { it.practicePart }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ieltsHistory = ieltsHistory,
                        multilevelHistory = multilevelHistoryMap,
                        // Ensure a valid part is selected, default to FULL if available
                        selectedMultilevelPart = if (multilevelHistoryMap.containsKey("FULL")) "FULL" else multilevelHistoryMap.keys.firstOrNull()
                            ?: "FULL"
                    )
                }
            }.collect { }
        }
    }
}

// *** FIX: Renamed function ***
private fun List<MultilevelExamResultEntity>.toMultilevelGenericSummary(): List<GenericExamResultSummary> {
    val multilevelMaxScores =
        mapOf("FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0)
    return this.map { summary ->
        val maxScore =
            multilevelMaxScores[summary.practicedPart] ?: summary.totalScore
        GenericExamResultSummary(
            id = summary.id,
            examDate = Instant.parse(summary.createdAt).toEpochMilli(),
            score = summary.totalScore.toDouble(),
            scoreLabel = "Score: ${summary.totalScore} / ${maxScore.toInt()}",
            type = ExamType.MULTILEVEL,
            practicePart = summary.practicedPart
        )
    }.sortedByDescending { it.examDate }
}

// *** FIX: Renamed function ***
private fun List<IeltsExamResultEntity>.toIeltsGenericSummary(): List<GenericExamResultSummary> {
    return this.map { summary ->
        GenericExamResultSummary(
            id = summary.id,
            examDate = summary.createdAt.toLong(),
            score = summary.overallBand,
            scoreLabel = "Overall Band: ${summary.overallBand}",
            type = ExamType.IELTS
        )
    }.sortedByDescending { it.examDate }
}