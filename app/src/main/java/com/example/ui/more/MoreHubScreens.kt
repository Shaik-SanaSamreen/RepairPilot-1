package com.example.ui.more

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AuditLog
import com.example.model.Expense
import com.example.model.InventoryTransaction
import com.example.model.InventoryTxType
import com.example.model.PaymentStatus
import com.example.model.PublicTracking
import com.example.model.RepairStatus
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.ShopInvite
import com.example.model.ShopMember
import com.example.model.ShopNotification
import com.example.model.StaffRole
import com.example.model.TechnicianProfile
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PaymentStatusPill
import com.example.ui.components.RepairStatusPill
import com.example.ui.components.RevenueBarChart
import com.example.ui.components.RolePill
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.util.BillingCalculator
import com.example.viewmodel.DashboardSummary

enum class MoreSubScreen {
    HUB,
    TRACKING,
    REPORTS,
    TECHNICIANS,
    EXPENSES,
    NOTIFICATIONS,
    AUDIT_LOGS,
    SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubContainer(
    activeSubScreen: MoreSubScreen,
    onNavigateSub: (MoreSubScreen) -> Unit,
    shop: Shop,
    role: StaffRole,
    summary: DashboardSummary,
    repairs: List<RepairTicket>,
    technicians: List<TechnicianProfile>,
    members: List<ShopMember>,
    invites: List<ShopInvite>,
    expenses: List<Expense>,
    inventoryTx: List<InventoryTransaction>,
    notifications: List<ShopNotification>,
    auditLogs: List<AuditLog>,
    trackingResult: PublicTracking?,
    trackingSearched: Boolean,
    onLookupTracking: (String, String) -> Unit,
    onSelectRepair: (RepairTicket) -> Unit,
    onAddTechnician: (String, String, String) -> Unit,
    onCreateInvite: (String, StaffRole, String) -> Unit,
    onSyncTeam: () -> Unit,
    onAddExpense: (String, String, Double, String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSaveShopSettings: (String, String, String, String, Double) -> Unit,
    onOpenBilling: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (activeSubScreen != MoreSubScreen.HUB) {
        BackHandler { onNavigateSub(MoreSubScreen.HUB) }
    }

    when (activeSubScreen) {
        MoreSubScreen.HUB -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(shop.shopName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Shop Code: ${shop.shopCode}", style = MaterialTheme.typography.bodySmall)
                            }
                            RolePill(role = role)
                        }
                    }
                }
                if (role != StaffRole.TECHNICIAN) {
                    item {
                        MoreMenuRow("Billing, Invoices & Payments", "Generate PDF invoices & record payments", Icons.Rounded.ReceiptLong) {
                            onOpenBilling()
                        }
                    }
                }
                item {
                    MoreMenuRow("Repair Tracking & Customer Portal", "Lookup by ticket # or customer mobile", Icons.Rounded.QrCodeScanner) {
                        onNavigateSub(MoreSubScreen.TRACKING)
                    }
                }
                if (role != StaffRole.TECHNICIAN) {
                    item {
                        MoreMenuRow("Reports & Analytics", "Revenue, profit, technician stats & export", Icons.Rounded.Analytics) {
                            onNavigateSub(MoreSubScreen.REPORTS)
                        }
                    }
                }
                item {
                    MoreMenuRow("Technicians & Staff Team", "${technicians.size} technicians • ${members.size} members", Icons.Rounded.Engineering) {
                        onNavigateSub(MoreSubScreen.TECHNICIANS)
                    }
                }
                if (role == StaffRole.OWNER) {
                    item {
                        MoreMenuRow("Expenses Ledger", "${expenses.size} recorded expenses", Icons.Rounded.MoneyOff) {
                            onNavigateSub(MoreSubScreen.EXPENSES)
                        }
                    }
                }
                item {
                    MoreMenuRow("Notifications", "${summary.unreadNotifications} unread alerts", Icons.Rounded.Notifications) {
                        onNavigateSub(MoreSubScreen.NOTIFICATIONS)
                    }
                }
                item {
                    MoreMenuRow("Audit Trail", "${auditLogs.size} immutable activity events", Icons.Rounded.History) {
                        onNavigateSub(MoreSubScreen.AUDIT_LOGS)
                    }
                }
                if (role == StaffRole.OWNER) {
                    item {
                        MoreMenuRow("Shop Settings", "Tax rate, currency, address & phone", Icons.Rounded.Settings) {
                            onNavigateSub(MoreSubScreen.SETTINGS)
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("more_sign_out_button")
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        MoreSubScreen.TRACKING -> SubScaffold("Repair Tracking", { onNavigateSub(MoreSubScreen.HUB) }) {
            TrackingSubScreen(
                repairs = repairs,
                trackingResult = trackingResult,
                trackingSearched = trackingSearched,
                onLookupPublic = onLookupTracking,
                onSelectRepair = onSelectRepair
            )
        }

        MoreSubScreen.REPORTS -> SubScaffold("Reports & Analytics", { onNavigateSub(MoreSubScreen.HUB) }) {
            ReportsSubScreen(shop, summary, repairs, technicians, inventoryTx)
        }

        MoreSubScreen.TECHNICIANS -> SubScaffold("Technicians & Team", { onNavigateSub(MoreSubScreen.HUB) }) {
            TechniciansSubScreen(role, technicians, members, invites, onAddTechnician, onCreateInvite, onSyncTeam)
        }

        MoreSubScreen.EXPENSES -> SubScaffold("Shop Expenses", { onNavigateSub(MoreSubScreen.HUB) }) {
            ExpensesSubScreen(shop, expenses, onAddExpense)
        }

        MoreSubScreen.NOTIFICATIONS -> SubScaffold("Notifications", { onNavigateSub(MoreSubScreen.HUB) }) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMarkNotificationRead(notif.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(notif.title, fontWeight = FontWeight.Bold)
                            Text(notif.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        MoreSubScreen.AUDIT_LOGS -> SubScaffold("Audit Trail", { onNavigateSub(MoreSubScreen.HUB) }) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.id }) { log ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${log.action} • ${log.entityType}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = RoyalBlue)
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        MoreSubScreen.SETTINGS -> SubScaffold("Shop Settings", { onNavigateSub(MoreSubScreen.HUB) }) {
            ShopSettingsSubScreen(shop, onSaveShopSettings)
        }
    }
}

@Composable
private fun MoreMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = RoyalBlue)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            content()
        }
    }
}

