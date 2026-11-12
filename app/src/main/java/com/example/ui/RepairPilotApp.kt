package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.RepairOperationsRepository
import com.example.data.ShopRepository
import com.example.model.Shop
import com.example.model.StaffRole
import com.example.ui.auth.AuthAndOnboardingScreen
import com.example.ui.auth.ShopSetupAndPortalScreen
import com.example.ui.auth.signOutUser
import com.example.ui.billing.BillingAndPaymentsScreen
import com.example.ui.customers.CustomersAndDevicesScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.more.MoreHubContainer
import com.example.ui.more.MoreSubScreen
import com.example.ui.repairs.RepairDetailScreen
import com.example.ui.repairs.RepairsListScreen
import com.example.util.LocalNotifier
import com.example.viewmodel.RepairPilotViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val tag: String,
) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard, "nav_dashboard"),
    REPAIRS("Repairs", Icons.Rounded.Build, "nav_repairs"),
    CUSTOMERS("Customers", Icons.Rounded.People, "nav_customers"),
    INVENTORY("Inventory", Icons.Rounded.Inventory2, "nav_inventory"),
    MORE("More", Icons.Rounded.MoreHoriz, "nav_more"),
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun RepairPilotApp() {
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthAndOnboardingScreen(onAuthSuccess = {})
        return
    }

    key(user.uid) {
        AuthenticatedWorkspace(user = user)
    }
}

