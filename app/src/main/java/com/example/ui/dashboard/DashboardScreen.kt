package com.example.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Payment
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.SparePart
import com.example.model.StaffRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.RepairStatusPill
import com.example.ui.components.RevenueBarChart
import com.example.ui.components.RolePill
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.BillingCalculator
import com.example.viewmodel.DashboardSummary

@Composable
fun DashboardScreen(
    shop: Shop,
    role: StaffRole,
    summary: DashboardSummary,
    repairs: List<RepairTicket>,
    payments: List<Payment>,
    lowStockParts: List<SparePart>,
    onOpenNewTicket: () -> Unit,
    onOpenAddCustomer: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenRepairs: () -> Unit,
    onSelectRepair: (RepairTicket) -> Unit,
    onOpenBilling: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur = shop.currencySymbol.ifBlank { "$" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(NavyPrimary, Color(0xFF1A4680))))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_shop_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.32f)
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            RolePill(role = role)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = shop.shopName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Code ${shop.shopCode}  •  Tax ${shop.taxRatePercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("dashboard_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (summary.unreadNotifications > 0) {
                                        Badge { Text("${summary.unreadNotifications}") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Action Buttons (Role-aware)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (role != StaffRole.TECHNICIAN) {
                            item {
                                QuickActionChip(
                                    label = "New Ticket",
                                    icon = Icons.Rounded.Add,
                                    onClick = onOpenNewTicket,
                                    tag = "quick_new_ticket"
                                )
                            }
                            item {
                                QuickActionChip(
                                    label = "Add Customer",
                                    icon = Icons.Rounded.PersonAdd,
                                    onClick = onOpenAddCustomer,
                                    tag = "quick_add_customer"
                                )
                            }
                        }
                        item {
                            QuickActionChip(
                                label = "Track Status",
                                icon = Icons.Rounded.QrCodeScanner,
                                onClick = onOpenTracking,
                                tag = "quick_track"
                            )
                        }
                        if (role != StaffRole.TECHNICIAN) {
                            item {
                                QuickActionChip(
                                    label = "Record Payment",
                                    icon = Icons.Rounded.Payments,
                                    onClick = onOpenBilling,
                                    tag = "quick_payment"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Low-Stock Warning Banner
        if (lowStockParts.isNotEmpty()) {
            item {
                Surface(
                    color = WarningAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInventory() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Low Stock",
                            tint = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${lowStockParts.size} Low-Stock Spare Part(s)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lowStockParts.take(2).joinToString(", ") { "${it.name} (${it.stockQty})" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelLarge,
                            color = RoyalBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // KPI Grid (2 columns)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Total Repairs",
                        value = "${summary.totalRepairs}",
                        subtitle = "${summary.pendingRepairs} pending intake",
                        icon = Icons.Rounded.Build,
                        accentColor = NavyPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "In Progress",
                        value = "${summary.inProgressRepairs}",
                        subtitle = "${summary.completedRepairs} ready/completed",
                        icon = Icons.Rounded.HourglassTop,
                        accentColor = RoyalBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Completed",
                        value = "${summary.completedRepairs}",
                        subtitle = "${summary.deliveredRepairs} delivered",
                        icon = Icons.Rounded.CheckCircle,
                        accentColor = CyanAccent,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Delivered",
                        value = "${summary.deliveredRepairs}",
                        subtitle = "${summary.cancelledRepairs} closed/rejected",
                        icon = Icons.Rounded.DeliveryDining,
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (role != StaffRole.TECHNICIAN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiMetricCard(
                            title = "Today's Revenue",
                            value = BillingCalculator.formatMoney(summary.todaysRevenue, cur),
                            subtitle = "7d: ${BillingCalculator.formatMoney(summary.weeklyRevenue, cur)}",
                            icon = Icons.Rounded.AccountBalanceWallet,
                            accentColor = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Pending Payments",
                            value = BillingCalculator.formatMoney(summary.pendingPayments, cur),
                            subtitle = "${summary.lowStockCount} low-stock SKUs",
                            icon = Icons.Rounded.Inventory2,
                            accentColor = if (summary.pendingPayments > 0) ErrorRed else RoyalBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Revenue Trend Chart (Owner / Receptionist)
        if (role != StaffRole.TECHNICIAN) {
            item {
                RevenueBarChart(
                    points = summary.revenueTrend,
                    currencySymbol = cur
                )
            }
        }

        // Recent Repair Requests
        item {
            SectionHeader(
                title = "Recent Repair Tickets",
                actionLabel = "See All",
                onAction = onOpenRepairs
            )
        }

        if (repairs.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No repair tickets yet",
                    subtitle = "Register a customer & device or create your first repair ticket to start tracking.",
                    actionLabel = if (role != StaffRole.TECHNICIAN) "Create Repair Ticket" else null,
                    onAction = if (role != StaffRole.TECHNICIAN) onOpenNewTicket else null
                )
            }
        } else {
            items(repairs.take(5), key = { it.id }) { ticket ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRepair(ticket) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = ticket.ticketNumber,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBlue
                                )
                                PriorityPill(ticket.priorityEnum)
                            }
                            RepairStatusPill(ticket.statusEnum)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ticket.deviceSummary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${ticket.customerName}  •  ${ticket.problemDescription}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Recent Transactions (Owner / Receptionist)
        if (role != StaffRole.TECHNICIAN && payments.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent Transactions",
                    actionLabel = "Billing",
                    onAction = onOpenBilling
                )
            }
            items(payments.take(4), key = { it.id }) { payment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${payment.ticketNumber} • ${payment.customerName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${payment.method}${if (payment.isAdvance) " (Advance)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+${BillingCalculator.formatMoney(payment.amount, cur)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
