package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.Invoice
import com.example.model.Shop
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfInvoiceGenerator {

    fun generateAndShareInvoicePdf(
        context: Context,
        shop: Shop,
        invoice: Invoice,
    ): Result<File> = runCatching {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeNum = invoice.invoiceNumber.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val pdfFile = File(dir, "Invoice_$safeNum.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 72dpi
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val navyPaint = Paint().apply {
            color = Color.parseColor("#0F2D52")
            style = Paint.Style.FILL
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#06B6D4")
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subHeaderWhite = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 11f
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = Color.parseColor("#0F2D52")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 11.5f
            isAntiAlias = true
        }
        val boldBodyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 10.5f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        // Top Navy Header Band
        canvas.drawRect(0f, 0f, 595f, 118f, navyPaint)
        canvas.drawRect(0f, 118f, 595f, 123f, accentPaint)

        canvas.drawText(shop.shopName.ifBlank { "RepairPilot Shop" }, 36f, 48f, titlePaint)
        canvas.drawText(
            listOfNotNull(
                shop.phone.takeIf { it.isNotBlank() },
                shop.address.takeIf { it.isNotBlank() }
            ).joinToString("  •  "),
            36f,
            72f,
            subHeaderWhite
        )
        canvas.drawText("REPAIR INVOICE", 36f, 98f, subHeaderWhite)

        // Invoice Metadata
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(
            invoice.createdAt?.toDate() ?: Date()
        )
        var y = 160f
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 36f, y, headingPaint)
        canvas.drawText("Date: $dateStr", 400f, y, bodyPaint)
        y += 20f
        canvas.drawText("Ticket #: ${invoice.ticketNumber}", 36f, y, bodyPaint)
        canvas.drawText("Status: ${invoice.paymentStatus}", 400f, y, boldBodyPaint)

        y += 28f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 24f

        // Customer & Device
        canvas.drawText("BILL TO", 36f, y, mutedPaint)
        canvas.drawText("DEVICE SERVICED", 310f, y, mutedPaint)
        y += 18f
        canvas.drawText(invoice.customerName, 36f, y, boldBodyPaint)
        canvas.drawText(invoice.deviceSummary, 310f, y, boldBodyPaint)
        y += 16f
        if (invoice.customerMobile.isNotBlank()) {
            canvas.drawText("Mobile: ${invoice.customerMobile}", 36f, y, bodyPaint)
        }
        y += 28f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 24f

        // Charges Table Header
        canvas.drawText("DESCRIPTION", 36f, y, mutedPaint)
        canvas.drawText("AMOUNT", 470f, y, mutedPaint)
        y += 14f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 22f

        val cur = shop.currencySymbol.ifBlank { "$" }
        fun drawRow(label: String, value: String, bold: Boolean = false) {
            val paint = if (bold) boldBodyPaint else bodyPaint
            canvas.drawText(label, 36f, y, paint)
            canvas.drawText(value, 470f, y, paint)
            y += 22f
        }

        drawRow("Technician Labour & Diagnostics", BillingCalculator.formatMoney(invoice.labourCharge, cur))
        drawRow("Spare Parts & Components", BillingCalculator.formatMoney(invoice.partsTotal, cur))

        if (invoice.lineItemsSummary.isNotBlank()) {
            canvas.drawText("Parts: ${invoice.lineItemsSummary.take(80)}", 48f, y, mutedPaint)
            y += 20f
        }
        if (invoice.discountAmount > 0.0) {
            drawRow("Discount", "-${BillingCalculator.formatMoney(invoice.discountAmount, cur)}")
        }
        drawRow("Tax (${invoice.taxRatePercent}%)", BillingCalculator.formatMoney(invoice.taxAmount, cur))

        y += 6f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 24f

        drawRow("Grand Total", BillingCalculator.formatMoney(invoice.grandTotal, cur), bold = true)
        drawRow("Amount Paid", BillingCalculator.formatMoney(invoice.paidAmount, cur))
        drawRow("Balance Due", BillingCalculator.formatMoney(invoice.balanceDue, cur), bold = true)

        y += 30f
        canvas.drawText("Thank you for trusting ${shop.shopName.ifBlank { "RepairPilot" }} with your device!", 36f, y, mutedPaint)

        document.finishPage(page)
        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber} - ${invoice.ticketNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Repair Invoice PDF"))
        pdfFile
    }
}
