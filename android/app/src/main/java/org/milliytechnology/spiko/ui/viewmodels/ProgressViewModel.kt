// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProgressViewModel.kt
package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.local.ExamResultEntity
import org.milliytechnology.spiko.data.repositories.ExamRepository
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
    val practicePart: String
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDuration: DurationFilter = DurationFilter.ALL,
    // Holds all history from the DB, ungrouped
    val allMultilevelHistory: List<GenericExamResultSummary> = emptyList(),
    // The currently selected part to display, e.g., "FULL", "P1_1"
    val selectedMultilevelPart: String = "FULL",
    // History grouped by part and filtered by duration
    val filteredMultilevelHistory: Map<String, List<GenericExamResultSummary>> = emptyMap()
) {
    // Provides a list of available parts for the filter dropdown based on current data
    val availableMultilevelParts: Set<String> get() = filteredMultilevelHistory.keys
}

data class ExamStatistics(
    val totalExams: Int = 0,
    val averageScore: Double = 0.0,
    val bestScore: Double = 0.0,
    val latestScore: Double = 0.0,
    val improvement: Double = 0.0 // Change between latest and second-to-last
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
                val allHistory = results.toGenericSummary()
                _uiState.update { currentState ->
                    // Set the initial selected part to "FULL" if available, otherwise the first one.
                    val initialPart = if (allHistory.any { it.practicePart == "FULL" }) "FULL"
                    else allHistory.firstOrNull()?.practicePart ?: "FULL"

                    applyFilters(
                        currentState.copy(
                            isLoading = false,
                            allMultilevelHistory = allHistory,
                            selectedMultilevelPart = initialPart
                        )
                    )
                }
            }
        }
    }

    fun selectMultilevelPart(part: String) {
        _uiState.update { currentState ->
            applyFilters(currentState.copy(selectedMultilevelPart = part))
        }
    }

    fun selectDuration(duration: DurationFilter) {
        _uiState.update { currentState ->
            applyFilters(currentState.copy(selectedDuration = duration))
        }
    }

    private fun applyFilters(state: ProgressUiState): ProgressUiState {
        val cutoffTime = if (state.selectedDuration == DurationFilter.ALL) {
            0L
        } else {
            Instant.now().minus(state.selectedDuration.days, ChronoUnit.DAYS).toEpochMilli()
        }

        val filteredAndGroupedHistory = state.allMultilevelHistory
            .filter { it.examDate >= cutoffTime }
            .groupBy { it.practicePart }

        return state.copy(filteredMultilevelHistory = filteredAndGroupedHistory)
    }

    // A helper to get the list of results for the currently selected part
    fun getCurrentHistory(): List<GenericExamResultSummary> {
        val state = _uiState.value
        return state.filteredMultilevelHistory[state.selectedMultilevelPart] ?: emptyList()
    }

    // A helper to get the set of available parts for the dropdown
    fun getAvailableMultilevelParts(): Set<String> {
        return _uiState.value.availableMultilevelParts
    }

    // Calculates statistics for the currently selected and filtered list of results
    fun getStatistics(): ExamStatistics {
        val history = getCurrentHistory()
        if (history.isEmpty()) return ExamStatistics()

        val scores = history.map { it.score }
        val latestScore = history.first().score
        val improvement = if (history.size > 1) {
            // Improvement is the delta between the latest and the one before it
            latestScore - history[1].score
        } else 0.0

        return ExamStatistics(
            totalExams = scores.size,
            averageScore = scores.average(),
            bestScore = scores.maxOrNull() ?: 0.0,
            latestScore = latestScore,
            improvement = improvement
        )
    }
}

// Helper function to convert DB entities to UI-ready summary models
private fun List<ExamResultEntity>.toGenericSummary(): List<GenericExamResultSummary> {
    // Define the maximum score for each part
    val multilevelMaxScores =
        mapOf("FULL" to 72.0, "P1_1" to 12.0, "P1_2" to 12.0, "P2" to 24.0, "P3" to 24.0)

    return this.map { entity ->
        val maxScore = multilevelMaxScores[entity.practicedPart] ?: entity.totalScore.toDouble()
        GenericExamResultSummary(
            id = entity.id,
            examDate = Instant.parse(entity.createdAt).toEpochMilli(),
            score = entity.totalScore.toDouble(),
            // Create the desired "Score: 10 / 12" label
            scoreLabel = "Score: ${entity.totalScore} / ${maxScore.toInt()}",
            practicePart = entity.practicedPart
        )
    }.sortedByDescending { it.examDate } // Ensure latest is always first
}