@Composable
private fun TrackingSubScreen(
    repairs: List<RepairTicket>,
    trackingResult: PublicTracking?,
    trackingSearched: Boolean,
    onLookupPublic: (String, String) -> Unit,
    onSelectRepair: (RepairTicket) -> Unit,
) {
    var staffQuery by remember { mutableStateOf("") }
    var pubTicket by remember { mutableStateOf("") }
    var pubLast4 by remember { mutableStateOf("") }

    val staffMatches = remember(repairs, staffQuery) {
        if (staffQuery.isBlank()) emptyList()
        else repairs.filter {
            it.ticketNumber.contains(staffQuery, true) ||
                it.customerMobile.contains(staffQuery, true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Staff Quick Lookup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = staffQuery,
                        onValueChange = { staffQuery = it },
                        label = { Text("Ticket # or Customer Mobile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        items(staffMatches, key = { it.id }) { ticket ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectRepair(ticket) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(ticket.ticketNumber, fontWeight = FontWeight.Bold, color = RoyalBlue)
                        Text("${ticket.deviceSummary} • ${ticket.customerName}", style = MaterialTheme.typography.bodySmall)
                    }
                    RepairStatusPill(ticket.statusEnum)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Customer Portal Check (No PII)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = pubTicket,
                        onValueChange = { pubTicket = it.uppercase() },
                        label = { Text("Ticket # (e.g. RP-1001)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pubLast4,
                        onValueChange = { pubLast4 = it.filter(Char::isDigit).take(4) },
                        label = { Text("Mobile Last 4 Digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onLookupPublic(pubTicket, pubLast4) },
                        enabled = pubTicket.isNotBlank() && pubLast4.length == 4,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Verify Customer View") }

                    if (trackingResult != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(trackingResult.ticketNumber, fontWeight = FontWeight.Bold)
                                    RepairStatusPill(RepairStatus.fromString(trackingResult.status))
                                }
                                Text(trackingResult.deviceSummary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Due: ${BillingCalculator.formatMoney(trackingResult.balanceDue)}")
                                    PaymentStatusPill(PaymentStatus.fromString(trackingResult.paymentStatus))
                                }
                            }
                        }
                    } else if (trackingSearched) {
                        Text("No ticket found for that combination.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsSubScreen(
    shop: Shop,
    summary: DashboardSummary,
    repairs: List<RepairTicket>,
    technicians: List<TechnicianProfile>,
    inventoryTx: List<InventoryTransaction>,
) {
    val context = LocalContext.current
    val cur = shop.currencySymbol.ifBlank { "$" }
    val partsConsumedQty = inventoryTx.filter { it.txType == InventoryTxType.REPAIR_CONSUME.name }.sumOf { -it.quantityDelta }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard("Daily Revenue", BillingCalculator.formatMoney(summary.todaysRevenue, cur), "Today", Icons.Rounded.Analytics, SuccessGreen, Modifier.weight(1f))
                KpiMetricCard("Weekly Revenue", BillingCalculator.formatMoney(summary.weeklyRevenue, cur), "Last 7 days", Icons.Rounded.Analytics, RoyalBlue, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard("Monthly Revenue", BillingCalculator.formatMoney(summary.monthlyRevenue, cur), "Last 30 days", Icons.Rounded.Analytics, CyanAccent, Modifier.weight(1f))
                KpiMetricCard("Est. Net Profit", BillingCalculator.formatMoney(summary.estimatedProfit, cur), "Exp: ${BillingCalculator.formatMoney(summary.totalExpenses, cur)}", Icons.Rounded.Analytics, NavyPrimary, Modifier.weight(1f))
            }
        }
        item {
            RevenueBarChart(points = summary.revenueTrend, currencySymbol = cur)
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Operational Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Total Repairs: ${summary.totalRepairs}  |  Completed/Ready: ${summary.completedRepairs + summary.deliveredRepairs}")
                    Text("Pending: ${summary.pendingRepairs}  |  Cancelled/Rejected: ${summary.cancelledRepairs}")
                    Text("Outstanding Receivables: ${BillingCalculator.formatMoney(summary.pendingPayments, cur)}")
                    Text("Spare Parts Consumed: $partsConsumedQty units")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val csv = buildString {
                                appendLine("RepairPilot Analytics Report - ${shop.shopName}")
                                appendLine("Total Revenue,${summary.totalRevenue}")
                                appendLine("Total Expenses,${summary.totalExpenses}")
                                appendLine("Estimated Profit,${summary.estimatedProfit}")
                                appendLine("Outstanding Payments,${summary.pendingPayments}")
                                appendLine("Total Repairs,${summary.totalRepairs}")
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${shop.shopName} Analytics Report")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(share, "Export Report"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Analytics Report")
                    }
                }
            }
        }
        item {
            Text("Technician Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(technicians, key = { it.id }) { tech ->
            val completedByTech = repairs.count {
                it.assignedTechUid == tech.id &&
                    it.statusEnum in listOf(RepairStatus.REPAIR_COMPLETED, RepairStatus.READY_FOR_PICKUP, RepairStatus.DELIVERED)
            }
            val activeByTech = repairs.count {
                it.assignedTechUid == tech.id && !it.statusEnum.isTerminal
            }
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(tech.name, fontWeight = FontWeight.SemiBold)
                        Text(tech.specialty, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "$completedByTech done • $activeByTech active",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TechniciansSubScreen(
    role: StaffRole,
    technicians: List<TechnicianProfile>,
    members: List<ShopMember>,
    invites: List<ShopInvite>,
    onAddTechnician: (String, String, String) -> Unit,
    onCreateInvite: (String, StaffRole, String) -> Unit,
    onSyncTeam: () -> Unit,
) {
    var showAddTech by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (role == StaffRole.OWNER) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAddTech = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Tech")
                    }
                    OutlinedButton(onClick = { showInviteDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("Invite Staff")
                    }
                }
            }
        }
        item {
            Text("Technicians (${technicians.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(technicians, key = { it.id }) { tech ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(tech.name, fontWeight = FontWeight.SemiBold)
                        Text("${tech.specialty} • ${tech.phone}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Jobs: ${tech.assignedCount}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shop Members (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (role == StaffRole.OWNER) {
                    TextButton(onClick = onSyncTeam) { Text("Sync Access") }
                }
            }
        }
        items(members, key = { it.id }) { m ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(m.displayName, fontWeight = FontWeight.SemiBold)
                        Text(m.email, style = MaterialTheme.typography.bodySmall)
                    }
                    RolePill(StaffRole.fromString(m.role))
                }
            }
        }
        if (invites.isNotEmpty()) {
            item { Text("Staff Invite Codes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(invites, key = { it.id }) { inv ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(inv.id, style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono), fontWeight = FontWeight.Bold)
                            Text("${inv.staffName} • ${inv.role}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (inv.active) "Active" else "Redeemed", color = if (inv.active) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAddTech) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var specialty by remember { mutableStateOf("Board & Display Repair") }
        AlertDialog(
            onDismissRequest = { showAddTech = false },
            title = { Text("Add Technician") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                    OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Specialty") })
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddTechnician(name, phone, specialty); showAddTech = false },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddTech = false }) { Text("Cancel") } }
        )
    }

    if (showInviteDialog) {
        var staffName by remember { mutableStateOf("") }
        var inviteRole by remember { mutableStateOf(StaffRole.TECHNICIAN) }
        var specialty by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Generate Staff Invite Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = staffName, onValueChange = { staffName = it }, label = { Text("Staff Name *") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(StaffRole.TECHNICIAN, StaffRole.RECEPTIONIST).forEach { r ->
                            FilterChip(selected = inviteRole == r, onClick = { inviteRole = r }, label = { Text(r.label) })
                        }
                    }
                    OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Role / Specialty") })
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateInvite(staffName, inviteRole, specialty); showInviteDialog = false },
                    enabled = staffName.isNotBlank()
                ) { Text("Generate Code") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ExpensesSubScreen(
    shop: Shop,
    expenses: List<Expense>,
    onAddExpense: (String, String, Double, String) -> Unit,
) {
    val cur = shop.currencySymbol.ifBlank { "$" }
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Shop Expense")
            }
        }
        items(expenses, key = { it.id }) { exp ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(exp.title, fontWeight = FontWeight.SemiBold)
                        Text(exp.category, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = BillingCalculator.formatMoney(exp.amount, cur),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("Parts Procurement") }
        var amt by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Log Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") })
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") })
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddExpense(title, cat, amt.toDoubleOrNull() ?: 0.0, note); showAdd = false },
                    enabled = title.isNotBlank() && (amt.toDoubleOrNull() ?: 0.0) > 0.0
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShopSettingsSubScreen(
    shop: Shop,
    onSave: (String, String, String, String, Double) -> Unit,
) {
    var name by remember(shop) { mutableStateOf(shop.shopName) }
    var phone by remember(shop) { mutableStateOf(shop.phone) }
    var address by remember(shop) { mutableStateOf(shop.address) }
    var currency by remember(shop) { mutableStateOf(shop.currencySymbol) }
    var tax by remember(shop) { mutableStateOf(shop.taxRatePercent.toString()) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = currency, onValueChange = { currency = it.take(4) }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Default Tax %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Button(
            onClick = { onSave(name, phone, address, currency, tax.toDoubleOrNull() ?: 8.5) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save Shop Settings") }
    }
}
