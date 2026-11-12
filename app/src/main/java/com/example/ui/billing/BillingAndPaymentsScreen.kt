package com.example.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Invoice
import com.example.model.Payment
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.StaffRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PaymentStatusPill
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.util.BillingCalculator
import com.example.util.PdfInvoiceGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingAndPaymentsScreen(
    shop: Shop,
    role: StaffRole,
    repairs: List<RepairTicket>,
    invoices: List<Invoice>,
    payments: List<Payment>,
    onGenerateInvoice: (RepairTicket, Double, Double, Double, String) -> Unit,
    onRecordPayment: (Invoice, RepairTicket?, Double, PaymentMethod, Boolean, String) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cur = shop.currencySymbol.ifBlank { "$" }
    var selectedTab by remember { mutableIntStateOf(0) }
    var unpaidOnly by remember { mutableStateOf(false) }
    var showCreateInvoice by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<Invoice?>(null) }

    val filteredInvoices = remember(invoices, unpaidOnly) {
        if (!unpaidOnly) invoices else invoices.filter { it.paymentStatus != PaymentStatus.PAID.name }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (role != StaffRole.TECHNICIAN && selectedTab == 0 && repairs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateInvoice = true },
                    modifier = Modifier.testTag("fab_generate_invoice")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Generate Invoice")
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Invoices (${invoices.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Payments (${payments.size})") }
                )
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !unpaidOnly,
                            onClick = { unpaidOnly = false },
                            label = { Text("All Invoices") }
                        )
                        FilterChip(
                            selected = unpaidOnly,
                            onClick = { unpaidOnly = !unpaidOnly },
                            label = { Text("Unpaid / Balance Due") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredInvoices.isEmpty()) {
                        EmptyStateView(
                            title = "No invoices generated yet",
                            subtitle = "Generate an invoice for a repair ticket to record Cash, UPI, Card, or Bank payments and export PDFs.",
                            actionLabel = if (role != StaffRole.TECHNICIAN && repairs.isNotEmpty()) "Generate Invoice" else null,
                            onAction = if (role != StaffRole.TECHNICIAN && repairs.isNotEmpty()) ({ showCreateInvoice = true }) else null
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(filteredInvoices, key = { it.id }) { inv ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${inv.invoiceNumber} • ${inv.ticketNumber}",
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                                fontWeight = FontWeight.Bold,
                                                color = RoyalBlue
                                            )
                                            PaymentStatusPill(PaymentStatus.fromString(inv.paymentStatus))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${inv.customerName} — ${inv.deviceSummary}", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "Total: ${BillingCalculator.formatMoney(inv.grandTotal, cur)}  |  Paid: ${BillingCalculator.formatMoney(inv.paidAmount, cur)}  |  Due: ${BillingCalculator.formatMoney(inv.balanceDue, cur)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (role != StaffRole.TECHNICIAN && inv.balanceDue > 0.01) {
                                                Button(
                                                    onClick = { paymentTarget = inv },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.Payments, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Record Payment")
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    PdfInvoiceGenerator.generateAndShareInvoicePdf(context, shop, inv)
                                                        .onFailure { onShowToast(it.localizedMessage ?: "PDF export failed") }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("PDF Invoice")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (payments.isEmpty()) {
                    EmptyStateView(
                        title = "No payments recorded",
                        subtitle = "Payments received across Cash, UPI, Bank Transfer, and Card appear here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(payments, key = { it.id }) { pay ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${pay.ticketNumber} • ${pay.customerName}", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${PaymentMethod.fromString(pay.method).label}${if (pay.isAdvance) " (Advance)" else ""}${if (pay.referenceNumber.isNotBlank()) " • Ref ${pay.referenceNumber}" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text("Received by ${pay.receivedByName}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = BillingCalculator.formatMoney(pay.amount, cur),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateInvoice) {
        GenerateInvoiceDialog(
            shop = shop,
            repairs = repairs,
            onDismiss = { showCreateInvoice = false },
            onSave = { repair, labour, discount, tax, summary ->
                onGenerateInvoice(repair, labour, discount, tax, summary)
                showCreateInvoice = false
            }
        )
    }

    if (paymentTarget != null) {
        val inv = paymentTarget!!
        RecordPaymentDialog(
            invoice = inv,
            currencySymbol = cur,
            onDismiss = { paymentTarget = null },
            onSave = { amount, method, isAdvance, ref ->
                val repair = repairs.firstOrNull { it.id == inv.repairId }
                onRecordPayment(inv, repair, amount, method, isAdvance, ref)
                paymentTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateInvoiceDialog(
    shop: Shop,
    repairs: List<RepairTicket>,
    onDismiss: () -> Unit,
    onSave: (RepairTicket, Double, Double, Double, String) -> Unit,
) {
    var selectedRepair by remember(repairs) { mutableStateOf(repairs.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var labour by remember(selectedRepair) { mutableStateOf((selectedRepair?.labourCharge ?: 40.0).toString()) }
    var discount by remember(selectedRepair) { mutableStateOf((selectedRepair?.discountAmount ?: 0.0).toString()) }
    var tax by remember { mutableStateOf(shop.taxRatePercent.toString()) }
    var notes by remember { mutableStateOf("") }

    val preview = BillingCalculator.calculate(
        labourCharge = labour.toDoubleOrNull() ?: 0.0,
        partsTotal = selectedRepair?.partsTotal ?: 0.0,
        discountAmount = discount.toDoubleOrNull() ?: 0.0,
        taxRatePercent = tax.toDoubleOrNull() ?: shop.taxRatePercent,
        paidAmount = selectedRepair?.paidAmount ?: 0.0
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate / Update Invoice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedRepair?.let { "${it.ticketNumber} • ${it.customerName}" } ?: "Select Ticket",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repair Ticket *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        repairs.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.ticketNumber} • ${r.deviceSummary} (${r.customerName})") },
                                onClick = {
                                    selectedRepair = r
                                    labour = r.labourCharge.toString()
                                    discount = r.discountAmount.toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Parts Total: ${BillingCalculator.formatMoney(selectedRepair?.partsTotal ?: 0.0, shop.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = labour, onValueChange = { labour = it }, label = { Text("Labour") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Tax %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(0.8f))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Line Item Notes") })
                Text(
                    text = "Grand Total: ${BillingCalculator.formatMoney(preview.grandTotal, shop.currencySymbol)} (Tax ${BillingCalculator.formatMoney(preview.taxAmount, shop.currencySymbol)})",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = selectedRepair ?: return@Button
                    onSave(r, labour.toDoubleOrNull() ?: 0.0, discount.toDoubleOrNull() ?: 0.0, tax.toDoubleOrNull() ?: shop.taxRatePercent, notes)
                },
                enabled = selectedRepair != null
            ) { Text("Save Invoice") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordPaymentDialog(
    invoice: Invoice,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Double, PaymentMethod, Boolean, String) -> Unit,
) {
    var amount by remember { mutableStateOf(invoice.balanceDue.toString()) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var isAdvance by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment (${invoice.invoiceNumber})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Balance Due: ${BillingCalculator.formatMoney(invoice.balanceDue, currencySymbol)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Payment Amount *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isAdvance, onCheckedChange = { isAdvance = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Advance Deposit")
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("UPI / Bank / Card Reference") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(amount.toDoubleOrNull() ?: 0.0, method, isAdvance, reference) },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Record Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
