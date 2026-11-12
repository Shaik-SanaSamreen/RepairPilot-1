package com.example.util

import com.example.model.PaymentStatus
import com.example.model.RepairStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingAndWorkflowUnitTest {

    @Test
    fun billingCalculator_computesDecimalTaxDiscountAndBalance() {
        val result = BillingCalculator.calculate(
            labourCharge = 75.50,
            partsTotal = 149.99,
            discountAmount = 25.49,
            taxRatePercent = 8.25,
            paidAmount = 100.00
        )
        assertEquals(225.49, result.subtotal, 0.001)
        assertEquals(25.49, result.discountApplied, 0.001)
        assertEquals(200.00, result.taxableAmount, 0.001)
        assertEquals(16.50, result.taxAmount, 0.001)
        assertEquals(216.50, result.grandTotal, 0.001)
        assertEquals(116.50, result.balanceDue, 0.001)
        assertEquals(PaymentStatus.PARTIAL, result.paymentStatus)
    }

    @Test
    fun billingCalculator_capsDiscountAtSubtotalAndMarksPaid() {
        val result = BillingCalculator.calculate(
            labourCharge = 50.0,
            partsTotal = 50.0,
            discountAmount = 150.0,
            taxRatePercent = 10.0,
            paidAmount = 0.0
        )
        assertEquals(100.0, result.discountApplied, 0.001)
        assertEquals(0.0, result.grandTotal, 0.001)
        assertEquals(PaymentStatus.UNPAID, result.paymentStatus)

        val paidResult = BillingCalculator.calculate(
            labourCharge = 100.0,
            partsTotal = 0.0,
            discountAmount = 0.0,
            taxRatePercent = 10.0,
            paidAmount = 110.0
        )
        assertEquals(0.0, paidResult.balanceDue, 0.001)
        assertEquals(PaymentStatus.PAID, paidResult.paymentStatus)
    }

    @Test
    fun repairStatus_enforcesValidWorkflowTransitions() {
        assertTrue(RepairStatus.DIAGNOSING in RepairStatus.RECEIVED.allowedNextStatuses())
        assertFalse(RepairStatus.DELIVERED in RepairStatus.RECEIVED.allowedNextStatuses())
        assertTrue(RepairStatus.DELIVERED.allowedNextStatuses().isEmpty())
        assertTrue(RepairStatus.READY_FOR_PICKUP in RepairStatus.REPAIR_COMPLETED.allowedNextStatuses())
    }

    @Test
    fun trackingKey_sanitizesTicketAndLast4Digits() {
        assertEquals("RP-1042_8899", BillingCalculator.buildTrackingKey("rp-1042", "+1 (555) 123-8899"))
    }
}
