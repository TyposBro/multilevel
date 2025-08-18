package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.ClickPaymentMethod
import org.milliytechnology.spiko.data.remote.models.CreatePaymentRequest
import org.milliytechnology.spiko.data.remote.models.CreatePaymentResponse
import org.milliytechnology.spiko.data.remote.models.PaymentPlan
import org.milliytechnology.spiko.data.remote.models.PaymentProvider
import org.milliytechnology.spiko.data.remote.models.PaymentStatusResponse
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.PaymentRepository
import org.milliytechnology.spiko.features.payment.ClickPaymentService
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val clickPaymentService: ClickPaymentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _paymentResult = MutableStateFlow<PaymentResult?>(null)
    val paymentResult: StateFlow<PaymentResult?> = _paymentResult.asStateFlow()

    private var statusPollingJob: Job? = null

    init {
        // Load available plans
        _uiState.value = _uiState.value.copy(
            availablePlans = PaymentPlan.AVAILABLE_PLANS,
            selectedPlan = PaymentPlan.AVAILABLE_PLANS.firstOrNull()
        )
    }

    fun selectPlan(plan: PaymentPlan) {
        _uiState.value = _uiState.value.copy(selectedPlan = plan)
    }

    fun createWebPayment(planId: String) {
        val plan = PaymentPlan.AVAILABLE_PLANS.find { it.id == planId } ?: return
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val request = clickPaymentService.createWebPaymentRequest(planId)

                when (val result = paymentRepository.createPayment(request)) {
                    is RepositoryResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            paymentResponse = result.data,
                            statusMessage = "Перенаправление на страницу оплаты..."
                        )
                    }
                    is RepositoryResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка создания платежа: ${e.message}"
                )
            }
        }
    }

    fun checkPaymentStatus(transactionId: String) {
        viewModelScope.launch {
            when (val result = paymentRepository.getPaymentStatus(transactionId)) {
                is RepositoryResult.Success -> {
                    val status = result.data
                    _paymentResult.value = PaymentResult(
                        success = status.status == "COMPLETED",
                        message = when (status.status) {
                            "COMPLETED" -> "Оплата успешно завершена!"
                            "FAILED" -> "Платеж отклонен"
                            "PENDING" -> "Ожидание оплаты..."
                            "PREPARED" -> "Платеж подготовлен..."
                            else -> "Неизвестный статус: ${status.status}"
                        },
                        status = status.status,
                        transactionId = transactionId
                    )
                }
                is RepositoryResult.Error -> {
                    _paymentResult.value = PaymentResult(
                        success = false,
                        message = "Ошибка проверки статуса: ${result.message}",
                        status = "ERROR",
                        transactionId = transactionId
                    )
                }
            }
        }
    }

    private fun startStatusPolling(transactionId: String) {
        stopStatusPolling() // Stop any existing polling
        
        statusPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                
                when (val result = paymentRepository.getPaymentStatus(transactionId)) {
                    is RepositoryResult.Success -> {
                        val status = result.data
                        
                        _uiState.value = _uiState.value.copy(
                            statusMessage = when (status.status) {
                                "COMPLETED" -> "Оплата успешно завершена!"
                                "FAILED" -> "Платеж отклонен"
                                "PENDING" -> "Ожидание оплаты..."
                                "PREPARED" -> "Платеж подготовлен..."
                                else -> "Статус: ${status.status}"
                            }
                        )
                        
                        // Stop polling if payment is completed or failed
                        if (status.status in listOf("COMPLETED", "FAILED")) {
                            _paymentResult.value = PaymentResult(
                                success = status.status == "COMPLETED",
                                message = _uiState.value.statusMessage,
                                status = status.status,
                                transactionId = transactionId
                            )
                            break
                        }
                    }
                    is RepositoryResult.Error -> {
                        // Continue polling on error, but log it
                        // You might want to add error handling here
                    }
                }
            }
        }
        
        // Stop polling after 5 minutes
        viewModelScope.launch {
            delay(300_000) // 5 minutes
            stopStatusPolling()
            _uiState.value = _uiState.value.copy(
                statusMessage = "Время ожидания истекло. Проверьте статус вручную."
            )
        }
    }

    fun stopStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearPaymentResult() {
        _paymentResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusPolling()
    }
}

data class PaymentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val availablePlans: List<PaymentPlan> = emptyList(),
    val selectedPlan: PaymentPlan? = null,
    val paymentResponse: CreatePaymentResponse? = null,
    val statusMessage: String = ""
)

data class PaymentResult(
    val success: Boolean,
    val message: String,
    val status: String,
    val transactionId: String
)
