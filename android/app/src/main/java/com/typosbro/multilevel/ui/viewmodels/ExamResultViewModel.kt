// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamResultViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.ExamResultResponse
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamResultUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val result: ExamResultResponse? = null
)

@HiltViewModel
class ExamResultViewModel @Inject constructor(
    private val repository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamResultUiState())
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    private val resultId: String = checkNotNull(savedStateHandle["resultId"])

    init {
        fetchResultDetails()
    }

    private fun fetchResultDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Use a 'when' expression to handle the custom RepositoryResult
            when (val result = repository.getExamResultDetails(resultId)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, result = result.data)
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }
}