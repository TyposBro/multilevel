// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProgressViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.ExamResultSummary
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val history: List<ExamResultSummary> = emptyList(),
    val averageScore: Double = 0.0,
    val highestScore: Double = 0.0,
    val testCount: Int = 0
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel(){

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = chatRepository.getExamHistorySummary()) {
                is Result.Success -> {
                    val historyList = result.data.history.sortedBy { it.examDate }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            history = historyList,
                            testCount = historyList.size,
                            averageScore = historyList.map { item -> item.overallBand }.average(),
                            highestScore = historyList.maxOfOrNull { item -> item.overallBand } ?: 0.0
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}