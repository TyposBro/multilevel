// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProgressViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.ExamResultEntity
import com.typosbro.multilevel.data.repositories.ExamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject


enum class DurationFilter(val displayName: String, val days: Long) {
    WEEK("1 Week", 7),
    MONTH("1 Month", 30),
    THREE_MONTHS("3 Months", 90),
    SIX_MONTHS("6 Months", 180),
    ALL("All Time", Long.MAX_VALUE)
}

data class GenericExamResultSummary(
    val id: String,
    val examDate: Long,
    val score: Double,
    val scoreLabel: String,
    val practicePart: String = "FULL"
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDuration: DurationFilter = DurationFilter.ALL,
    val ieltsHistory: List<GenericExamResultSummary> = emptyList(),
    val multilevelHistory: Map<String, List<GenericExamResultSummary>> = emptyMap(),
    val selectedMultilevelPart: String = "FULL",
    // Filtered data based on current filters
    val filteredIeltsHistory: List<GenericExamResultSummary> = emptyList(),
    val filteredMultilevelHistory: Map<String, List<GenericExamResultSummary>> = emptyMap(),
    // Available parts for multilevel (only parts that have data)
    val availableMultilevelParts: Set<String> = emptySet()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val examRepository: ExamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        loadExamHistory()
    }

    private fun loadExamHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            examRepository.getLocalHistorySummary().collect { results ->
                val historyMap = results
                    .toGenericSummary()
                    .groupBy { it.practicePart }

                _uiState.update { currentState ->
                    val newState = currentState.copy(
                        isLoading = false,
                        multilevelHistory = historyMap,
                        availableMultilevelParts = historyMap.keys,
                        selectedMultilevelPart = if (historyMap.containsKey("FULL")) "FULL"
                        else historyMap.keys.firstOrNull() ?: "FULL"
                    )
                    // Apply current filters to the new data
                    applyFilters(newState)
                }
            }

        }
    }


    fun selectMultilevelPart(part: String) {
        _uiState.update { currentState ->
            val newState = currentState.copy(selectedMultilevelPart = part)
            applyFilters(newState)
        }
    }

    fun selectDuration(duration: DurationFilter) {
        _uiState.update { currentState ->
            val newState = currentState.copy(selectedDuration = duration)
            applyFilters(newState)
        }
    }

    private fun applyFilters(state: ProgressUiState): ProgressUiState {
        val cutoffTime = if (state.selectedDuration == DurationFilter.ALL) {
            0L
        } else {
            Instant.now().minus(state.selectedDuration.days, ChronoUnit.DAYS).toEpochMilli()
        }

        val filteredIelts = state.ieltsHistory.filter { it.examDate >= cutoffTime }

        val filteredMultilevel = state.multilevelHistory.mapValues { (_, results) ->
            results.filter { it.examDate >= cutoffTime }
        }.filter { (_, results) -> results.isNotEmpty() }

        return state.copy(
            filteredIeltsHistory = filteredIelts,
            filteredMultilevelHistory = filteredMultilevel
        )
    }

    // Get the currently displayed history based on selected filters
    fun getCurrentHistory(): List<GenericExamResultSummary> {
        val currentState = _uiState.value
        return currentState.filteredMultilevelHistory[currentState.selectedMultilevelPart]
            ?: emptyList()
    }

    // Get available parts for the current duration filter
    fun getAvailableMultilevelParts(): Set<String> {
        return _uiState.value.filteredMultilevelHistory.keys
    }

    // Get statistics for the current selection
    fun getStatistics(): ExamStatistics {
        val history = getCurrentHistory()
        if (history.isEmpty()) {
            return ExamStatistics()
        }

        val scores = history.map { it.score }
        val averageScore = scores.average()
        val bestScore = scores.maxOrNull() ?: 0.0
        val latestScore = history.firstOrNull()?.score ?: 0.0
        val improvement = if (history.size >= 2) {
            latestScore - history.last().score
        } else 0.0

        return ExamStatistics(
            totalExams = history.size,
            averageScore = averageScore,
            bestScore = bestScore,
            latestScore = latestScore,
            improvement = improvement
        )
    }
}

data class ExamStatistics(
    val totalExams: Int = 0,
    val averageScore: Double = 0.0,
    val bestScore: Double = 0.0,
    val latestScore: Double = 0.0,
    val improvement: Double = 0.0
)

// Helper function to convert DB entities to UI models
private fun List<ExamResultEntity>.toGenericSummary(): List<GenericExamResultSummary> {
    val multilevelMaxScores =
        mapOf("FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0)
    return this.map { summary ->
        val maxScore = multilevelMaxScores[summary.practicedPart] ?: summary.totalScore
        GenericExamResultSummary(
            id = summary.id,
            examDate = Instant.parse(summary.createdAt).toEpochMilli(),
            score = summary.totalScore.toDouble(),
            scoreLabel = "Score: ${summary.totalScore} / ${maxScore.toInt()}",
            practicePart = summary.practicedPart
        )
    }.sortedByDescending { it.examDate }
}

