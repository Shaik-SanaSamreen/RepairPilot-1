package com.example.util

import com.example.model.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

data class InvoiceBreakdown(
    val subtotal: Double,
    val discountApplied: Double,
    val taxableAmount: Double,
    val taxAmount: Double,
    val grandTotal: Double,
    val balanceDue: Double,
    val paymentStatus: PaymentStatus,
)

object BillingCalculator {
    private val moneyFormat = DecimalFormat("#,##0.00")

    fun round2(value: Double): Double =
        BigDecimal.valueOf(value.coerceAtLeast(0.0))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    fun calculate(
        labourCharge: Double,
        partsTotal: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        paidAmount: Double = 0.0,
    ): InvoiceBreakdown {
        val labour = BigDecimal.valueOf(labourCharge.coerceAtLeast(0.0))
        val parts = BigDecimal.valueOf(partsTotal.coerceAtLeast(0.0))
        val subtotal = labour.add(parts).setScale(2, RoundingMode.HALF_UP)

        val rawDiscount = BigDecimal.valueOf(discountAmount.coerceAtLeast(0.0))
        val discountApplied = rawDiscount.min(subtotal).setScale(2, RoundingMode.HALF_UP)

        val taxableAmount = subtotal.subtract(discountApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val taxRate = BigDecimal.valueOf(taxRatePercent.coerceIn(0.0, 50.0))
        val taxAmount = taxableAmount
            .multiply(taxRate)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)

        val grandTotal = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP)
        val paid = BigDecimal.valueOf(paidAmount.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
        val balanceDue = grandTotal.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val status = when {
            grandTotal <= BigDecimal.ZERO && paid <= BigDecimal.ZERO -> PaymentStatus.UNPAID
            balanceDue <= BigDecimal("0.009") && paid > BigDecimal.ZERO -> PaymentStatus.PAID
            paid > BigDecimal.ZERO -> PaymentStatus.PARTIAL
            else -> PaymentStatus.UNPAID
        }

        return InvoiceBreakdown(
            subtotal = subtotal.toDouble(),
            discountApplied = discountApplied.toDouble(),
            taxableAmount = taxableAmount.toDouble(),
            taxAmount = taxAmount.toDouble(),
            grandTotal = grandTotal.toDouble(),
            balanceDue = balanceDue.toDouble(),
            paymentStatus = status,
        )
    }

    fun formatMoney(amount: Double, currencySymbol: String = "$"): String {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        return "$currencySymbol${moneyFormat.format(safe)}"
    }

    fun buildTrackingKey(ticketNumber: String, customerMobile: String): String {
        val cleanTicket = ticketNumber.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        val digits = customerMobile.filter { it.isDigit() }
        val last4 = if (digits.length >= 4) digits.takeLast(4) else digits.padStart(4, '0')
        return "${cleanTicket}_${last4}"
    }
}
