package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.MultilevelExamResultResponse
import com.typosbro.multilevel.data.repositories.MultilevelExamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MultilevelResultUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val result: MultilevelExamResultResponse? = null
)

@HiltViewModel
class MultilevelResultViewModel @Inject constructor(
    private val repository: MultilevelExamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultilevelResultUiState())
    val uiState = _uiState.asStateFlow()

    private val resultId: String = checkNotNull(savedStateHandle["resultId"])

    init {
        fetchResultDetails()
    }

    private fun fetchResultDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getLocalResultDetails(resultId).collectLatest { resultEntity ->
                if (resultEntity != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result = resultEntity.toResponse()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Result with ID $resultId not found in local database."
                        )
                    }
                }
            }
        }
    }
}
