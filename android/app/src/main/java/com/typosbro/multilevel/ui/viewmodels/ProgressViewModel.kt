// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProgressViewModel.kt

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
data class GenericExamResultSummary(
    val id: String,
    val examDate: Long,
    val score: Double,
    val scoreLabel: String, // e.g., "Overall Band" or "Total Score"
    val type: ExamType
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: ExamType = ExamType.MULTILEVEL, // Default to the new exam type
    val ieltsHistory: List<GenericExamResultSummary> = emptyList(),
    val multilevelHistory: List<GenericExamResultSummary> = emptyList()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val chatRepository: ChatRepository, // For IELTS
    private val multilevelExamRepository: MultilevelExamRepository // For Multilevel
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAllHistories()
    }

    fun selectTab(type: ExamType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    private fun loadAllHistories() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Fetch both histories in parallel for better performance
            val ieltsDeferred =
                async { multilevelExamRepository.getExamHistory() } // Corrected repository for Multilevel
            val multilevelDeferred = async { chatRepository.getExamHistorySummary() } // For IELTS

            val ieltsResult = ieltsDeferred.await()
            val multilevelResult = multilevelDeferred.await()

            var ieltsHistory: List<GenericExamResultSummary> = emptyList()
            var multilevelHistory: List<GenericExamResultSummary> = emptyList()
            var error: String? = null

            // Process IELTS results
            when (multilevelResult) {
                is RepositoryResult.Success -> {
                    ieltsHistory = multilevelResult.data.history.map { summary ->
                        GenericExamResultSummary(
                            id = summary.id,
                            examDate = summary.examDate,
                            score = summary.overallBand,
                            scoreLabel = "Overall Band: ${summary.overallBand}",
                            type = ExamType.IELTS
                        )
                    }.sortedByDescending { it.examDate }
                }

                is RepositoryResult.Error -> error = multilevelResult.message
            }

            // Process Multilevel results
            when (ieltsResult) {
                is RepositoryResult.Success -> {
                    multilevelHistory = ieltsResult.data.history.map { summary ->
                        GenericExamResultSummary(
                            id = summary.id,
                            examDate = summary.examDate,
                            score = summary.totalScore.toDouble(),
                            scoreLabel = "Total Score: ${summary.totalScore}",
                            type = ExamType.MULTILEVEL
                        )
                    }.sortedByDescending { it.examDate }
                }

                is RepositoryResult.Error -> error =
                    error ?: ieltsResult.message // Keep first error
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    ieltsHistory = ieltsHistory,
                    multilevelHistory = multilevelHistory,
                    error = error
                )
            }
        }
    }
}