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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// An enum to identify the exam type in a type-safe way
enum class ExamType {
    IELTS,
    MULTILEVEL
}

// NEW: An enum for the history filter periods
enum class HistoryPeriod(val days: Long) {
    SEVEN_DAYS(7),
    ONE_MONTH(30),
    SIX_MONTHS(180)
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
    val selectedMultilevelPart: String = "FULL",
    // NEW: State for the selected history time period
    val selectedPeriod: HistoryPeriod = HistoryPeriod.SEVEN_DAYS,
    // NEW: An event-like state to trigger navigation to a subscription screen
    val navigateToSubscription: Boolean = false
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val ieltsRepository: ChatRepository, // Renamed for clarity
    private val multilevelExamRepository: MultilevelExamRepository
    // In a real app, you would inject a repository to get the user's subscription status
    // private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    // A hardcoded value to simulate the user's current subscription tier.
    // In a real app, this would come from a repository.
    private val currentUserTier = "free" // Possible values: "free", "silver", "gold"


    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // This flow combines the raw data from repositories with the UI state for filtering
            combine(
                ieltsRepository.getLocalHistorySummary(),
                multilevelExamRepository.getLocalHistorySummary(),
                _uiState.map { it.selectedPeriod }
                    .distinctUntilChanged() // Re-triggers when period changes
            ) { ieltsResults, multilevelResults, selectedPeriod ->

                val historyCutoff =
                    System.currentTimeMillis() - (selectedPeriod.days * 24 * 60 * 60 * 1000)

                // Filter IELTS results based on the selected period
                val ieltsHistory = ieltsResults
                    .filter { it.createdAt.toLong() >= historyCutoff }
                    .toIeltsGenericSummary()

                // Filter Multilevel results, then group them by part
                val multilevelHistoryMap = multilevelResults
                    .filter { Instant.parse(it.createdAt).toEpochMilli() >= historyCutoff }
                    .toMultilevelGenericSummary()
                    .groupBy { it.practicePart }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ieltsHistory = ieltsHistory,
                        multilevelHistory = multilevelHistoryMap,
                        // Ensure a valid part is selected, default to FULL if available
                        selectedMultilevelPart = if (multilevelHistoryMap.containsKey(it.selectedMultilevelPart)) it.selectedMultilevelPart
                        else if (multilevelHistoryMap.containsKey("FULL")) "FULL" else multilevelHistoryMap.keys.firstOrNull()
                            ?: "FULL"
                    )
                }
            }.collect { }
        }
    }

    fun selectTab(type: ExamType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    fun selectMultilevelPart(part: String) {
        _uiState.update { it.copy(selectedMultilevelPart = part) }
    }

    /**
     * Handles selection of the history time period.
     * If the user is free and selects a premium period, it triggers a navigation prompt.
     */
    fun selectPeriod(period: HistoryPeriod) {
        // Assume only 7 days is free.
        val isPeriodFree = period == HistoryPeriod.SEVEN_DAYS
        val hasSubscription = currentUserTier == "silver" || currentUserTier == "gold"

        if (isPeriodFree || hasSubscription) {
            _uiState.update { it.copy(selectedPeriod = period) }
        } else {
            // User is on a free plan and selected a paid-for period.
            _uiState.update { it.copy(navigateToSubscription = true) }
        }
    }

    /**
     * Resets the navigation event after it has been handled.
     */
    fun onSubscriptionNavigationHandled() {
        _uiState.update { it.copy(navigateToSubscription = false) }
    }
}


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