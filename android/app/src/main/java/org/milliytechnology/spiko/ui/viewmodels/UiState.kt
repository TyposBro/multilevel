package org.milliytechnology.spiko.ui.viewmodels

/**
 * A generic sealed interface to represent UI states for asynchronous operations.
 * It provides clear states for Idle, Loading, Success with data, and Error with a message.
 */
sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}