package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StaffRole(val label: String) {
    OWNER("Shop Owner"),
    TECHNICIAN("Technician"),
    RECEPTIONIST("Receptionist");

    companion object {
        fun fromString(value: String?): StaffRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RECEPTIONIST
    }
}

enum class RepairPriority(val label: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    URGENT("Urgent");

    companion object {
        fun fromString(value: String?): RepairPriority =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
    }
}

enum class RepairStatus(
    val label: String,
    val stepIndex: Int,
    val isTerminal: Boolean = false
) {
    RECEIVED("Received", 0),
    DIAGNOSING("Diagnosing", 1),
    AWAITING_CUSTOMER_APPROVAL("Awaiting Approval", 2),
    APPROVED("Approved", 3),
    IN_PROGRESS("In Progress", 4),
    WAITING_FOR_PARTS("Waiting for Parts", 5),
    REPAIR_COMPLETED("Repair Completed", 6),
    READY_FOR_PICKUP("Ready for Pickup", 7),
    DELIVERED("Delivered", 8, isTerminal = true),
    REJECTED("Rejected", 9, isTerminal = true),
    CANCELLED("Cancelled", 10, isTerminal = true),
    UNABLE_TO_REPAIR("Unable to Repair", 11, isTerminal = false);

    fun allowedNextStatuses(): List<RepairStatus> = when (this) {
        RECEIVED -> listOf(DIAGNOSING, APPROVED, CANCELLED)
        DIAGNOSING -> listOf(AWAITING_CUSTOMER_APPROVAL, APPROVED, IN_PROGRESS, UNABLE_TO_REPAIR, CANCELLED)
        AWAITING_CUSTOMER_APPROVAL -> listOf(APPROVED, REJECTED, CANCELLED)
        APPROVED -> listOf(IN_PROGRESS, WAITING_FOR_PARTS, CANCELLED)
        IN_PROGRESS -> listOf(WAITING_FOR_PARTS, REPAIR_COMPLETED, UNABLE_TO_REPAIR, CANCELLED)
        WAITING_FOR_PARTS -> listOf(IN_PROGRESS, REPAIR_COMPLETED, UNABLE_TO_REPAIR, CANCELLED)
        REPAIR_COMPLETED -> listOf(READY_FOR_PICKUP, DELIVERED)
        READY_FOR_PICKUP -> listOf(DELIVERED)
        UNABLE_TO_REPAIR -> listOf(READY_FOR_PICKUP, DELIVERED, CANCELLED)
        DELIVERED, REJECTED, CANCELLED -> emptyList()
    }

    companion object {
        fun fromString(value: String?): RepairStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RECEIVED
    }
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card");

    companion object {
        fun fromString(value: String?): PaymentMethod =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CASH
    }
}

enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIAL("Partial"),
    PAID("Paid");

    companion object {
        fun fromString(value: String?): PaymentStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNPAID
    }
}

enum class InventoryTxType(val label: String) {
    STOCK_IN("Stock In"),
    STOCK_OUT("Stock Out"),
    REPAIR_CONSUME("Repair Used");

    companion object {
        fun fromString(value: String?): InventoryTxType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STOCK_IN
    }
}

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val activeShopId: String = "",
    val activeRole: String = StaffRole.OWNER.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Shop(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val shopName: String = "",
    val shopCode: String = "",
    val phone: String = "",
    val address: String = "",
    val currencySymbol: String = "$",
    val taxRatePercent: Double = 8.5,
    val nextTicketSeq: Long = 1001L,
    val nextInvoiceSeq: Long = 5001L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val shopId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = StaffRole.RECEPTIONIST.name,
    val specialty: String = "",
    val active: Boolean = true,
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopInvite(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val ownerUid: String = "",
    val staffName: String = "",
    val role: String = StaffRole.TECHNICIAN.name,
    val specialty: String = "",
    val active: Boolean = true,
    val usedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val active: Boolean = true,
    val totalRepairs: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Device(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val brand: String = "",
    val model: String = "",
    val imeiOrSerial: String = "",
    val deviceType: String = "Smartphone",
    val color: String = "",
    val condition: String = "",
    val accessoriesReceived: String = "",
    val reportedIssue: String = "",
    val photoUris: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RepairTicket(
    @DocumentId val id: String = "",
    val ticketNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerMobile: String = "",
    val deviceId: String = "",
    val deviceSummary: String = "",
    val problemDescription: String = "",
    val initialDiagnosis: String = "",
    val estimatedCost: Double = 0.0,
    val estimatedCompletionDate: String = "",
    val assignedTechUid: String = "",
    val assignedTechName: String = "",
    val priority: String = RepairPriority.NORMAL.name,
    val status: String = RepairStatus.RECEIVED.name,
    val intakeCondition: String = "",
    val repairNotes: String = "",
    val labourCharge: Double = 0.0,
    val partsTotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxRatePercent: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val invoiceId: String = "",
    val photoUris: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: RepairStatus get() = RepairStatus.fromString(status)
    val priorityEnum: RepairPriority get() = RepairPriority.fromString(priority)
}

@IgnoreExtraProperties
data class RepairStatusHistory(
    @DocumentId val id: String = "",
    val fromStatus: String = "",
    val toStatus: String = "",
    val note: String = "",
    val changedByUid: String = "",
    val changedByName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RepairNote(
    @DocumentId val id: String = "",
    val content: String = "",
    val isDiagnosis: Boolean = false,
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = StaffRole.TECHNICIAN.name,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RepairPartUsage(
    @DocumentId val id: String = "",
    val partId: String = "",
    val partName: String = "",
    val sku: String = "",
    val quantity: Long = 1L,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0,
    val addedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TechnicianProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val specialty: String = "",
    val active: Boolean = true,
    val assignedCount: Long = 0L,
    val completedCount: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SparePart(
    @DocumentId val id: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val stockQty: Long = 0L,
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val minStockLevel: Long = 5L,
    val supplierName: String = "",
    val supplierContact: String = "",
    val active: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val isLowStock: Boolean get() = active && stockQty <= minStockLevel
}

@IgnoreExtraProperties
data class InventoryTransaction(
    @DocumentId val id: String = "",
    val partId: String = "",
    val partName: String = "",
    val txType: String = InventoryTxType.STOCK_IN.name,
    val quantityDelta: Long = 0L,
    val stockBefore: Long = 0L,
    val stockAfter: Long = 0L,
    val unitCost: Double = 0.0,
    val referenceTicket: String = "",
    val note: String = "",
    val performedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val repairId: String = "",
    val ticketNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerMobile: String = "",
    val deviceSummary: String = "",
    val labourCharge: Double = 0.0,
    val partsTotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxRatePercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val lineItemsSummary: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val invoiceId: String = "",
    val repairId: String = "",
    val ticketNumber: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val method: String = PaymentMethod.CASH.name,
    val isAdvance: Boolean = false,
    val referenceNumber: String = "",
    val receivedByUid: String = "",
    val receivedByName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val recordedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val eventType: String = "",
    val repairId: String = "",
    val ticketNumber: String = "",
    val isRead: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val summary: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = StaffRole.OWNER.name,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PublicTracking(
    @DocumentId val id: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val ticketNumber: String = "",
    val deviceSummary: String = "",
    val status: String = RepairStatus.RECEIVED.name,
    val estimatedCompletionDate: String = "",
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val timelineSummary: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
