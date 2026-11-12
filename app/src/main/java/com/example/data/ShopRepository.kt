package com.example.data

import com.example.model.AuditLog
import com.example.model.Expense
import com.example.model.PublicTracking
import com.example.model.RepairStatus
import com.example.model.Shop
import com.example.model.ShopInvite
import com.example.model.ShopMember
import com.example.model.ShopNotification
import com.example.model.StaffRole
import com.example.model.TechnicianProfile
import com.example.model.UserAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun requireUserName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Staff Member"

    fun observeUserAccount(uid: String): Flow<UserAccount?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fe ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserAccount(uid: String, displayName: String, email: String): Result<UserAccount> = runCatching {
        val docRef = db.collection("users").document(uid)
        val snap = docRef.get().await()
        if (snap.exists()) {
            snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserAccount(id = uid, uid = uid, displayName = displayName, email = email)
        } else {
            val payload = mapOf(
                "uid" to uid,
                "displayName" to displayName.ifBlank { "RepairPilot User" }.take(120),
                "email" to email.take(160),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            docRef.set(payload).await()
            UserAccount(id = uid, uid = uid, displayName = displayName, email = email)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    suspend fun createShop(
        shopName: String,
        shopCode: String,
        phone: String,
        address: String,
        currencySymbol: String,
        taxRatePercent: Double,
    ): Result<Shop> = runCatching {
        val uid = requireUserId()
        val cleanName = shopName.trim().take(100)
        require(cleanName.isNotBlank()) { "Shop name is required" }
        val cleanCode = shopCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "").ifBlank { "RP" }.take(8)
        val shopId = "shop_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val shopRef = db.collection("shops").document(shopId)
        val memberRef = shopRef.collection("members").document(uid)
        val techRef = shopRef.collection("technicians").document("tech_$uid")
        val userRef = db.collection("users").document(uid)
        val logRef = shopRef.collection("auditLogs").document()

        val displayName = requireUserName()
        val email = auth.currentUser?.email.orEmpty()

        val shopPayload = mapOf(
            "ownerUid" to uid,
            "shopName" to cleanName,
            "shopCode" to cleanCode,
            "phone" to phone.trim().take(40),
            "address" to address.trim().take(250),
            "currencySymbol" to currencySymbol.trim().ifBlank { "$" }.take(8),
            "taxRatePercent" to taxRatePercent.coerceIn(0.0, 50.0),
            "nextTicketSeq" to 1001L,
            "nextInvoiceSeq" to 5001L,
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        val memberPayload = mapOf(
            "uid" to uid,
            "shopId" to shopId,
            "displayName" to displayName,
            "email" to email,
            "role" to StaffRole.OWNER.name,
            "specialty" to "Shop Lead & Diagnostics",
            "active" to true,
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        // Create shop first so member creation rule can read shops/{shopId}.ownerUid
        shopRef.set(shopPayload).await()
        memberRef.set(memberPayload).await()

        val batch = db.batch()
        batch.set(
            techRef,
            mapOf(
                "uid" to uid,
                "name" to displayName,
                "phone" to phone.trim().take(30),
                "specialty" to "Lead Technician",
                "active" to true,
                "assignedCount" to 0L,
                "completedCount" to 0L,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            userRef,
            mapOf(
                "activeShopId" to shopId,
                "activeRole" to StaffRole.OWNER.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            logRef,
            mapOf(
                "action" to "SHOP_CREATED",
                "entityType" to "SHOP",
                "entityId" to shopId,
                "summary" to "Registered shop $cleanName ($cleanCode)",
                "actorUid" to uid,
                "actorName" to displayName,
                "actorRole" to StaffRole.OWNER.name,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.commit().await()

        Shop(
            id = shopId,
            ownerUid = uid,
            shopName = cleanName,
            shopCode = cleanCode,
            phone = phone.trim(),
            address = address.trim(),
            currencySymbol = currencySymbol.trim().ifBlank { "$" },
            taxRatePercent = taxRatePercent.coerceIn(0.0, 50.0),
            memberUids = listOf(uid),
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops")
    }

    suspend fun updateShopSettings(
        shop: Shop,
        shopName: String,
        phone: String,
        address: String,
        currencySymbol: String,
        taxRatePercent: Double,
    ): Result<Unit> = runCatching {
        require(shop.id.isNotBlank()) { "Shop ID required" }
        requireNotNull(shop.createdAt) { "Shop createdAt missing" }
        val docRef = db.collection("shops").document(shop.id)
        docRef.update(
            mapOf(
                "shopName" to shopName.trim().take(100),
                "phone" to phone.trim().take(40),
                "address" to address.trim().take(250),
                "currencySymbol" to currencySymbol.trim().ifBlank { "$" }.take(8),
                "taxRatePercent" to taxRatePercent.coerceIn(0.0, 50.0),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/${shop.id}")
    }

    fun observeShop(shopId: String): Flow<Shop?> = flow {
        val docRef = db.collection("shops").document(shopId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fe ?: error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    fun observeMyMembership(shopId: String, uid: String): Flow<ShopMember?> = flow {
        val docRef = db.collection("shops").document(shopId).collection("members").document(uid)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(ShopMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    fun observeMembers(shopId: String): Flow<List<ShopMember>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/members"
        emitAll(
            db.collection("shops").document(shopId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { it.toObjects(ShopMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeTechnicians(shopId: String): Flow<List<TechnicianProfile>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/technicians"
        emitAll(
            db.collection("shops").document(shopId).collection("technicians")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TechnicianProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addTechnicianProfile(
        shop: Shop,
        name: String,
        phone: String,
        specialty: String,
    ): Result<String> = runCatching {
        requireUserId()
        val cleanName = name.trim().take(100)
        require(cleanName.isNotBlank()) { "Technician name is required" }
        val ref = db.collection("shops").document(shop.id).collection("technicians").document()
        ref.set(
            mapOf(
                "name" to cleanName,
                "phone" to phone.trim().take(30),
                "specialty" to specialty.trim().ifBlank { "General Smartphone Repair" }.take(100),
                "active" to true,
                "assignedCount" to 0L,
                "completedCount" to 0L,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/technicians")
    }

    suspend fun updateTechnicianProfile(
        shopId: String,
        tech: TechnicianProfile,
        name: String,
        phone: String,
        specialty: String,
        active: Boolean,
    ): Result<Unit> = runCatching {
        require(tech.id.isNotBlank()) { "Technician ID required" }
        db.collection("shops").document(shopId).collection("technicians").document(tech.id)
            .update(
                mapOf(
                    "name" to name.trim().take(100),
                    "phone" to phone.trim().take(30),
                    "specialty" to specialty.trim().ifBlank { "General Repair" }.take(100),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/$shopId/technicians/${tech.id}")
    }

    suspend fun createStaffInvite(
        shop: Shop,
        staffName: String,
        role: StaffRole,
        specialty: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val code = "INV_${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        db.collection("shopInvites").document(code).set(
            mapOf(
                "shopId" to shop.id,
                "shopName" to shop.shopName,
                "ownerUid" to uid,
                "staffName" to staffName.trim().ifBlank { "Team Member" }.take(100),
                "role" to role.name,
                "specialty" to specialty.trim().take(100),
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        code
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shopInvites")
    }

    fun observeMyInvites(ownerUid: String): Flow<List<ShopInvite>> = flow {
        emitAll(
            db.collection("shopInvites")
                .whereEqualTo("ownerUid", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ShopInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == ownerUid) handleFirestoreError(fe ?: error, OperationType.LIST, "shopInvites")
                    throw error
                }
        )
    }

    suspend fun joinShopWithInvite(inviteCode: String): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanCode = inviteCode.trim().uppercase()
        require(cleanCode.isNotBlank()) { "Invite code is required" }

        val inviteRef = db.collection("shopInvites").document(cleanCode)
        val inviteSnap = inviteRef.get().await()
        val invite = inviteSnap.toObject(ShopInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalArgumentException("Invite code not found")
        require(invite.active) { "This invite code has already been redeemed" }

        val shopRef = db.collection("shops").document(invite.shopId)
        val shopSnap = shopRef.get().await()
        val currentMembers = (shopSnap.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val updatedMembers = (currentMembers + uid).distinct().take(25)

        shopRef.update(
            mapOf(
                "memberUids" to updatedMembers,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        shopRef.collection("members").document(uid).set(
            mapOf(
                "uid" to uid,
                "shopId" to invite.shopId,
                "displayName" to requireUserName(),
                "email" to auth.currentUser?.email.orEmpty(),
                "role" to invite.role,
                "specialty" to invite.specialty,
                "active" to true,
                "inviteCode" to cleanCode,
                "memberUids" to updatedMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        inviteRef.update(
            mapOf(
                "active" to false,
                "usedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        db.collection("users").document(uid).update(
            mapOf(
                "activeShopId" to invite.shopId,
                "activeRole" to invite.role,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        invite.shopId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "shopInvites/$inviteCode")
    }

    fun observeExpenses(shopId: String): Flow<List<Expense>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/expenses"
        emitAll(
            db.collection("shops").document(shopId).collection("expenses")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addExpense(
        shop: Shop,
        title: String,
        category: String,
        amount: Double,
        note: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(title.isNotBlank() && amount > 0.0) { "Valid title and positive amount required" }
        val ref = db.collection("shops").document(shop.id).collection("expenses").document()
        ref.set(
            mapOf(
                "title" to title.trim().take(120),
                "category" to category.trim().ifBlank { "Operations" }.take(60),
                "amount" to com.example.util.BillingCalculator.round2(amount),
                "note" to note.trim().take(400),
                "recordedByUid" to uid,
                "memberUids" to shop.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "shops/${shop.id}/expenses")
    }

    fun observeNotifications(shopId: String): Flow<List<ShopNotification>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/notifications"
        emitAll(
            db.collection("shops").document(shopId).collection("notifications")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ShopNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun markNotificationRead(shopId: String, notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("shops").document(shopId).collection("notifications").document(notificationId)
            .update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/$shopId/notifications/$notificationId")
    }

    fun observeAuditLogs(shopId: String): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        val path = "shops/$shopId/auditLogs"
        emitAll(
            db.collection("shops").document(shopId).collection("auditLogs")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun lookupPublicTracking(ticketNumber: String, mobileLast4: String): Result<PublicTracking?> = runCatching {
        requireUserId()
        val key = com.example.util.BillingCalculator.buildTrackingKey(ticketNumber, mobileLast4)
        val snap = db.collection("publicTracking").document(key).get().await()
        if (!snap.exists()) null
        else snap.toObject(PublicTracking::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "publicTracking")
    }

    suspend fun syncMemberUidsAcrossShop(shop: Shop): Result<Unit> = runCatching {
        requireUserId()
        val shopRef = db.collection("shops").document(shop.id)
        val collections = listOf("customers", "devices", "repairs", "technicians", "parts", "invoices", "expenses", "notifications")
        val batch = db.batch()
        for (col in collections) {
            val docs = shopRef.collection(col).whereArrayContains("memberUids", shop.ownerUid).get().await()
            for (doc in docs.documents) {
                batch.update(doc.reference, mapOf("memberUids" to shop.memberUids, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "shops/${shop.id}")
    }
}
