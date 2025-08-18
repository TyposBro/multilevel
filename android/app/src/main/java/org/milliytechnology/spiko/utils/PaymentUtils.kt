package org.milliytechnology.spiko.utils

import java.text.NumberFormat
import java.util.*

object PaymentUtils {
    
    /**
     * Formats price in tiyin to human readable format
     */
    fun formatPrice(priceInTiyin: Long): String {
        val priceInSums = priceInTiyin / 100.0
        return "${priceInSums.toInt()} сум"
    }
    
    /**
     * Formats price in sums with proper number formatting
     */
    fun formatPriceWithThousands(priceInSums: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("uz", "UZ"))
        return "${formatter.format(priceInSums.toInt())} сум"
    }
    
    /**
     * Validates Uzbekistan phone number
     */
    fun isValidUzbekistanPhone(phone: String): Boolean {
        val cleanPhone = phone.replace(Regex("[^\\d]"), "")
        return when {
            cleanPhone.startsWith("998") && cleanPhone.length == 12 -> true
            cleanPhone.length == 9 && cleanPhone.matches(Regex("^(90|91|93|94|95|97|98|99).*")) -> true
            else -> false
        }
    }
    
    /**
     * Formats phone number to +998XXXXXXXXX
     */
    fun formatPhoneNumber(phone: String): String {
        val cleanPhone = phone.replace(Regex("[^\\d]"), "")
        return when {
            cleanPhone.startsWith("998") -> "+$cleanPhone"
            cleanPhone.length == 9 -> "+998$cleanPhone"
            else -> phone
        }
    }
    
    /**
     * Gets display name for payment status
     */
    fun getPaymentStatusDisplayName(status: String): String {
        return when (status.uppercase()) {
            "PENDING" -> "Ожидание"
            "PREPARED" -> "Подготовлен"
            "COMPLETED" -> "Завершен"
            "FAILED" -> "Отклонен"
            "CANCELLED" -> "Отменен"
            else -> status
        }
    }
    
    /**
     * Gets color for payment status
     */
    fun getPaymentStatusColor(status: String): androidx.compose.ui.graphics.Color {
        return when (status.uppercase()) {
            "COMPLETED" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            "FAILED", "CANCELLED" -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
            "PENDING" -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            "PREPARED" -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
            else -> androidx.compose.ui.graphics.Color.Gray
        }
    }
    
    /**
     * Converts duration in days to human readable format
     */
    fun formatDuration(days: Int): String {
        return when {
            days == 1 -> "1 день"
            days < 7 -> "$days дней"
            days == 7 -> "1 неделя"
            days < 30 -> "${days / 7} недель"
            days == 30 -> "1 месяц"
            days < 365 -> "${days / 30} месяцев"
            else -> "${days / 365} лет"
        }
    }
}
