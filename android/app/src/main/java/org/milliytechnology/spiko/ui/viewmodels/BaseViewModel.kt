// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/BaseViewModel.kt
package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.RepositoryResult

// Simple base for common loading/error state
open class BaseViewModel : ViewModel() {
    protected val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    protected val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow() // Expose error state

    // Function to clear error message (e.g., when user dismisses it)
    fun clearError() {
        _error.value = null
    }

    // Helper to run suspending functions with loading/error handling
    protected fun <T> launchDataLoad(
        block: suspend () -> RepositoryResult<T>,
        onSuccess: (T) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null // Clear previous error
            when (val result = block()) {
                is RepositoryResult.Success -> onSuccess(result.data)
                is RepositoryResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    // Overload for actions that don't need specific success data handling
    protected fun launchAction(
        block: suspend () -> RepositoryResult<*>,
        onComplete: (() -> Unit)? = null // Optional completion callback
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = block()) {
                is RepositoryResult.Success -> { /* Action succeeded */
                }

                is RepositoryResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
            onComplete?.invoke() // Call completion callback if provided
        }
    }
}