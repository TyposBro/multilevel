package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.MultilevelExamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val uiState = _uiState.asStateFlow()

    // A helper map to define max scores for the chart
    private val multilevelMaxScores = mapOf(
        "FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0
    )

    init {
        loadAllHistories()
    }

    fun selectTab(type: ExamType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    // NEW function to handle sub-category selection
    fun selectMultilevelPart(part: String) {
        _uiState.update { it.copy(selectedMultilevelPart = part) }
    }


    private fun loadAllHistories() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Fetch both histories in parallel for better performance
            // CORRECTED variable names for clarity
            val multilevelDeferred = async { multilevelExamRepository.getExamHistory() }
            val ieltsDeferred = async { ieltsRepository.getExamHistorySummary() }

            val multilevelResult = multilevelDeferred.await()
            val ieltsResult = ieltsDeferred.await()

            var ieltsHistory: List<GenericExamResultSummary> = emptyList()
            var multilevelHistoryMap: Map<String, List<GenericExamResultSummary>> = emptyMap()
            var error: String? = null

            // Process IELTS results
            when (ieltsResult) {
                is RepositoryResult.Success -> {
                    ieltsHistory = ieltsResult.data.history.map { summary ->
                        GenericExamResultSummary(
                            id = summary.id,
                            examDate = summary.examDate,
                            score = summary.overallBand,
                            scoreLabel = "Overall Band: ${summary.overallBand}",
                            type = ExamType.IELTS
                        )
                    }.sortedByDescending { it.examDate }
                }

                is RepositoryResult.Error -> error = ieltsResult.message
            }

            // Process Multilevel results
            when (multilevelResult) {
                is RepositoryResult.Success -> {
                    multilevelHistoryMap = multilevelResult.data.history.map { summary ->
                        val maxScore =
                            multilevelMaxScores[summary.practicePart] ?: summary.totalScore
                        GenericExamResultSummary(
                            id = summary.id,
                            examDate = summary.examDate,
                            score = summary.totalScore.toDouble(),
                            scoreLabel = "Score: ${summary.totalScore} / ${maxScore.toInt()}",
                            type = ExamType.MULTILEVEL,
                            practicePart = summary.practicePart
                        )
                    }.sortedByDescending { it.examDate }
                        .groupBy { it.practicePart } // The key change: grouping by part
                }

                is RepositoryResult.Error -> error =
                    error ?: multilevelResult.message // Keep first error
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    ieltsHistory = ieltsHistory,
                    multilevelHistory = multilevelHistoryMap,
                    error = error,
                    // Ensure a valid part is selected, default to FULL if available
                    selectedMultilevelPart = if (multilevelHistoryMap.containsKey("FULL")) "FULL" else multilevelHistoryMap.keys.firstOrNull()
                        ?: "FULL"
                )
            }
        }
    }
}