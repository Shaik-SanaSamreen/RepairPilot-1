package com.example.ui.customers

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Customer
import com.example.model.Device
import com.example.model.RepairTicket
import com.example.model.StaffRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RepairStatusPill
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoyalBlue

@Composable
fun CustomersAndDevicesScreen(
    role: StaffRole,
    customers: List<Customer>,
    devices: List<Device>,
    repairs: List<RepairTicket>,
    openAddCustomerInitially: Boolean = false,
    onDismissAddCustomerInitial: () -> Unit = {},
    onAddCustomer: (String, String, String, String) -> Unit,
    onUpdateCustomer: (Customer, String, String, String, String, Boolean) -> Unit,
    onAddDevice: (Customer, String, String, String, String, String, String, String, String, List<String>) -> Unit,
    onSelectRepair: (RepairTicket) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var showAddCustomer by remember { mutableStateOf(openAddCustomerInitially) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

    val filtered = remember(customers, search) {
        customers.filter {
            search.isBlank() ||
                it.name.contains(search, true) ||
                it.mobile.contains(search, true) ||
                it.email.contains(search, true)
        }
    }

    if (selectedCustomer != null) {
        val currentCust = customers.firstOrNull { it.id == selectedCustomer!!.id } ?: selectedCustomer!!
        CustomerDetailView(
            role = role,
            customer = currentCust,
            devices = devices.filter { it.customerId == currentCust.id },
            repairs = repairs.filter { it.customerId == currentCust.id },
            onBack = { selectedCustomer = null },
            onUpdateCustomer = { name, mob, email, addr, active ->
                onUpdateCustomer(currentCust, name, mob, email, addr, active)
            },
            onAddDevice = { brand, model, imei, type, color, cond, acc, issue, photos ->
                onAddDevice(currentCust, brand, model, imei, type, color, cond, acc, issue, photos)
            },
            onSelectRepair = onSelectRepair
        )
        return
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (role != StaffRole.TECHNICIAN) {
                FloatingActionButton(
                    onClick = { showAddCustomer = true },
                    modifier = Modifier.testTag("fab_add_customer")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Customer")
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
                placeholder = { Text("Search customers by name, phone, email...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                EmptyStateView(
                    title = "No customers yet",
                    subtitle = "Add your first customer to register their mobile devices and repair tickets.",
                    actionLabel = if (role != StaffRole.TECHNICIAN) "Add Customer" else null,
                    onAction = if (role != StaffRole.TECHNICIAN) ({ showAddCustomer = true }) else null
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { cust ->
                        val custDeviceCount = devices.count { it.customerId == cust.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomer = cust },
                            shape = RoundedCornerShape(16.dp),
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
                                        text = cust.name + if (!cust.active) " (Inactive)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = cust.mobile,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = RoyalBlue
                                    )
                                    if (cust.email.isNotBlank()) {
                                        Text(cust.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$custDeviceCount devices",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${cust.totalRepairs} repairs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomer) {
        CustomerFormDialog(
            initial = null,
            onDismiss = {
                showAddCustomer = false
                onDismissAddCustomerInitial()
            },
            onSave = { name, mob, email, addr, _ ->
                onAddCustomer(name, mob, email, addr)
                showAddCustomer = false
                onDismissAddCustomerInitial()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDetailView(
    role: StaffRole,
    customer: Customer,
    devices: List<Device>,
    repairs: List<RepairTicket>,
    onBack: () -> Unit,
    onUpdateCustomer: (String, String, String, String, Boolean) -> Unit,
    onAddDevice: (String, String, String, String, String, String, String, String, List<String>) -> Unit,
    onSelectRepair: (RepairTicket) -> Unit,
) {
    BackHandler(onBack = onBack)
    var showEditCustomer by remember { mutableStateOf(false) }
    var showAddDevice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (role != StaffRole.TECHNICIAN) {
                        IconButton(onClick = { showEditCustomer = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Customer")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Mobile: ${customer.mobile}", style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono))
                        if (customer.email.isNotBlank()) Text("Email: ${customer.email}")
                        if (customer.address.isNotBlank()) Text("Address: ${customer.address}")
                        Text("Status: ${if (customer.active) "Active" else "Deactivated"}")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Devices (${devices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (role != StaffRole.TECHNICIAN) {
                        OutlinedButton(onClick = { showAddDevice = true }, modifier = Modifier.testTag("add_device_button")) {
                            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register Device")
                        }
                    }
                }
            }

            items(devices, key = { it.id }) { dev ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("${dev.brand} ${dev.model}", fontWeight = FontWeight.Bold)
                        Text("${dev.deviceType} • ${dev.color.ifBlank { "Standard" }} • IMEI/SN: ${dev.imeiOrSerial.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Issue: ${dev.reportedIssue}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Text("Repair History (${repairs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(repairs, key = { it.id }) { ticket ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRepair(ticket) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.ticketNumber, fontWeight = FontWeight.Bold, color = RoyalBlue)
                            Text(ticket.deviceSummary, style = MaterialTheme.typography.bodyMedium)
                        }
                        RepairStatusPill(ticket.statusEnum)
                    }
                }
            }
        }
    }

    if (showEditCustomer) {
        CustomerFormDialog(
            initial = customer,
            onDismiss = { showEditCustomer = false },
            onSave = { name, mob, email, addr, active ->
                onUpdateCustomer(name, mob, email, addr, active)
                showEditCustomer = false
            }
        )
    }

    if (showAddDevice) {
        AddDeviceDialog(
            onDismiss = { showAddDevice = false },
            onSave = { brand, model, imei, type, color, cond, acc, issue, photos ->
                onAddDevice(brand, model, imei, type, color, cond, acc, issue, photos)
                showAddDevice = false
            }
        )
    }
}

@Composable
private fun CustomerFormDialog(
    initial: Customer?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var mobile by remember { mutableStateOf(initial?.mobile.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Register Customer" else "Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_mobile_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (active) "Customer Active" else "Deactivated")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, mobile, email, address, active) },
                enabled = name.isNotBlank() && mobile.trim().length >= 6,
                modifier = Modifier.testTag("save_customer_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String, List<String>) -> Unit,
) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf("Smartphone") }
    var color by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Good frame, cracked glass") }
    var accessories by remember { mutableStateOf("SIM Tray") }
    var issue by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<String>() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> uri?.let { photos.add(it.toString()) } }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Brand *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deviceType,
                        onValueChange = { deviceType = it },
                        label = { Text("Type") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Color") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = imei,
                    onValueChange = { imei = it },
                    label = { Text("IMEI / Serial (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("Device Condition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessories,
                    onValueChange = { accessories = it },
                    label = { Text("Accessories Received") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = issue,
                    onValueChange = { issue = it },
                    label = { Text("Reported Issue *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Device Photo (${photos.size})")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(brand, model, imei, deviceType, color, condition, accessories, issue, photos.toList()) },
                enabled = brand.isNotBlank() && model.isNotBlank() && issue.isNotBlank()
            ) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
