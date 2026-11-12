package com.example.ui.inventory

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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.InventoryTransaction
import com.example.model.InventoryTxType
import com.example.model.Shop
import com.example.model.SparePart
import com.example.model.StaffRole
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import com.example.util.BillingCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    shop: Shop,
    role: StaffRole,
    parts: List<SparePart>,
    transactions: List<InventoryTransaction>,
    onAddPart: (String, String, String, Long, Double, Double, Long, String, String) -> Unit,
    onAdjustStock: (SparePart, Long, InventoryTxType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var lowStockOnly by remember { mutableStateOf(false) }
    var showAddPart by remember { mutableStateOf(false) }
    var stockAdjustTarget by remember { mutableStateOf<Pair<SparePart, InventoryTxType>?>(null) }

    val cur = shop.currencySymbol.ifBlank { "$" }
    val filteredParts = remember(parts, search, lowStockOnly) {
        parts.filter {
            val matchesText = search.isBlank() ||
                it.name.contains(search, true) ||
                it.sku.contains(search, true) ||
                it.category.contains(search, true)
            val matchesLow = !lowStockOnly || it.isLowStock
            matchesText && matchesLow
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (role == StaffRole.OWNER && selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddPart = true },
                    modifier = Modifier.testTag("fab_add_part")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Spare Part")
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
                    text = { Text("Spare Parts (${parts.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Stock Ledger (${transactions.size})") }
                )
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Search part name, SKU, category...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !lowStockOnly,
                            onClick = { lowStockOnly = false },
                            label = { Text("All Parts") }
                        )
                        FilterChip(
                            selected = lowStockOnly,
                            onClick = { lowStockOnly = !lowStockOnly },
                            label = { Text("Low Stock (${parts.count { it.isLowStock }})") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredParts.isEmpty()) {
                        EmptyStateView(
                            title = "No spare parts found",
                            subtitle = "Add OEM screens, batteries, and charging ports to track stock and repair costs.",
                            actionLabel = if (role == StaffRole.OWNER) "Add Spare Part" else null,
                            onAction = if (role == StaffRole.OWNER) ({ showAddPart = true }) else null
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(filteredParts, key = { it.id }) { part ->
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(part.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "${part.sku}  •  ${part.category}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                color = if (part.isLowStock) WarningContainer else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (part.isLowStock) {
                                                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.padding(end = 4.dp))
                                                    }
                                                    Text(
                                                        text = "Qty ${part.stockQty}",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Buy ${BillingCalculator.formatMoney(part.purchasePrice, cur)}  •  Sell ${BillingCalculator.formatMoney(part.sellingPrice, cur)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "Min ${part.minStockLevel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (part.supplierName.isNotBlank()) {
                                            Text(
                                                text = "Supplier: ${part.supplierName} ${part.supplierContact}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (role != StaffRole.RECEPTIONIST) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { stockAdjustTarget = part to InventoryTxType.STOCK_IN },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Stock In")
                                                }
                                                OutlinedButton(
                                                    onClick = { stockAdjustTarget = part to InventoryTxType.STOCK_OUT },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Stock Out")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        val isPositive = tx.quantityDelta > 0
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.partName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${InventoryTxType.fromString(tx.txType).label} (${tx.stockBefore} -> ${tx.stockAfter})${if (tx.referenceTicket.isNotBlank()) " • ${tx.referenceTicket}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (tx.note.isNotBlank()) {
                                        Text(tx.note, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    text = if (isPositive) "+${tx.quantityDelta}" else "${tx.quantityDelta}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) SuccessGreen else ErrorRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddPart) {
        AddSparePartDialog(
            onDismiss = { showAddPart = false },
            onSave = { name, sku, cat, qty, buy, sell, min, supName, supPhone ->
                onAddPart(name, sku, cat, qty, buy, sell, min, supName, supPhone)
                showAddPart = false
            }
        )
    }

    if (stockAdjustTarget != null) {
        val (part, txType) = stockAdjustTarget!!
        AdjustStockDialog(
            part = part,
            txType = txType,
            onDismiss = { stockAdjustTarget = null },
            onConfirm = { qty, note ->
                onAdjustStock(part, qty, txType, note)
                stockAdjustTarget = null
            }
        )
    }
}

@Composable
private fun AddSparePartDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, Double, Double, Long, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Display & OLED") }
    var stockQty by remember { mutableStateOf("5") }
    var purchasePrice by remember { mutableStateOf("45") }
    var sellingPrice by remember { mutableStateOf("89") }
    var minStock by remember { mutableStateOf("2") }
    var supplierName by remember { mutableStateOf("") }
    var supplierContact by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Spare Part") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Part Name *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it.uppercase() }, label = { Text("SKU *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stockQty, onValueChange = { stockQty = it.filter(Char::isDigit) }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = minStock, onValueChange = { minStock = it.filter(Char::isDigit) }, label = { Text("Min Alert") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("Buy Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("Sell Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = supplierName, onValueChange = { supplierName = it }, label = { Text("Supplier Name") }, singleLine = true)
                OutlinedTextField(value = supplierContact, onValueChange = { supplierContact = it }, label = { Text("Supplier Contact") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name, sku, category,
                        stockQty.toLongOrNull() ?: 0L,
                        purchasePrice.toDoubleOrNull() ?: 0.0,
                        sellingPrice.toDoubleOrNull() ?: 0.0,
                        minStock.toLongOrNull() ?: 2L,
                        supplierName, supplierContact
                    )
                },
                enabled = name.isNotBlank() && sku.isNotBlank()
            ) { Text("Save Part") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AdjustStockDialog(
    part: SparePart,
    txType: InventoryTxType,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var qty by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${txType.label}: ${part.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current stock: ${part.stockQty}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter(Char::isDigit) },
                    label = { Text("Quantity *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reason / Invoice Ref") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty.toLongOrNull() ?: 1L, note) },
                enabled = (qty.toLongOrNull() ?: 0L) > 0L
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
