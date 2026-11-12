package com.example.ui.repairs

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Customer
import com.example.model.Device
import com.example.model.RepairNote
import com.example.model.RepairPartUsage
import com.example.model.RepairPriority
import com.example.model.RepairStatus
import com.example.model.RepairStatusHistory
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.SparePart
import com.example.model.StaffRole
import com.example.model.TechnicianProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PriorityPill
import com.example.ui.components.RepairStatusPill
import com.example.ui.components.RepairWorkflowStepper
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.util.BillingCalculator
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RepairsListScreen(
    shop: Shop,
    role: StaffRole,
    currentUserId: String,
    repairs: List<RepairTicket>,
    customers: List<Customer>,
    devices: List<Device>,
    technicians: List<TechnicianProfile>,
    showCreateSheetInitially: Boolean = false,
    onDismissCreateInitial: () -> Unit = {},
    onSelectRepair: (RepairTicket) -> Unit,
    onCreateTicket: (
        customer: Customer,
        device: Device,
        problem: String,
        diagnosis: String,
        estCost: Double,
        estDate: String,
        priority: RepairPriority,
        intakeCondition: String,
        tech: TechnicianProfile?,
        labour: Double,
        photos: List<String>,
    ) -> Unit,
    onQuickAddCustomer: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<RepairStatus?>(null) }
    var onlyMine by remember { mutableStateOf(role == StaffRole.TECHNICIAN) }
    var showCreateSheet by remember { mutableStateOf(showCreateSheetInitially) }

    val filtered = remember(repairs, search, statusFilter, onlyMine, currentUserId) {
        repairs.filter { t ->
            val matchesQuery = search.isBlank() ||
                t.ticketNumber.contains(search, true) ||
                t.customerName.contains(search, true) ||
                t.customerMobile.contains(search, true) ||
                t.deviceSummary.contains(search, true)
            val matchesStatus = statusFilter == null || t.statusEnum == statusFilter
            val matchesMine = !onlyMine || t.assignedTechUid.contains(currentUserId) || t.assignedTechUid.isBlank()
            matchesQuery && matchesStatus && matchesMine
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (role != StaffRole.TECHNICIAN) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    modifier = Modifier.testTag("fab_new_repair")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create Repair Ticket")
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search ticket #, customer, mobile, device...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("repairs_search_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (role == StaffRole.TECHNICIAN) {
                    FilterChip(
                        selected = onlyMine,
                        onClick = { onlyMine = !onlyMine },
                        label = { Text("My Bench") }
                    )
                }
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${repairs.size})") }
                )
                RepairStatus.entries.forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = if (statusFilter == st) null else st },
                        label = { Text(st.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                EmptyStateView(
                    title = "No matching repair tickets",
                    subtitle = "Adjust your filters or create a new repair intake ticket.",
                    actionLabel = if (role != StaffRole.TECHNICIAN) "New Repair Ticket" else null,
                    onAction = if (role != StaffRole.TECHNICIAN) ({ showCreateSheet = true }) else null
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { ticket ->
                        RepairTicketCard(
                            ticket = ticket,
                            currencySymbol = shop.currencySymbol,
                            onClick = { onSelectRepair(ticket) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateRepairTicketSheet(
            customers = customers,
            devices = devices,
            technicians = technicians,
            onDismiss = {
                showCreateSheet = false
                onDismissCreateInitial()
            },
            onCreate = { cust, dev, prob, diag, estCost, estDate, prio, cond, tech, labour, photos ->
                onCreateTicket(cust, dev, prob, diag, estCost, estDate, prio, cond, tech, labour, photos)
                showCreateSheet = false
                onDismissCreateInitial()
            },
            onQuickAddCustomer = onQuickAddCustomer,
        )
    }
}

@Composable
private fun RepairTicketCard(
    ticket: RepairTicket,
    currencySymbol: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("repair_card_${ticket.ticketNumber}"),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ticket.deviceSummary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${ticket.customerName}  •  ${ticket.customerMobile}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ticket.problemDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (ticket.assignedTechName.isNotBlank()) "Tech: ${ticket.assignedTechName}" else "Unassigned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total ${BillingCalculator.formatMoney(ticket.totalAmount, currencySymbol)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRepairTicketSheet(
    customers: List<Customer>,
    devices: List<Device>,
    technicians: List<TechnicianProfile>,
    onDismiss: () -> Unit,
    onCreate: (Customer, Device, String, String, Double, String, RepairPriority, String, TechnicianProfile?, Double, List<String>) -> Unit,
    onQuickAddCustomer: (String, String) -> Unit,
) {
    var selectedCustomer by remember(customers) { mutableStateOf(customers.firstOrNull()) }
    val customerDevices = remember(devices, selectedCustomer) {
        devices.filter { it.customerId == selectedCustomer?.id }
    }
    var selectedDevice by remember(customerDevices) { mutableStateOf(customerDevices.firstOrNull()) }
    var selectedTech by remember(technicians) { mutableStateOf<TechnicianProfile?>(technicians.firstOrNull()) }
    var priority by remember { mutableStateOf(RepairPriority.NORMAL) }
    var problem by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var estCost by remember { mutableStateOf("120") }
    var labour by remember { mutableStateOf("40") }
    var estDate by remember { mutableStateOf("2026-11-15") }
    var intakeCond by remember { mutableStateOf("Minor frame wear, powers on") }
    val photoUris = remember { mutableStateListOf<String>() }

    var custExpanded by remember { mutableStateOf(false) }
    var devExpanded by remember { mutableStateOf(false) }
    var techExpanded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> uri?.let { photoUris.add(it.toString()) } }
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create Repair Ticket",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (customers.isEmpty() || devices.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Register at least one customer and device in the Customers tab first before creating a ticket.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Customer Dropdown
            ExposedDropdownMenuBox(
                expanded = custExpanded,
                onExpandedChange = { custExpanded = !custExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCustomer?.let { "${it.name} (${it.mobile})" } ?: "Select Customer",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Customer *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = custExpanded, onDismissRequest = { custExpanded = false }) {
                    customers.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} (${c.mobile})") },
                            onClick = {
                                selectedCustomer = c
                                selectedDevice = devices.firstOrNull { it.customerId == c.id }
                                custExpanded = false
                            }
                        )
                    }
                }
            }

            // Device Dropdown
            ExposedDropdownMenuBox(
                expanded = devExpanded,
                onExpandedChange = { devExpanded = !devExpanded }
            ) {
                OutlinedTextField(
                    value = selectedDevice?.let { "${it.brand} ${it.model}" } ?: "Select Customer Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = devExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = devExpanded, onDismissRequest = { devExpanded = false }) {
                    customerDevices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text("${d.brand} ${d.model} (${d.color.ifBlank { d.deviceType }})") },
                            onClick = {
                                selectedDevice = d
                                devExpanded = false
                            }
                        )
                    }
                }
            }

            // Technician Dropdown
            ExposedDropdownMenuBox(
                expanded = techExpanded,
                onExpandedChange = { techExpanded = !techExpanded }
            ) {
                OutlinedTextField(
                    value = selectedTech?.name ?: "Assign Technician (Optional)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assigned Technician") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = techExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = techExpanded, onDismissRequest = { techExpanded = false }) {
                    technicians.filter { it.active }.forEach { t ->
                        DropdownMenuItem(
                            text = { Text("${t.name} • ${t.specialty}") },
                            onClick = {
                                selectedTech = t
                                techExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = problem,
                onValueChange = { problem = it },
                label = { Text("Problem Description *") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = diagnosis,
                onValueChange = { diagnosis = it },
                label = { Text("Initial Diagnosis") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = intakeCond,
                onValueChange = { intakeCond = it },
                label = { Text("Intake Device Condition") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = estCost,
                    onValueChange = { estCost = it },
                    label = { Text("Est. Cost") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = labour,
                    onValueChange = { labour = it },
                    label = { Text("Labour Fee") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = estDate,
                    onValueChange = { estDate = it },
                    label = { Text("Est. Date") },
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepairPriority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.label) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Photo (${photoUris.size})")
                }
            }

            Button(
                onClick = {
                    val c = selectedCustomer ?: return@Button
                    val d = selectedDevice ?: return@Button
                    onCreate(
                        c,
                        d,
                        problem,
                        diagnosis,
                        estCost.toDoubleOrNull() ?: 0.0,
                        estDate,
                        priority,
                        intakeCond,
                        selectedTech,
                        labour.toDoubleOrNull() ?: 0.0,
                        photoUris.toList()
                    )
                },
                enabled = selectedCustomer != null && selectedDevice != null && problem.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_repair_ticket")
            ) {
                Text("Create Repair Ticket")
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepairDetailScreen(
    shop: Shop,
    role: StaffRole,
    repair: RepairTicket,
    history: List<RepairStatusHistory>,
    notes: List<RepairNote>,
    partsUsed: List<RepairPartUsage>,
    availableParts: List<SparePart>,
    technicians: List<TechnicianProfile>,
    onBack: () -> Unit,
    onTransitionStatus: (RepairStatus, String) -> Unit,
    onAssignTech: (TechnicianProfile) -> Unit,
    onAddNote: (String, Boolean) -> Unit,
    onConsumePart: (SparePart, Long) -> Unit,
    onOpenBillingForTicket: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val cur = shop.currencySymbol.ifBlank { "$" }
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    var pendingStatus by remember { mutableStateOf<RepairStatus?>(null) }
    var statusNote by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteContent by remember { mutableStateOf("") }
    var noteIsDiagnosis by remember { mutableStateOf(false) }
    var showPartDialog by remember { mutableStateOf(false) }
    var selectedPart by remember(availableParts) { mutableStateOf(availableParts.firstOrNull { it.stockQty > 0 }) }
    var partQtyInput by remember { mutableStateOf("1") }
    var showTechDialog by remember { mutableStateOf(false) }

    val nextStatuses = remember(repair.status, role) {
        val base = repair.statusEnum.allowedNextStatuses()
        if (role == StaffRole.OWNER && base.isEmpty() && repair.statusEnum.isTerminal) {
            listOf(RepairStatus.IN_PROGRESS, RepairStatus.READY_FOR_PICKUP)
        } else {
            base
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = repair.ticketNumber,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = repair.deviceSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (role != StaffRole.TECHNICIAN) {
                        IconButton(onClick = onOpenBillingForTicket) {
                            Icon(Icons.Rounded.ReceiptLong, contentDescription = "Invoice & Billing")
                        }
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // Overview Card
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RepairStatusPill(repair.statusEnum)
                            PriorityPill(repair.priorityEnum)
                        }
                        RepairWorkflowStepper(current = repair.statusEnum)
                        HorizontalDivider()
                        Text("Customer: ${repair.customerName} (${repair.customerMobile})", fontWeight = FontWeight.SemiBold)
                        Text("Reported Issue: ${repair.problemDescription}", style = MaterialTheme.typography.bodyMedium)
                        if (repair.initialDiagnosis.isNotBlank()) {
                            Text("Diagnosis: ${repair.initialDiagnosis}", style = MaterialTheme.typography.bodyMedium, color = RoyalBlue)
                        }
                        if (repair.intakeCondition.isNotBlank()) {
                            Text("Intake Condition: ${repair.intakeCondition}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tech: ${repair.assignedTechName.ifBlank { "Unassigned" }}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Total ${BillingCalculator.formatMoney(repair.totalAmount, cur)} (Paid ${BillingCalculator.formatMoney(repair.paidAmount, cur)})",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (repair.photoUris.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(repair.photoUris) { uriStr ->
                                    AsyncImage(
                                        model = uriStr,
                                        contentDescription = "Repair intake photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status Transitions & Actions
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Workflow Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (nextStatuses.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                nextStatuses.forEach { target ->
                                    OutlinedButton(
                                        onClick = {
                                            statusNote = ""
                                            pendingStatus = target
                                        },
                                        modifier = Modifier.testTag("transition_${target.name}")
                                    ) {
                                        Text("Move to ${target.label}")
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Ticket is in terminal state (${repair.statusEnum.label}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showNoteDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Note")
                            }
                            if (role != StaffRole.RECEPTIONIST) {
                                OutlinedButton(
                                    onClick = { showPartDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use Part")
                                }
                            }
                        }
                        if (role != StaffRole.TECHNICIAN) {
                            OutlinedButton(
                                onClick = { showTechDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Assign / Reassign Technician")
                            }
                        }
                    }
                }
            }

            // Spare Parts Consumed
            item {
                Text("Spare Parts Used (${partsUsed.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (partsUsed.isEmpty()) {
                item {
                    Text(
                        text = "No spare parts consumed yet. Labour: ${BillingCalculator.formatMoney(repair.labourCharge, cur)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(partsUsed, key = { it.id }) { used ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${used.quantity}x ${used.partName}", fontWeight = FontWeight.SemiBold)
                                Text("SKU: ${used.sku}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = BillingCalculator.formatMoney(used.lineTotal, cur),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Notes & Diagnosis
            item {
                Text("Diagnosis & Repair Notes (${notes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(notes, key = { it.id }) { note ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (note.isDiagnosis) "DIAGNOSIS • ${note.authorName}" else "NOTE • ${note.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (note.isDiagnosis) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = note.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Status History Timeline
            item {
                Text("Status Audit History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(history, key = { it.id }) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${event.fromStatus} -> ${RepairStatus.fromString(event.toStatus).label}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (event.note.isNotBlank()) {
                            Text(event.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "By ${event.changedByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = event.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Now",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // Status Transition Confirmation Dialog
    if (pendingStatus != null) {
        AlertDialog(
            onDismissRequest = { pendingStatus = null },
            title = { Text("Move to ${pendingStatus!!.label}?") },
            text = {
                OutlinedTextField(
                    value = statusNote,
                    onValueChange = { statusNote = it },
                    label = { Text("Update note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onTransitionStatus(pendingStatus!!, statusNote)
                    pendingStatus = null
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStatus = null }) { Text("Cancel") }
            }
        )
    }

    // Add Note Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add Repair / Diagnosis Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = noteIsDiagnosis, onCheckedChange = { noteIsDiagnosis = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark as Official Diagnosis")
                    }
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Details *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddNote(noteContent, noteIsDiagnosis)
                        noteContent = ""
                        showNoteDialog = false
                    },
                    enabled = noteContent.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    // Consume Spare Part Dialog
    if (showPartDialog) {
        AlertDialog(
            onDismissRequest = { showPartDialog = false },
            title = { Text("Consume Spare Part") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (availableParts.none { it.stockQty > 0 }) {
                        Text("No in-stock spare parts available in inventory.")
                    } else {
                        availableParts.filter { it.stockQty > 0 }.take(6).forEach { part ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selectedPart?.id == part.id) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .clickable { selectedPart = part }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(part.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${part.stockQty} left • ${BillingCalculator.formatMoney(part.sellingPrice, cur)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        OutlinedTextField(
                            value = partQtyInput,
                            onValueChange = { partQtyInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = selectedPart ?: return@Button
                        val qty = partQtyInput.toLongOrNull() ?: 1L
                        onConsumePart(p, qty)
                        showPartDialog = false
                    },
                    enabled = selectedPart != null && (partQtyInput.toLongOrNull() ?: 0L) > 0L
                ) { Text("Deduct & Add") }
            },
            dismissButton = { TextButton(onClick = { showPartDialog = false }) { Text("Cancel") } }
        )
    }

    // Assign Technician Dialog
    if (showTechDialog) {
        AlertDialog(
            onDismissRequest = { showTechDialog = false },
            title = { Text("Assign Technician") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    technicians.filter { it.active }.forEach { tech ->
                        OutlinedButton(
                            onClick = {
                                onAssignTech(tech)
                                showTechDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${tech.name} (${tech.specialty})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTechDialog = false }) { Text("Close") } }
        )
    }
}