@Composable
private fun AuthenticatedWorkspace(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val db = remember(context) {
        val databaseId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(databaseId)
    }

    val vm: RepairPilotViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                RepairPilotViewModel(
                    shopRepo = ShopRepository(db, Firebase.auth),
                    opsRepo = RepairOperationsRepository(db, Firebase.auth),
                    currentUserId = user.uid,
                    userDisplayName = user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@")
                        ?: "Staff Member",
                    userEmail = user.email.orEmpty(),
                )
            }
        }
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val userAccountState by vm.userAccountState.collectAsStateWithLifecycle()
    val shopState by vm.shopState.collectAsStateWithLifecycle()
    val membershipState by vm.membershipState.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
            if (msg.startsWith("Status updated") || msg.startsWith("Ticket ")) {
                LocalNotifier.showNotification(context, "RepairPilot Update", msg)
            }
        }
    }

    val handleSignOut: () -> Unit = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = {}
        )
    }

    val resolvedAccount = (userAccountState as? UiState.Success)?.data
    val resolvedMembership = (membershipState as? UiState.Success)?.data
    val role = StaffRole.fromString(resolvedMembership?.role ?: resolvedAccount?.activeRole)

    when {
        userAccountState is UiState.Loading || shopState is UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading RepairPilot workspace...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        shopState is UiState.Error -> {
            val errMsg = (shopState as UiState.Error).message
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Workspace Sync Error",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = handleSignOut) {
                            Text("Sign Out & Retry")
                        }
                    }
                }
            }
        }

        else -> {
            val activeShop = (shopState as? UiState.Success)?.data
            if (activeShop == null) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { inner ->
                    Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                        ShopSetupAndPortalScreen(
                            viewModel = vm,
                            userDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Shop Partner",
                            onSignOut = handleSignOut
                        )
                        if (isBusy) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            } else {
                ShopWorkspaceShell(
                    shop = activeShop,
                    role = role,
                    vm = vm,
                    isBusy = isBusy,
                    snackbarHostState = snackbarHostState,
                    onSignOut = handleSignOut,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopWorkspaceShell(
    shop: Shop,
    role: StaffRole,
    vm: RepairPilotViewModel,
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    var moreSubScreen by rememberSaveable { mutableStateOf(MoreSubScreen.HUB) }
    var showBillingOverlay by rememberSaveable { mutableStateOf(false) }
    var openNewTicketOnRepairs by rememberSaveable { mutableStateOf(false) }
    var openAddCustomerOnCustomers by rememberSaveable { mutableStateOf(false) }

    val summary by vm.dashboardSummary.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val repairs by vm.repairs.collectAsStateWithLifecycle()
    val technicians by vm.technicians.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val invites by vm.myInvites.collectAsStateWithLifecycle()
    val spareParts by vm.spareParts.collectAsStateWithLifecycle()
    val inventoryTransactions by vm.inventoryTransactions.collectAsStateWithLifecycle()
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val auditLogs by vm.auditLogs.collectAsStateWithLifecycle()
    val selectedRepairId by vm.selectedRepairId.collectAsStateWithLifecycle()
    val selectedRepairHistory by vm.selectedRepairHistory.collectAsStateWithLifecycle()
    val selectedRepairNotes by vm.selectedRepairNotes.collectAsStateWithLifecycle()
    val selectedRepairPartsUsed by vm.selectedRepairPartsUsed.collectAsStateWithLifecycle()
    val trackingResult by vm.trackingResult.collectAsStateWithLifecycle()
    val trackingSearched by vm.trackingSearched.collectAsStateWithLifecycle()

    val selectedRepair = remember(repairs, selectedRepairId) {
        selectedRepairId?.let { id -> repairs.firstOrNull { it.id == id } }
    }
    val lowStockParts = remember(spareParts) { spareParts.filter { it.isLowStock } }

    if (selectedRepair != null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                RepairDetailScreen(
                    shop = shop,
                    role = role,
                    repair = selectedRepair,
                    history = selectedRepairHistory,
                    notes = selectedRepairNotes,
                    partsUsed = selectedRepairPartsUsed,
                    availableParts = spareParts,
                    technicians = technicians,
                    onBack = { vm.selectRepairForDetail(null) },
                    onTransitionStatus = { target, note ->
                        vm.transitionRepairStatus(selectedRepair, target, note)
                    },
                    onAssignTech = { tech -> vm.assignTechnician(selectedRepair, tech) },
                    onAddNote = { content, isDiag -> vm.addRepairNote(selectedRepair, content, isDiag) },
                    onConsumePart = { part, qty -> vm.consumePartOnRepair(selectedRepair, part, qty) },
                    onOpenBillingForTicket = {
                        vm.selectRepairForDetail(null)
                        showBillingOverlay = true
                    }
                )
            }
        }
        return
    }

    if (showBillingOverlay) {
        BackHandler { showBillingOverlay = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Billing & Invoices") },
                    navigationIcon = {
                        IconButton(onClick = { showBillingOverlay = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { inner ->
            BillingAndPaymentsScreen(
                shop = shop,
                role = role,
                repairs = repairs,
                invoices = invoices,
                payments = payments,
                onGenerateInvoice = { repair, labour, discount, tax, summaryNotes ->
                    vm.generateOrUpdateInvoice(repair, labour, discount, tax, summaryNotes)
                },
                onRecordPayment = { inv, repair, amount, method, isAdvance, ref ->
                    vm.recordPayment(inv, repair, amount, method, isAdvance, ref)
                },
                onShowToast = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                modifier = Modifier.padding(inner)
            )
        }
        return
    }

    BackHandler(enabled = currentTab != MainTab.DASHBOARD && moreSubScreen == MoreSubScreen.HUB) {
        currentTab = MainTab.DASHBOARD
    }

    val navigateToTab: (MainTab) -> Unit = { tab ->
        if (tab != MainTab.MORE) {
            moreSubScreen = MoreSubScreen.HUB
        }
        currentTab = tab
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MainTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab,
                            onClick = { navigateToTab(tab) },
                            icon = {
                                TabIconWithBadge(
                                    tab = tab,
                                    lowStockCount = lowStockParts.size,
                                    unreadCount = summary.unreadNotifications
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { navigateToTab(tab) },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            lowStockCount = lowStockParts.size,
                                            unreadCount = summary.unreadNotifications
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)

                    when (currentTab) {
                        MainTab.DASHBOARD -> {
                            DashboardScreen(
                                shop = shop,
                                role = role,
                                summary = summary,
                                repairs = repairs,
                                payments = payments,
                                lowStockParts = lowStockParts,
                                onOpenNewTicket = {
                                    openNewTicketOnRepairs = true
                                    currentTab = MainTab.REPAIRS
                                },
                                onOpenAddCustomer = {
                                    openAddCustomerOnCustomers = true
                                    currentTab = MainTab.CUSTOMERS
                                },
                                onOpenTracking = {
                                    moreSubScreen = MoreSubScreen.TRACKING
                                    currentTab = MainTab.MORE
                                },
                                onOpenNotifications = {
                                    moreSubScreen = MoreSubScreen.NOTIFICATIONS
                                    currentTab = MainTab.MORE
                                },
                                onOpenInventory = { currentTab = MainTab.INVENTORY },
                                onOpenRepairs = { currentTab = MainTab.REPAIRS },
                                onSelectRepair = { vm.selectRepairForDetail(it.id) },
                                onOpenBilling = { showBillingOverlay = true },
                                modifier = contentModifier
                            )
                        }

                        MainTab.REPAIRS -> {
                            RepairsListScreen(
                                shop = shop,
                                role = role,
                                currentUserId = vm.currentUserId,
                                repairs = repairs,
                                customers = customers,
                                devices = devices,
                                technicians = technicians,
                                showCreateSheetInitially = openNewTicketOnRepairs,
                                onDismissCreateInitial = { openNewTicketOnRepairs = false },
                                onSelectRepair = { vm.selectRepairForDetail(it.id) },
                                onCreateTicket = { cust, dev, prob, diag, estCost, estDate, prio, cond, tech, labour, photos ->
                                    vm.createRepairTicket(
                                        customer = cust,
                                        device = dev,
                                        problemDescription = prob,
                                        initialDiagnosis = diag,
                                        estimatedCost = estCost,
                                        estimatedCompletionDate = estDate,
                                        priority = prio,
                                        intakeCondition = cond,
                                        tech = tech,
                                        labourCharge = labour,
                                        photoUris = photos,
                                    )
                                },
                                onQuickAddCustomer = { name, mobile ->
                                    vm.addCustomer(name, mobile, "", "")
                                },
                                modifier = contentModifier
                            )
                        }

                        MainTab.CUSTOMERS -> {
                            CustomersAndDevicesScreen(
                                role = role,
                                customers = customers,
                                devices = devices,
                                repairs = repairs,
                                openAddCustomerInitially = openAddCustomerOnCustomers,
                                onDismissAddCustomerInitial = { openAddCustomerOnCustomers = false },
                                onAddCustomer = { name, mobile, email, addr ->
                                    vm.addCustomer(name, mobile, email, addr)
                                },
                                onUpdateCustomer = { cust, name, mobile, email, addr, active ->
                                    vm.updateCustomer(cust, name, mobile, email, addr, active)
                                },
                                onAddDevice = { cust, brand, model, imei, type, color, cond, acc, issue, photos ->
                                    vm.addDevice(cust, brand, model, imei, type, color, cond, acc, issue, photos)
                                },
                                onSelectRepair = { vm.selectRepairForDetail(it.id) },
                                modifier = contentModifier
                            )
                        }

                        MainTab.INVENTORY -> {
                            InventoryScreen(
                                shop = shop,
                                role = role,
                                parts = spareParts,
                                transactions = inventoryTransactions,
                                onAddPart = { name, sku, cat, qty, buy, sell, min, supName, supContact ->
                                    vm.addSparePart(name, sku, cat, qty, buy, sell, min, supName, supContact)
                                },
                                onAdjustStock = { part, qty, txType, note ->
                                    vm.adjustStock(part, qty, txType, note)
                                },
                                modifier = contentModifier
                            )
                        }

                        MainTab.MORE -> {
                            Box(modifier = contentModifier) {
                                MoreHubContainer(
                                    activeSubScreen = moreSubScreen,
                                    onNavigateSub = { moreSubScreen = it },
                                    shop = shop,
                                    role = role,
                                    summary = summary,
                                    repairs = repairs,
                                    technicians = technicians,
                                    members = members,
                                    invites = invites,
                                    expenses = expenses,
                                    inventoryTx = inventoryTransactions,
                                    notifications = notifications,
                                    auditLogs = auditLogs,
                                    trackingResult = trackingResult,
                                    trackingSearched = trackingSearched,
                                    onLookupTracking = { t, m -> vm.lookupPublicTracking(t, m) },
                                    onSelectRepair = { vm.selectRepairForDetail(it.id) },
                                    onAddTechnician = { name, phone, spec -> vm.addTechnician(name, phone, spec) },
                                    onCreateInvite = { staffName, r, spec ->
                                        vm.createStaffInvite(staffName, r, spec) {}
                                    },
                                    onSyncTeam = { vm.syncTeamAccess() },
                                    onAddExpense = { title, cat, amt, note -> vm.addExpense(title, cat, amt, note) },
                                    onMarkNotificationRead = { vm.markNotificationRead(it) },
                                    onSaveShopSettings = { name, phone, addr, cur, tax ->
                                        vm.updateShopSettings(name, phone, addr, cur, tax)
                                    },
                                    onOpenBilling = { showBillingOverlay = true },
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }

                    if (isBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: MainTab,
    lowStockCount: Int,
    unreadCount: Int,
) {
    val badgeCount = when (tab) {
        MainTab.INVENTORY -> lowStockCount
        MainTab.MORE -> unreadCount
        else -> 0
    }
    if (badgeCount > 0) {
        BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
            Icon(tab.icon, contentDescription = tab.label)
        }
    } else {
        Icon(tab.icon, contentDescription = tab.label)
    }
}
