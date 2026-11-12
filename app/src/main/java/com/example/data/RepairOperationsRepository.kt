package com.example.data

import com.example.model.Customer
import com.example.model.Device
import com.example.model.InventoryTransaction
import com.example.model.InventoryTxType
import com.example.model.Invoice
import com.example.model.Payment
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.RepairNote
import com.example.model.RepairPartUsage
import com.example.model.RepairPriority
import com.example.model.RepairStatus
import com.example.model.RepairStatusHistory
import com.example.model.RepairTicket
import com.example.model.Shop
import com.example.model.SparePart
import com.example.model.StaffRole
import com.example.util.BillingCalculator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RepairOperationsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun requireUserName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Staff"

    private fun <T> observeShopCollection(
        shopId: String,
        collectionName: String,
        clazz: Class<T>,
        sortTransform: (List<T>) -> List<T> = { it },
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/$collectionName"
        emitAll(
            db.collection("shops").document(shopId).collection(collectionName)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    sortTransform(snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // ==================== CUSTOMERS ====================
    fun observeCustomers(shopId: String): Flow<List<Customer>> =
        observeShopCollection(shopId, "customers", Customer::class.java) { list ->
            list.sortedBy { it.name.lowercase() }
        }

    suspend fun addCustomer(
        shop: Shop,
        name: String,
        mobile: String,
        email: String,
        address: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanName = name.trim().take(100)
        val cleanMobile = mobile.trim().take(30)
        require(cleanName.isNotBlank() && cleanMobile.length >= 6) { "Valid customer name and mobile required" }

        // Prevent duplicate mobile within shop
        val existing = db.collection("shops").document(shop.id).collection("customers")
            .whereArrayContains("memberUids", uid)
            .whereEqualTo("mobile", cleanMobile)
            .get()
            .await()
        if (!existing.isEmpty) {
            throw IllegalArgumentException("Customer with mobile $cleanMobile already exists")
        }

        val ref = db.collection("shops").document(shop.id).collection("customers").document()
        ref.set(
            mapOf(
                "name" to cleanName,
                "mobile" to cleanMobile,
                "email" to email.trim().take(160),
                "address" to address.trim().take(250),
                "active" to true,
                "totalRepairs" to 0L,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/customers")
    }

    suspend fun updateCustomer(
        shopId: String,
        customer: Customer,
        name: String,
        mobile: String,
        email: String,
        address: String,
        active: Boolean,
    ): Result<Unit> = runCatching {
        require(customer.id.isNotBlank()) { "Customer ID required" }
        db.collection("shops").document(shopId).collection("customers").document(customer.id)
            .update(
                mapOf(
                    "name" to name.trim().take(100),
                    "mobile" to mobile.trim().take(30),
                    "email" to email.trim().take(160),
                    "address" to address.trim().take(250),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/$shopId/customers/${customer.id}")
    }

    // ==================== DEVICES ====================
    fun observeDevices(shopId: String): Flow<List<Device>> =
        observeShopCollection(shopId, "devices", Device::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    suspend fun addDevice(
        shop: Shop,
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
    ): Result<String> = runCatching {
        requireUserId()
        require(brand.isNotBlank() && model.isNotBlank()) { "Device brand and model required" }
        val ref = db.collection("shops").document(shop.id).collection("devices").document()
        ref.set(
            mapOf(
                "customerId" to customer.id,
                "customerName" to customer.name,
                "brand" to brand.trim().take(60),
                "model" to model.trim().take(80),
                "imeiOrSerial" to imeiOrSerial.trim().take(60),
                "deviceType" to deviceType.trim().ifBlank { "Smartphone" }.take(40),
                "color" to color.trim().take(40),
                "condition" to condition.trim().ifBlank { "Standard wear" }.take(120),
                "accessoriesReceived" to accessories.trim().take(200),
                "reportedIssue" to reportedIssue.trim().ifBlank { "Diagnostics requested" }.take(500),
                "photoUris" to photoUris.take(10),
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/devices")
    }

    // ==================== REPAIRS & WORKFLOW ====================
    fun observeRepairs(shopId: String): Flow<List<RepairTicket>> =
        observeShopCollection(shopId, "repairs", RepairTicket::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    fun observeStatusHistory(shopId: String, repairId: String): Flow<List<RepairStatusHistory>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/repairs/$repairId/statusHistory"
        emitAll(
            db.collection("shops").document(shopId).collection("repairs")
                .document(repairId).collection("statusHistory")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RepairStatusHistory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeRepairNotes(shopId: String, repairId: String): Flow<List<RepairNote>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/repairs/$repairId/notes"
        emitAll(
            db.collection("shops").document(shopId).collection("repairs")
                .document(repairId).collection("notes")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RepairNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeRepairPartsUsed(shopId: String, repairId: String): Flow<List<RepairPartUsage>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/repairs/$repairId/partsUsed"
        emitAll(
            db.collection("shops").document(shopId).collection("repairs")
                .document(repairId).collection("partsUsed")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RepairPartUsage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createRepairTicket(
        shop: Shop,
        customer: Customer,
        device: Device,
        problemDescription: String,
        initialDiagnosis: String,
        estimatedCost: Double,
        estimatedCompletionDate: String,
        priority: RepairPriority,
        intakeCondition: String,
        assignedTechUid: String,
        assignedTechName: String,
        labourCharge: Double,
        photoUris: List<String>,
        actorRole: StaffRole,
    ): Result<RepairTicket> = runCatching {
        val uid = requireUserId()
        val actorName = requireUserName()
        val shopRef = db.collection("shops").document(shop.id)

        // Atomically reserve next ticket sequence
        val seq = db.runTransaction { tx ->
            val snap = tx.get(shopRef)
            val current = snap.getLong("nextTicketSeq") ?: 1001L
            tx.update(
                shopRef,
                mapOf(
                    "nextTicketSeq" to current + 1L,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            current
        }.await()

        val ticketNumber = "${shop.shopCode.ifBlank { "RP" }}-$seq"
        val deviceSummary = "${device.brand} ${device.model}".trim()
        val breakdown = BillingCalculator.calculate(
            labourCharge = labourCharge,
            partsTotal = 0.0,
            discountAmount = 0.0,
            taxRatePercent = shop.taxRatePercent,
            paidAmount = 0.0
        )

        val repairRef = shopRef.collection("repairs").document()
        val historyRef = repairRef.collection("statusHistory").document()
        val notifRef = shopRef.collection("notifications").document()
        val auditRef = shopRef.collection("auditLogs").document()
        val custRef = shopRef.collection("customers").document(customer.id)
        val trackingKey = BillingCalculator.buildTrackingKey(ticketNumber, customer.mobile)
        val trackingRef = db.collection("publicTracking").document(trackingKey)
        val todayStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())

        val batch = db.batch()
        batch.set(
            repairRef,
            mapOf(
                "ticketNumber" to ticketNumber,
                "customerId" to customer.id,
                "customerName" to customer.name,
                "customerMobile" to customer.mobile,
                "deviceId" to device.id,
                "deviceSummary" to deviceSummary.take(160),
                "problemDescription" to problemDescription.trim().take(1000),
                "initialDiagnosis" to initialDiagnosis.trim().take(1000),
                "estimatedCost" to BillingCalculator.round2(estimatedCost),
                "estimatedCompletionDate" to estimatedCompletionDate.trim().take(40),
                "assignedTechUid" to assignedTechUid.take(128),
                "assignedTechName" to assignedTechName.take(100),
                "priority" to priority.name,
                "status" to RepairStatus.RECEIVED.name,
                "intakeCondition" to intakeCondition.trim().take(300),
                "repairNotes" to "",
                "labourCharge" to BillingCalculator.round2(labourCharge),
                "partsTotal" to 0.0,
                "discountAmount" to 0.0,
                "taxRatePercent" to shop.taxRatePercent,
                "totalAmount" to breakdown.grandTotal,
                "paidAmount" to 0.0,
                "photoUris" to photoUris.take(10),
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            historyRef,
            mapOf(
                "fromStatus" to "INTAKE",
                "toStatus" to RepairStatus.RECEIVED.name,
                "note" to "Ticket created for $deviceSummary",
                "changedByUid" to uid,
                "changedByName" to actorName,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            custRef,
            mapOf(
                "totalRepairs" to (customer.totalRepairs + 1L),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            notifRef,
            mapOf(
                "title" to "New Ticket $ticketNumber",
                "message" to "$deviceSummary intake for ${customer.name}",
                "eventType" to "NEW_REPAIR",
                "repairId" to repairRef.id,
                "ticketNumber" to ticketNumber,
                "isRead" to false,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            auditRef,
            mapOf(
                "action" to "CREATE_REPAIR",
                "entityType" to "REPAIR",
                "entityId" to repairRef.id,
                "summary" to "Created $ticketNumber for ${customer.name} ($deviceSummary)",
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to actorRole.name,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            trackingRef,
            mapOf(
                "shopId" to shop.id,
                "shopName" to shop.shopName,
                "ticketNumber" to ticketNumber,
                "deviceSummary" to deviceSummary.take(160),
                "status" to RepairStatus.RECEIVED.name,
                "estimatedCompletionDate" to estimatedCompletionDate.trim().take(40),
                "paymentStatus" to PaymentStatus.UNPAID.name,
                "totalAmount" to breakdown.grandTotal,
                "paidAmount" to 0.0,
                "balanceDue" to breakdown.grandTotal,
                "timelineSummary" to listOf("Received • $todayStr"),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.commit().await()

        RepairTicket(
            id = repairRef.id,
            ticketNumber = ticketNumber,
            customerId = customer.id,
            customerName = customer.name,
            customerMobile = customer.mobile,
            deviceId = device.id,
            deviceSummary = deviceSummary,
            problemDescription = problemDescription.trim(),
            initialDiagnosis = initialDiagnosis.trim(),
            estimatedCost = BillingCalculator.round2(estimatedCost),
            estimatedCompletionDate = estimatedCompletionDate.trim(),
            assignedTechUid = assignedTechUid,
            assignedTechName = assignedTechName,
            priority = priority.name,
            status = RepairStatus.RECEIVED.name,
            intakeCondition = intakeCondition.trim(),
            labourCharge = BillingCalculator.round2(labourCharge),
            taxRatePercent = shop.taxRatePercent,
            totalAmount = breakdown.grandTotal,
            memberUids = shop.memberUids,
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/repairs")
    }

    suspend fun transitionRepairStatus(
        shop: Shop,
        repair: RepairTicket,
        newStatus: RepairStatus,
        note: String,
        actorRole: StaffRole,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val actorName = requireUserName()
        val current = repair.statusEnum
        val allowed = current.allowedNextStatuses()
        require(newStatus in allowed || actorRole == StaffRole.OWNER) {
            "Transition from ${current.label} to ${newStatus.label} is not permitted"
        }

        val shopRef = db.collection("shops").document(shop.id)
        val repairRef = shopRef.collection("repairs").document(repair.id)
        val historyRef = repairRef.collection("statusHistory").document()
        val notifRef = shopRef.collection("notifications").document()
        val auditRef = shopRef.collection("auditLogs").document()
        val trackingKey = BillingCalculator.buildTrackingKey(repair.ticketNumber, repair.customerMobile)
        val trackingRef = db.collection("publicTracking").document(trackingKey)
        val nowStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())

        val batch = db.batch()
        batch.update(
            repairRef,
            mapOf(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            historyRef,
            mapOf(
                "fromStatus" to current.name,
                "toStatus" to newStatus.name,
                "note" to note.trim().take(500),
                "changedByUid" to uid,
                "changedByName" to actorName,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            notifRef,
            mapOf(
                "title" to "${repair.ticketNumber}: ${newStatus.label}",
                "message" to "${repair.deviceSummary} moved to ${newStatus.label}${if (note.isNotBlank()) " — $note" else ""}".take(400),
                "eventType" to "STATUS_${newStatus.name}",
                "repairId" to repair.id,
                "ticketNumber" to repair.ticketNumber,
                "isRead" to false,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            auditRef,
            mapOf(
                "action" to "STATUS_CHANGE",
                "entityType" to "REPAIR",
                "entityId" to repair.id,
                "summary" to "${repair.ticketNumber}: ${current.label} -> ${newStatus.label}",
                "actorUid" to uid,
                "actorName" to actorName,
                "actorRole" to actorRole.name,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )

        val freshRepairSnap = repairRef.get().await()
        val freshTotal = freshRepairSnap.getDouble("totalAmount") ?: repair.totalAmount
        val freshPaid = freshRepairSnap.getDouble("paidAmount") ?: repair.paidAmount
        val freshEstDate = freshRepairSnap.getString("estimatedCompletionDate") ?: repair.estimatedCompletionDate

        val payStatus = when {
            freshPaid >= (freshTotal - 0.01) && freshTotal > 0 -> PaymentStatus.PAID.name
            freshPaid > 0 -> PaymentStatus.PARTIAL.name
            else -> PaymentStatus.UNPAID.name
        }
        batch.set(
            trackingRef,
            mapOf(
                "shopId" to shop.id,
                "shopName" to shop.shopName,
                "ticketNumber" to repair.ticketNumber,
                "deviceSummary" to repair.deviceSummary,
                "status" to newStatus.name,
                "estimatedCompletionDate" to freshEstDate,
                "paymentStatus" to payStatus,
                "totalAmount" to freshTotal,
                "paidAmount" to freshPaid,
                "balanceDue" to BillingCalculator.round2((freshTotal - freshPaid).coerceAtLeast(0.0)),
                "timelineSummary" to listOf("${newStatus.label} • $nowStr"),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/${shop.id}/repairs/${repair.id}")
    }

    suspend fun assignTechnician(
        shop: Shop,
        repair: RepairTicket,
        techId: String,
        techName: String,
    ): Result<Unit> = runCatching {
        requireUserId()
        val shopRef = db.collection("shops").document(shop.id)
        val batch = db.batch()
        batch.update(
            shopRef.collection("repairs").document(repair.id),
            mapOf(
                "assignedTechUid" to techId,
                "assignedTechName" to techName,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            shopRef.collection("technicians").document(techId),
            mapOf(
                "assignedCount" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            shopRef.collection("notifications").document(),
            mapOf(
                "title" to "Technician Assigned",
                "message" to "$techName assigned to ${repair.ticketNumber} (${repair.deviceSummary})",
                "eventType" to "TECH_ASSIGNED",
                "repairId" to repair.id,
                "ticketNumber" to repair.ticketNumber,
                "isRead" to false,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/${shop.id}/repairs/${repair.id}")
    }

    suspend fun addRepairNote(
        shop: Shop,
        repair: RepairTicket,
        content: String,
        isDiagnosis: Boolean,
        actorRole: StaffRole,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val actorName = requireUserName()
        val clean = content.trim().take(1000)
        require(clean.isNotBlank()) { "Note cannot be empty" }

        val repairRef = db.collection("shops").document(shop.id).collection("repairs").document(repair.id)
        val noteRef = repairRef.collection("notes").document()
        val batch = db.batch()
        batch.set(
            noteRef,
            mapOf(
                "content" to clean,
                "isDiagnosis" to isDiagnosis,
                "authorUid" to uid,
                "authorName" to actorName,
                "authorRole" to actorRole.name,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        if (isDiagnosis) {
            batch.update(
                repairRef,
                mapOf(
                    "initialDiagnosis" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        } else {
            batch.update(
                repairRef,
                mapOf(
                    "repairNotes" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/repairs/${repair.id}/notes")
    }

    // ==================== SPARE PARTS & INVENTORY ====================
    fun observeSpareParts(shopId: String): Flow<List<SparePart>> =
        observeShopCollection(shopId, "parts", SparePart::class.java) { list ->
            list.sortedBy { it.name.lowercase() }
        }

    fun observeInventoryTransactions(shopId: String): Flow<List<InventoryTransaction>> =
        observeShopCollection(shopId, "inventoryTransactions", InventoryTransaction::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    suspend fun addSparePart(
        shop: Shop,
        name: String,
        sku: String,
        category: String,
        stockQty: Long,
        purchasePrice: Double,
        sellingPrice: Double,
        minStockLevel: Long,
        supplierName: String,
        supplierContact: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(name.isNotBlank() && sku.isNotBlank()) { "Part name and SKU required" }
        require(stockQty >= 0 && minStockLevel >= 0) { "Stock cannot be negative" }
        val shopRef = db.collection("shops").document(shop.id)
        val partRef = shopRef.collection("parts").document()
        val txRef = shopRef.collection("inventoryTransactions").document()

        val batch = db.batch()
        batch.set(
            partRef,
            mapOf(
                "name" to name.trim().take(120),
                "sku" to sku.trim().uppercase().take(60),
                "category" to category.trim().ifBlank { "Display & Touch" }.take(60),
                "stockQty" to stockQty,
                "purchasePrice" to BillingCalculator.round2(purchasePrice),
                "sellingPrice" to BillingCalculator.round2(sellingPrice),
                "minStockLevel" to minStockLevel,
                "supplierName" to supplierName.trim().take(100),
                "supplierContact" to supplierContact.trim().take(80),
                "active" to true,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (stockQty > 0L) {
            batch.set(
                txRef,
                mapOf(
                    "partId" to partRef.id,
                    "partName" to name.trim().take(120),
                    "txType" to InventoryTxType.STOCK_IN.name,
                    "quantityDelta" to stockQty,
                    "stockBefore" to 0L,
                    "stockAfter" to stockQty,
                    "unitCost" to BillingCalculator.round2(purchasePrice),
                    "note" to "Opening stock",
                    "performedByUid" to uid,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.commit().await()
        partRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/parts")
    }

    suspend fun adjustPartStock(
        shop: Shop,
        part: SparePart,
        quantityDelta: Long,
        txType: InventoryTxType,
        note: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(quantityDelta > 0L) { "Adjustment quantity must be positive" }
        val shopRef = db.collection("shops").document(shop.id)
        val partRef = shopRef.collection("parts").document(part.id)
        val txRef = shopRef.collection("inventoryTransactions").document()

        db.runTransaction { tx ->
            val snap = tx.get(partRef)
            val currentStock = snap.getLong("stockQty") ?: 0L
            val signedDelta = if (txType == InventoryTxType.STOCK_IN) quantityDelta else -quantityDelta
            val nextStock = currentStock + signedDelta
            if (nextStock < 0L) {
                throw IllegalStateException("Insufficient stock for ${part.name} (available: $currentStock)")
            }
            tx.update(
                partRef,
                mapOf(
                    "stockQty" to nextStock,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "partId" to part.id,
                    "partName" to part.name,
                    "txType" to txType.name,
                    "quantityDelta" to signedDelta,
                    "stockBefore" to currentStock,
                    "stockAfter" to nextStock,
                    "unitCost" to part.purchasePrice,
                    "note" to note.trim().take(300),
                    "performedByUid" to uid,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/${shop.id}/parts/${part.id}")
    }

    suspend fun consumePartOnRepair(
        shop: Shop,
        repair: RepairTicket,
        part: SparePart,
        quantity: Long,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(quantity in 1L..500L) { "Quantity must be between 1 and 500" }
        val shopRef = db.collection("shops").document(shop.id)
        val partRef = shopRef.collection("parts").document(part.id)
        val repairRef = shopRef.collection("repairs").document(repair.id)
        val usageRef = repairRef.collection("partsUsed").document()
        val txRef = shopRef.collection("inventoryTransactions").document()
        val lineTotal = BillingCalculator.round2(part.sellingPrice * quantity)

        var stockAfterResult = 0L
        db.runTransaction { tx ->
            val partSnap = tx.get(partRef)
            val repairSnap = tx.get(repairRef)
            val currentStock = partSnap.getLong("stockQty") ?: 0L
            if (currentStock < quantity) {
                throw IllegalStateException("Insufficient stock: only $currentStock left for ${part.name}")
            }
            val nextStock = currentStock - quantity
            stockAfterResult = nextStock

            val prevPartsTotal = repairSnap.getDouble("partsTotal") ?: 0.0
            val labour = repairSnap.getDouble("labourCharge") ?: 0.0
            val discount = repairSnap.getDouble("discountAmount") ?: 0.0
            val taxRate = repairSnap.getDouble("taxRatePercent") ?: shop.taxRatePercent
            val newPartsTotal = BillingCalculator.round2(prevPartsTotal + lineTotal)
            val breakdown = BillingCalculator.calculate(labour, newPartsTotal, discount, taxRate, repair.paidAmount)

            tx.update(partRef, mapOf("stockQty" to nextStock, "updatedAt" to FieldValue.serverTimestamp()))
            tx.update(
                repairRef,
                mapOf(
                    "partsTotal" to newPartsTotal,
                    "totalAmount" to breakdown.grandTotal,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.set(
                usageRef,
                mapOf(
                    "partId" to part.id,
                    "partName" to part.name,
                    "sku" to part.sku,
                    "quantity" to quantity,
                    "unitPrice" to part.sellingPrice,
                    "lineTotal" to lineTotal,
                    "addedByUid" to uid,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "partId" to part.id,
                    "partName" to part.name,
                    "txType" to InventoryTxType.REPAIR_CONSUME.name,
                    "quantityDelta" to -quantity,
                    "stockBefore" to currentStock,
                    "stockAfter" to nextStock,
                    "unitCost" to part.purchasePrice,
                    "referenceTicket" to repair.ticketNumber,
                    "note" to "Consumed on ${repair.ticketNumber}",
                    "performedByUid" to uid,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }.await()

        if (stockAfterResult <= part.minStockLevel) {
            shopRef.collection("notifications").document().set(
                mapOf(
                    "title" to "Low Stock Alert: ${part.name}",
                    "message" to "Remaining stock is $stockAfterResult (min threshold ${part.minStockLevel})",
                    "eventType" to "LOW_STOCK",
                    "repairId" to repair.id,
                    "ticketNumber" to repair.ticketNumber,
                    "isRead" to false,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "shops/${shop.id}/repairs/${repair.id}/partsUsed")
    }

    // ==================== INVOICES & PAYMENTS ====================
    fun observeInvoices(shopId: String): Flow<List<Invoice>> =
        observeShopCollection(shopId, "invoices", Invoice::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    fun observePayments(shopId: String): Flow<List<Payment>> =
        observeShopCollection(shopId, "payments", Payment::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    suspend fun generateOrUpdateInvoice(
        shop: Shop,
        repair: RepairTicket,
        labourCharge: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        lineItemsSummary: String,
    ): Result<Invoice> = runCatching {
        requireUserId()
        val shopRef = db.collection("shops").document(shop.id)
        val repairRef = shopRef.collection("repairs").document(repair.id)

        val breakdown = BillingCalculator.calculate(
            labourCharge = labourCharge,
            partsTotal = repair.partsTotal,
            discountAmount = discountAmount,
            taxRatePercent = taxRatePercent,
            paidAmount = repair.paidAmount,
        )

        val existingInvoiceId = repair.invoiceId
        val invoiceRef: com.google.firebase.firestore.DocumentReference
        val invoiceNumber: String

        if (existingInvoiceId.isNotBlank()) {
            invoiceRef = shopRef.collection("invoices").document(existingInvoiceId)
            val snap = invoiceRef.get().await()
            invoiceNumber = snap.getString("invoiceNumber") ?: "INV-${repair.ticketNumber}"
            invoiceRef.update(
                mapOf(
                    "labourCharge" to BillingCalculator.round2(labourCharge),
                    "partsTotal" to repair.partsTotal,
                    "discountAmount" to breakdown.discountApplied,
                    "taxRatePercent" to taxRatePercent.coerceIn(0.0, 50.0),
                    "taxAmount" to breakdown.taxAmount,
                    "grandTotal" to breakdown.grandTotal,
                    "paidAmount" to repair.paidAmount,
                    "balanceDue" to breakdown.balanceDue,
                    "paymentStatus" to breakdown.paymentStatus.name,
                    "lineItemsSummary" to lineItemsSummary.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            val seq = db.runTransaction { tx ->
                val snap = tx.get(shopRef)
                val current = snap.getLong("nextInvoiceSeq") ?: 5001L
                tx.update(shopRef, mapOf("nextInvoiceSeq" to current + 1L, "updatedAt" to FieldValue.serverTimestamp()))
                current
            }.await()
            invoiceNumber = "INV-$seq"
            invoiceRef = shopRef.collection("invoices").document()
            invoiceRef.set(
                mapOf(
                    "invoiceNumber" to invoiceNumber,
                    "repairId" to repair.id,
                    "ticketNumber" to repair.ticketNumber,
                    "customerId" to repair.customerId,
                    "customerName" to repair.customerName,
                    "customerMobile" to repair.customerMobile,
                    "deviceSummary" to repair.deviceSummary,
                    "labourCharge" to BillingCalculator.round2(labourCharge),
                    "partsTotal" to repair.partsTotal,
                    "discountAmount" to breakdown.discountApplied,
                    "taxRatePercent" to taxRatePercent.coerceIn(0.0, 50.0),
                    "taxAmount" to breakdown.taxAmount,
                    "grandTotal" to breakdown.grandTotal,
                    "paidAmount" to repair.paidAmount,
                    "balanceDue" to breakdown.balanceDue,
                    "paymentStatus" to breakdown.paymentStatus.name,
                    "lineItemsSummary" to lineItemsSummary.take(2000),
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }

        repairRef.update(
            mapOf(
                "labourCharge" to BillingCalculator.round2(labourCharge),
                "discountAmount" to breakdown.discountApplied,
                "taxRatePercent" to taxRatePercent.coerceIn(0.0, 50.0),
                "totalAmount" to breakdown.grandTotal,
                "invoiceId" to invoiceRef.id,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        val trackingKey = BillingCalculator.buildTrackingKey(repair.ticketNumber, repair.customerMobile)
        db.collection("publicTracking").document(trackingKey).set(
            mapOf(
                "shopId" to shop.id,
                "shopName" to shop.shopName,
                "ticketNumber" to repair.ticketNumber,
                "deviceSummary" to repair.deviceSummary,
                "status" to repair.status,
                "estimatedCompletionDate" to repair.estimatedCompletionDate,
                "paymentStatus" to breakdown.paymentStatus.name,
                "totalAmount" to breakdown.grandTotal,
                "paidAmount" to repair.paidAmount,
                "balanceDue" to breakdown.balanceDue,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        Invoice(
            id = invoiceRef.id,
            invoiceNumber = invoiceNumber,
            repairId = repair.id,
            ticketNumber = repair.ticketNumber,
            customerId = repair.customerId,
            customerName = repair.customerName,
            customerMobile = repair.customerMobile,
            deviceSummary = repair.deviceSummary,
            labourCharge = BillingCalculator.round2(labourCharge),
            partsTotal = repair.partsTotal,
            discountAmount = breakdown.discountApplied,
            taxRatePercent = taxRatePercent.coerceIn(0.0, 50.0),
            taxAmount = breakdown.taxAmount,
            grandTotal = breakdown.grandTotal,
            paidAmount = repair.paidAmount,
            balanceDue = breakdown.balanceDue,
            paymentStatus = breakdown.paymentStatus.name,
            lineItemsSummary = lineItemsSummary,
            memberUids = shop.memberUids,
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "shops/${shop.id}/invoices")
    }

    suspend fun recordPayment(
        shop: Shop,
        invoice: Invoice,
        repair: RepairTicket?,
        amount: Double,
        method: PaymentMethod,
        isAdvance: Boolean,
        referenceNumber: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val actorName = requireUserName()
        val cleanAmount = BillingCalculator.round2(amount)
        require(cleanAmount > 0.0) { "Payment amount must be greater than zero" }

        val shopRef = db.collection("shops").document(shop.id)
        val invRef = shopRef.collection("invoices").document(invoice.id)
        val repRef = shopRef.collection("repairs").document(invoice.repairId)
        val payRef = shopRef.collection("payments").document()
        val notifRef = shopRef.collection("notifications").document()

        var newPaid = 0.0
        var newBalance = 0.0
        var newStatus = PaymentStatus.PARTIAL.name

        db.runTransaction { tx ->
            val invSnap = tx.get(invRef)
            val currentPaid = invSnap.getDouble("paidAmount") ?: 0.0
            val grandTotal = invSnap.getDouble("grandTotal") ?: 0.0
            newPaid = BillingCalculator.round2(currentPaid + cleanAmount)
            newBalance = BillingCalculator.round2((grandTotal - newPaid).coerceAtLeast(0.0))
            newStatus = when {
                newBalance <= 0.01 && newPaid > 0.0 -> PaymentStatus.PAID.name
                newPaid > 0.0 -> PaymentStatus.PARTIAL.name
                else -> PaymentStatus.UNPAID.name
            }

            tx.update(
                invRef,
                mapOf(
                    "paidAmount" to newPaid,
                    "balanceDue" to newBalance,
                    "paymentStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.update(
                repRef,
                mapOf(
                    "paidAmount" to newPaid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.set(
                payRef,
                mapOf(
                    "invoiceId" to invoice.id,
                    "repairId" to invoice.repairId,
                    "ticketNumber" to invoice.ticketNumber,
                    "customerName" to invoice.customerName,
                    "amount" to cleanAmount,
                    "method" to method.name,
                    "isAdvance" to isAdvance,
                    "referenceNumber" to referenceNumber.trim().take(100),
                    "receivedByUid" to uid,
                    "receivedByName" to actorName,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            tx.set(
                notifRef,
                mapOf(
                    "title" to "Payment Received (${method.label})",
                    "message" to "${BillingCalculator.formatMoney(cleanAmount, shop.currencySymbol)} for ${invoice.ticketNumber} (${invoice.customerName})",
                    "eventType" to "PAYMENT_RECEIVED",
                    "repairId" to invoice.repairId,
                    "ticketNumber" to invoice.ticketNumber,
                    "isRead" to false,
                    "memberUids" to shop.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }.await()

        val trackingKey = BillingCalculator.buildTrackingKey(invoice.ticketNumber, invoice.customerMobile)
        db.collection("publicTracking").document(trackingKey).set(
            mapOf(
                "shopId" to shop.id,
                "shopName" to shop.shopName,
                "ticketNumber" to invoice.ticketNumber,
                "deviceSummary" to invoice.deviceSummary,
                "status" to (repair?.status ?: RepairStatus.IN_PROGRESS.name),
                "estimatedCompletionDate" to (repair?.estimatedCompletionDate ?: ""),
                "paymentStatus" to newStatus,
                "totalAmount" to invoice.grandTotal,
                "paidAmount" to newPaid,
                "balanceDue" to newBalance,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "shops/${shop.id}/payments")
    }
}
