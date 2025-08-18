package org.milliytechnology.spiko.features.payment

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ClickPaymentServiceTest {

    private val clickPaymentService = ClickPaymentService()

    @Test
    fun `test valid Uzbekistan phone numbers`() {
        val validNumbers = listOf(
            "+998901234567",
            "998901234567",
            "901234567",
            "+998971234567",
            "971234567"
        )

        validNumbers.forEach { number ->
            assertTrue(
                "Phone number $number should be valid",
                clickPaymentService.isValidUzbekistanPhoneNumber(number)
            )
        }
    }

    @Test
    fun `test invalid Uzbekistan phone numbers`() {
        val invalidNumbers = listOf(
            "+7901234567", // Wrong country code
            "123456789", // Wrong operator code
            "90123456", // Too short
            "9012345678", // Too long
            "+99890123456", // Missing digit
            "", // Empty
            "abc123def" // Non-numeric
        )

        invalidNumbers.forEach { number ->
            assertFalse(
                "Phone number $number should be invalid",
                clickPaymentService.isValidUzbekistanPhoneNumber(number)
            )
        }
    }

    @Test
    fun `test phone number formatting`() {
        val testCases = mapOf(
            "901234567" to "+998901234567",
            "998901234567" to "+998998901234567",
            "+998901234567" to "+998901234567",
            "90-123-45-67" to "+998901234567",
            "+998 90 123 45 67" to "+998901234567"
        )

        testCases.forEach { (input, expected) ->
            assertEquals(
                "Phone number $input should format to $expected",
                expected,
                clickPaymentService.formatPhoneNumber(input)
            )
        }
    }

    @Test
    fun `test payment request creation for web flow`() {
        val request = clickPaymentService.createWebPaymentRequest("silver_monthly")
        
        assertEquals("click", request.provider)
        assertEquals("silver_monthly", request.planId)
        assertEquals("web", request.paymentMethod)
        assertNull(request.phoneNumber)
    }

    @Test
    fun `test payment request creation for invoice flow`() {
        val phoneNumber = "+998901234567"
        val request = clickPaymentService.createInvoicePaymentRequest("silver_monthly", phoneNumber)
        
        assertEquals("click", request.provider)
        assertEquals("silver_monthly", request.planId)
        assertEquals("invoice", request.paymentMethod)
        assertEquals(phoneNumber, request.phoneNumber)
    }
}
