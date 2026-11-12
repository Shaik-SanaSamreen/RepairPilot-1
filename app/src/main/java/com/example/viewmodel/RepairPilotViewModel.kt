package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RepairOperationsRepository
import com.example.data.ShopRepository
import com.example.model.AuditLog
import com.example.model.Customer
import com.example.model.Device
import com.example.model.Expense
import com.example.model.InventoryTransaction
import com.example.model.InventoryTxType
import com.example.model.Invoice
import com.example.model.Payment
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.PublicTracking
import com.example.model.RepairNote
import com.example.model.RepairPartUsage
import com.example.model.RepairPriority
import com.example.model.RepairStatus
import com.example.model.RepairStatusHistory
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.ShopInvite
import com.example.model.ShopMember
import com.example.model.ShopNotification
import com.example.model.SparePart
import com.example.model.StaffRole
import com.example.model.TechnicianProfile
import com.example.model.UserAccount
import com.example.util.BillingCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DailyRevenuePoint(
    val label: String,
    val amount: Double,
)

data class DashboardSummary(
    val totalRepairs: Int = 0,
    val pendingRepairs: Int = 0,
    val inProgressRepairs: Int = 0,
    val completedRepairs: Int = 0,
    val deliveredRepairs: Int = 0,
    val cancelledRepairs: Int = 0,
    val todaysRevenue: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val weeklyRevenue: Double = 0.0,
    val monthlyRevenue: Double = 0.0,
    val pendingPayments: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val lowStockCount: Int = 0,
    val unreadNotifications: Int = 0,
    val revenueTrend: List<DailyRevenuePoint> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RepairPilotViewModel(
    private val shopRepo: ShopRepository,
    private val opsRepo: RepairOperationsRepository,
    val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    private val _selectedRepairId = MutableStateFlow<String?>(null)
    val selectedRepairId: StateFlow<String?> = _selectedRepairId.asStateFlow()

    private val _trackingResult = MutableStateFlow<PublicTracking?>(null)
    val trackingResult: StateFlow<PublicTracking?> = _trackingResult.asStateFlow()

    private val _trackingSearched = MutableStateFlow(false)
    val trackingSearched: StateFlow<Boolean> = _trackingSearched.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepo.ensureUserAccount(currentUserId, userDisplayName, userEmail)
                .onFailure { emitToast(it.localizedMessage ?: "Failed to sync user profile") }
        }
    }

    private fun <T> Flow<T>.toSafeState(
        initial: UiState<T> = UiState.Loading,
        tag: String = "RepairPilotVM",
    ): StateFlow<UiState<T>> = this
        .map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(tag, "Stream error", root)
            emit(UiState.Error(root.localizedMessage ?: "Unable to load data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = initial,
        )

    val userAccountState: StateFlow<UiState<UserAccount?>> =
        shopRepo.observeUserAccount(currentUserId).toSafeState()

    private val activeShopIdFlow: Flow<String?> = userAccountState.map { state ->
        (state as? UiState.Success)?.data?.activeShopId?.takeIf { it.isNotBlank() }
    }

    val shopState: StateFlow<UiState<Shop?>> = activeShopIdFlow
        .flatMapLatest { shopId ->
            if (shopId == null) flowOf(null) else shopRepo.observeShop(shopId)
        }
        .toSafeState()

    val membershipState: StateFlow<UiState<ShopMember?>> = activeShopIdFlow
        .flatMapLatest { shopId ->
            if (shopId == null) flowOf(null) else shopRepo.observeMyMembership(shopId, currentUserId)
        }
        .toSafeState()

    val activeShop: Shop?
        get() = (shopState.value as? UiState.Success)?.data

    val currentRole: StaffRole
        get() {
            val memberRole = (membershipState.value as? UiState.Success)?.data?.role
            val acctRole = (userAccountState.value as? UiState.Success)?.data?.activeRole
            return StaffRole.fromString(memberRole ?: acctRole)
        }

    private fun <T> shopListFlow(block: (String) -> Flow<List<T>>): StateFlow<List<T>> =
        activeShopIdFlow
            .flatMapLatest { shopId ->
                if (shopId == null) flowOf(emptyList()) else block(shopId)
            }
            .catch { error ->
                val fe = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fe == null && error is CancellationException) throw error
                Log.w("RepairPilotVM", "Collection stream error", fe ?: error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val customers: StateFlow<List<Customer>> = shopListFlow { opsRepo.observeCustomers(it) }
    val devices: StateFlow<List<Device>> = shopListFlow { opsRepo.observeDevices(it) }
    val repairs: StateFlow<List<RepairTicket>> = shopListFlow { opsRepo.observeRepairs(it) }
    val technicians: StateFlow<List<TechnicianProfile>> = shopListFlow { shopRepo.observeTechnicians(it) }
    val members: StateFlow<List<ShopMember>> = shopListFlow { shopRepo.observeMembers(it) }
    val spareParts: StateFlow<List<SparePart>> = shopListFlow { opsRepo.observeSpareParts(it) }
    val inventoryTransactions: StateFlow<List<InventoryTransaction>> = shopListFlow { opsRepo.observeInventoryTransactions(it) }
    val invoices: StateFlow<List<Invoice>> = shopListFlow { opsRepo.observeInvoices(it) }
    val payments: StateFlow<List<Payment>> = shopListFlow { opsRepo.observePayments(it) }
    val expenses: StateFlow<List<Expense>> = shopListFlow { shopRepo.observeExpenses(it) }
    val notifications: StateFlow<List<ShopNotification>> = shopListFlow { shopRepo.observeNotifications(it) }
    val auditLogs: StateFlow<List<AuditLog>> = shopListFlow { shopRepo.observeAuditLogs(it) }

    val myInvites: StateFlow<List<ShopInvite>> =
        shopRepo.observeMyInvites(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedRepairHistory: StateFlow<List<RepairStatusHistory>> =
        combine(activeShopIdFlow, _selectedRepairId) { sId, rId -> sId to rId }
            .flatMapLatest { (sId, rId) ->
                if (sId == null || rId == null) flowOf(emptyList())
                else opsRepo.observeStatusHistory(sId, rId)
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedRepairNotes: StateFlow<List<RepairNote>> =
        combine(activeShopIdFlow, _selectedRepairId) { sId, rId -> sId to rId }
            .flatMapLatest { (sId, rId) ->
                if (sId == null || rId == null) flowOf(emptyList())
                else opsRepo.observeRepairNotes(sId, rId)
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedRepairPartsUsed: StateFlow<List<RepairPartUsage>> =
        combine(activeShopIdFlow, _selectedRepairId) { sId, rId -> sId to rId }
            .flatMapLatest { (sId, rId) ->
                if (sId == null || rId == null) flowOf(emptyList())
                else opsRepo.observeRepairPartsUsed(sId, rId)
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        repairs,
        payments,
        invoices,
        spareParts,
        combine(expenses, notifications) { exp, notif -> exp to notif },
    ) { repList, payList, invList, partList, (expList, notifList) ->
        val nowCal = Calendar.getInstance()
        val todayStart = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = todayStart - 6L * 24L * 3600_000L
        val monthStart = todayStart - 29L * 24L * 3600_000L

        val todayRev = payList.filter { (it.createdAt?.toDate()?.time ?: 0L) >= todayStart }.sumOf { it.amount }
        val weekRev = payList.filter { (it.createdAt?.toDate()?.time ?: 0L) >= weekStart }.sumOf { it.amount }
        val monthRev = payList.filter { (it.createdAt?.toDate()?.time ?: 0L) >= monthStart }.sumOf { it.amount }
        val totalRev = payList.sumOf { it.amount }
        val pendingBal = invList.filter { it.paymentStatus != PaymentStatus.PAID.name }.sumOf { it.balanceDue }
        val totalExp = expList.sumOf { it.amount }
        val estProfit = BillingCalculator.round2(totalRev - totalExp)

        val trend = (6 downTo 0).map { daysAgo ->
            val dayStart = todayStart - daysAgo * 24L * 3600_000L
            val dayEnd = dayStart + 24L * 3600_000L
            val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
            val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                else -> "Sun"
            }
            val amt = payList.filter {
                val ms = it.createdAt?.toDate()?.time ?: 0L
                ms in dayStart until dayEnd
            }.sumOf { it.amount }
            DailyRevenuePoint(dayName, BillingCalculator.round2(amt))
        }

        DashboardSummary(
            totalRepairs = repList.size,
            pendingRepairs = repList.count {
                it.statusEnum in listOf(
                    RepairStatus.RECEIVED,
                    RepairStatus.DIAGNOSING,
                    RepairStatus.AWAITING_CUSTOMER_APPROVAL,
                )
            },
            inProgressRepairs = repList.count {
                it.statusEnum in listOf(
                    RepairStatus.APPROVED,
                    RepairStatus.IN_PROGRESS,
                    RepairStatus.WAITING_FOR_PARTS,
                )
            },
            completedRepairs = repList.count {
                it.statusEnum in listOf(RepairStatus.REPAIR_COMPLETED, RepairStatus.READY_FOR_PICKUP)
            },
            deliveredRepairs = repList.count { it.statusEnum == RepairStatus.DELIVERED },
            cancelledRepairs = repList.count {
                it.statusEnum in listOf(RepairStatus.CANCELLED, RepairStatus.REJECTED, RepairStatus.UNABLE_TO_REPAIR)
            },
            todaysRevenue = BillingCalculator.round2(todayRev),
            totalRevenue = BillingCalculator.round2(totalRev),
            weeklyRevenue = BillingCalculator.round2(weekRev),
            monthlyRevenue = BillingCalculator.round2(monthRev),
            pendingPayments = BillingCalculator.round2(pendingBal),
            totalExpenses = BillingCalculator.round2(totalExp),
            estimatedProfit = estProfit,
            lowStockCount = partList.count { it.isLowStock },
            unreadNotifications = notifList.count { !it.isRead },
            revenueTrend = trend,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardSummary())

    fun selectRepairForDetail(repairId: String?) {
        _selectedRepairId.value = repairId
    }

    private fun emitToast(msg: String) {
        _toastMessage.tryEmit(msg)
    }

    private fun launchOp(successMsg: String? = null, block: suspend () -> Result<Any?>) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = block()
                res.onSuccess {
                    if (!successMsg.isNullOrBlank()) emitToast(successMsg)
                }.onFailure { err ->
                    emitToast(err.localizedMessage ?: "Operation failed")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun registerShop(
        shopName: String,
        shopCode: String,
        phone: String,
        address: String,
        currencySymbol: String,
        taxRatePercent: Double,
    ) = launchOp("Shop workspace created!") {
        shopRepo.createShop(shopName, shopCode, phone, address, currencySymbol, taxRatePercent)
    }

    fun updateShopSettings(
        shopName: String,
        phone: String,
        address: String,
        currencySymbol: String,
        taxRatePercent: Double,
    ) {
        val shop = activeShop ?: return
        launchOp("Shop settings saved") {
            shopRepo.updateShopSettings(shop, shopName, phone, address, currencySymbol, taxRatePercent)
        }
    }

    fun joinShopWithCode(code: String) = launchOp("Joined shop workspace!") {
        shopRepo.joinShopWithInvite(code)
    }

    fun addCustomer(name: String, mobile: String, email: String, address: String, onCreated: (String) -> Unit = {}) {
        val shop = activeShop ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                opsRepo.addCustomer(shop, name, mobile, email, address)
                    .onSuccess { id ->
                        emitToast("Customer registered")
                        onCreated(id)
                    }
                    .onFailure { emitToast(it.localizedMessage ?: "Failed to add customer") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateCustomer(customer: Customer, name: String, mobile: String, email: String, address: String, active: Boolean) {
        val shop = activeShop ?: return
        launchOp("Customer updated") {
            opsRepo.updateCustomer(shop.id, customer, name, mobile, email, address, active)
        }
    }

    fun addDevice(
        customer: Customer,
        brand: String,
        model: String,
        imeiOrSerial: String,
        deviceType: String,
        color: String,
        condition: String,
        accessories: String,
        reportedIssue: String,
        photoUris: List<String>,
        onCreated: (String) -> Unit = {},
    ) {
        val shop = activeShop ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                opsRepo.addDevice(shop, customer, brand, model, imeiOrSerial, deviceType, color, condition, accessories, reportedIssue, photoUris)
                    .onSuccess { id ->
                        emitToast("Device registered")
                        onCreated(id)
                    }
                    .onFailure { emitToast(it.localizedMessage ?: "Failed to add device") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createRepairTicket(
        customer: Customer,
        device: Device,
        problemDescription: String,
        initialDiagnosis: String,
        estimatedCost: Double,
        estimatedCompletionDate: String,
        priority: RepairPriority,
        intakeCondition: String,
        tech: TechnicianProfile?,
        labourCharge: Double,
        photoUris: List<String>,
        onCreated: (RepairTicket) -> Unit = {},
    ) {
        val shop = activeShop ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                opsRepo.createRepairTicket(
                    shop = shop,
                    customer = customer,
                    device = device,
                    problemDescription = problemDescription,
                    initialDiagnosis = initialDiagnosis,
                    estimatedCost = estimatedCost,
                    estimatedCompletionDate = estimatedCompletionDate,
                    priority = priority,
                    intakeCondition = intakeCondition,
                    assignedTechUid = tech?.id.orEmpty(),
                    assignedTechName = tech?.name.orEmpty(),
                    labourCharge = labourCharge,
                    photoUris = photoUris,
                    actorRole = currentRole,
                ).onSuccess { ticket ->
                    emitToast("Ticket ${ticket.ticketNumber} created")
                    onCreated(ticket)
                }.onFailure {
                    emitToast(it.localizedMessage ?: "Failed to create ticket")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun transitionRepairStatus(repair: RepairTicket, newStatus: RepairStatus, note: String) {
        val shop = activeShop ?: return
        launchOp("Status updated to ${newStatus.label}") {
            opsRepo.transitionRepairStatus(shop, repair, newStatus, note, currentRole)
        }
    }

    fun assignTechnician(repair: RepairTicket, tech: TechnicianProfile) {
        val shop = activeShop ?: return
        launchOp("Assigned ${tech.name}") {
            opsRepo.assignTechnician(shop, repair, tech.id, tech.name)
        }
    }

    fun addRepairNote(repair: RepairTicket, content: String, isDiagnosis: Boolean) {
        val shop = activeShop ?: return
        launchOp(if (isDiagnosis) "Diagnosis saved" else "Repair note added") {
            opsRepo.addRepairNote(shop, repair, content, isDiagnosis, currentRole)
        }
    }

    fun addTechnician(name: String, phone: String, specialty: String) {
        val shop = activeShop ?: return
        launchOp("Technician added") {
            shopRepo.addTechnicianProfile(shop, name, phone, specialty)
        }
    }

    fun createStaffInvite(staffName: String, role: StaffRole, specialty: String, onCode: (String) -> Unit) {
        val shop = activeShop ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                shopRepo.createStaffInvite(shop, staffName, role, specialty)
                    .onSuccess { code ->
                        emitToast("Invite code $code created")
                        onCode(code)
                    }
                    .onFailure { emitToast(it.localizedMessage ?: "Failed to create invite") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun syncTeamAccess() {
        val shop = activeShop ?: return
        launchOp("Team access synchronized") {
            shopRepo.syncMemberUidsAcrossShop(shop)
        }
    }

    fun addSparePart(
        name: String,
        sku: String,
        category: String,
        stockQty: Long,
        purchasePrice: Double,
        sellingPrice: Double,
        minStockLevel: Long,
        supplierName: String,
        supplierContact: String,
    ) {
        val shop = activeShop ?: return
        launchOp("Spare part added") {
            opsRepo.addSparePart(shop, name, sku, category, stockQty, purchasePrice, sellingPrice, minStockLevel, supplierName, supplierContact)
        }
    }

    fun adjustStock(part: SparePart, qty: Long, txType: InventoryTxType, note: String) {
        val shop = activeShop ?: return
        launchOp("${txType.label} recorded") {
            opsRepo.adjustPartStock(shop, part, qty, txType, note)
        }
    }

    fun consumePartOnRepair(repair: RepairTicket, part: SparePart, qty: Long) {
        val shop = activeShop ?: return
        launchOp("Added ${qty}x ${part.name} to ${repair.ticketNumber}") {
            opsRepo.consumePartOnRepair(shop, repair, part, qty)
        }
    }

    fun generateOrUpdateInvoice(
        repair: RepairTicket,
        labourCharge: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        lineItemsSummary: String,
    ) {
        val shop = activeShop ?: return
        launchOp("Invoice updated") {
            opsRepo.generateOrUpdateInvoice(shop, repair, labourCharge, discountAmount, taxRatePercent, lineItemsSummary)
        }
    }

    fun recordPayment(
        invoice: Invoice,
        repair: RepairTicket?,
        amount: Double,
        method: PaymentMethod,
        isAdvance: Boolean,
        referenceNumber: String,
    ) {
        val shop = activeShop ?: return
        launchOp("Payment recorded") {
            opsRepo.recordPayment(shop, invoice, repair, amount, method, isAdvance, referenceNumber)
        }
    }

    fun addExpense(title: String, category: String, amount: Double, note: String) {
        val shop = activeShop ?: return
        launchOp("Expense logged") {
            shopRepo.addExpense(shop, title, category, amount, note)
        }
    }

    fun markNotificationRead(id: String) {
        val shop = activeShop ?: return
        viewModelScope.launch {
            shopRepo.markNotificationRead(shop.id, id)
        }
    }

    fun lookupPublicTracking(ticketNumber: String, mobileLast4: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                shopRepo.lookupPublicTracking(ticketNumber, mobileLast4)
                    .onSuccess {
                        _trackingResult.value = it
                        _trackingSearched.value = true
                        if (it == null) emitToast("No matching repair ticket found")
                    }
                    .onFailure {
                        _trackingSearched.value = true
                        emitToast(it.localizedMessage ?: "Lookup failed")
                    }
            } finally {
                _isBusy.value = false
            }
        }
    }
}
