package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.InventoryTxType
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.RepairPriority
import com.example.model.RepairStatus
import com.example.model.StaffRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RepairPilotRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullEndToEndWorkflow_shopToDeliveryAndPublicTracking_persistsAndEnforcesRules() = runBlocking {
        val ownerEmail = "owner_${UUID.randomUUID().toString().take(8)}@repairpilot.test"
        val ownerUid = signInTestUser(ownerEmail)
        val shopRepo = ShopRepository(firestore, auth)
        val opsRepo = RepairOperationsRepository(firestore, auth)

        // 1. User account + Shop creation
        withTimeout(TIMEOUT_MS) {
            shopRepo.ensureUserAccount(ownerUid, "Alice Owner", ownerEmail).getOrThrow()
        }
        val shop = withTimeout(TIMEOUT_MS) {
            shopRepo.createShop(
                shopName = "Precision Fix Lab",
                shopCode = "PFL",
                phone = "555-0199",
                address = "42 Silicon Ave",
                currencySymbol = "$",
                taxRatePercent = 10.0,
            ).getOrThrow()
        }
        assertEquals(ownerUid, shop.ownerUid)

        // 2. Add customer + verify duplicate guard
        val uniqueMobile = "98${(10000000..99999999).random()}"
        val customerId = withTimeout(TIMEOUT_MS) {
            opsRepo.addCustomer(shop, "Jordan Miles", uniqueMobile, "jordan@mail.com", "12 Oak St").getOrThrow()
        }
        val dupResult = withTimeout(TIMEOUT_MS) {
            opsRepo.addCustomer(shop, "Duplicate Jordan", uniqueMobile, "", "")
        }
        assertTrue(dupResult.isFailure)

        val customers = withTimeout(TIMEOUT_MS) {
            opsRepo.observeCustomers(shop.id).first { list -> list.any { it.id == customerId } }
        }
        val customer = customers.first { it.id == customerId }

        // 3. Register device
        val deviceId = withTimeout(TIMEOUT_MS) {
            opsRepo.addDevice(
                shop = shop,
                customer = customer,
                brand = "Apple",
                model = "iPhone 15 Pro",
                imeiOrSerial = "356999110022334",
                deviceType = "Smartphone",
                color = "Natural Titanium",
                condition = "Cracked front glass",
                accessories = "SIM Tray",
                reportedIssue = "Touch unresponsive after drop",
                photoUris = emptyList(),
            ).getOrThrow()
        }
        val devices = withTimeout(TIMEOUT_MS) {
            opsRepo.observeDevices(shop.id).first { list -> list.any { it.id == deviceId } }
        }
        val device = devices.first { it.id == deviceId }

        // 4. Add spare part
        val partId = withTimeout(TIMEOUT_MS) {
            opsRepo.addSparePart(
                shop = shop,
                name = "iPhone 15 Pro OLED",
                sku = "IP15P-OLED-${UUID.randomUUID().toString().take(4)}",
                category = "Display",
                stockQty = 3L,
                purchasePrice = 110.0,
                sellingPrice = 180.0,
                minStockLevel = 2L,
                supplierName = "MobileParts Direct",
                supplierContact = "555-7788",
            ).getOrThrow()
        }
        val parts = withTimeout(TIMEOUT_MS) {
            opsRepo.observeSpareParts(shop.id).first { list -> list.any { it.id == partId } }
        }
        val part = parts.first { it.id == partId }

        // 5. Create repair ticket
        val createdTicket = withTimeout(TIMEOUT_MS) {
            opsRepo.createRepairTicket(
                shop = shop,
                customer = customer,
                device = device,
                problemDescription = "Screen shattered and touch dead",
                initialDiagnosis = "Pending bench inspection",
                estimatedCost = 240.0,
                estimatedCompletionDate = "2026-11-14",
                priority = RepairPriority.HIGH,
                intakeCondition = "Frame scuffed, glass cracked",
                assignedTechUid = ownerUid,
                assignedTechName = "Alice Owner",
                labourCharge = 50.0,
                photoUris = emptyList(),
                actorRole = StaffRole.OWNER,
            ).getOrThrow()
        }
        assertEquals(RepairStatus.RECEIVED.name, createdTicket.status)

        // 6. Status workflow transitions + note + spare part consumption
        withTimeout(TIMEOUT_MS) {
            opsRepo.transitionRepairStatus(shop, createdTicket, RepairStatus.DIAGNOSING, "Opened display", StaffRole.OWNER).getOrThrow()
            val afterDiag = opsRepo.observeRepairs(shop.id).first { list ->
                list.firstOrNull { it.id == createdTicket.id }?.status == RepairStatus.DIAGNOSING.name
            }.first { it.id == createdTicket.id }

            opsRepo.addRepairNote(shop, afterDiag, "OLED digitizer flex torn; TrueTone intact", true, StaffRole.OWNER).getOrThrow()
            opsRepo.transitionRepairStatus(shop, afterDiag, RepairStatus.APPROVED, "Customer approved $230 + tax", StaffRole.OWNER).getOrThrow()
            val afterApprove = opsRepo.observeRepairs(shop.id).first { list ->
                list.firstOrNull { it.id == createdTicket.id }?.status == RepairStatus.APPROVED.name
            }.first { it.id == createdTicket.id }

            opsRepo.transitionRepairStatus(shop, afterApprove, RepairStatus.IN_PROGRESS, "Installing OEM OLED", StaffRole.OWNER).getOrThrow()
            val inProgress = opsRepo.observeRepairs(shop.id).first { list ->
                list.firstOrNull { it.id == createdTicket.id }?.status == RepairStatus.IN_PROGRESS.name
            }.first { it.id == createdTicket.id }

            // Insufficient stock guard test
            val overConsume = opsRepo.consumePartOnRepair(shop, inProgress, part, 10L)
            assertTrue(overConsume.isFailure)

            // Consume 1 OLED part
            opsRepo.consumePartOnRepair(shop, inProgress, part, 1L).getOrThrow()
        }

        // Verify part stock decremented to 2
        val updatedPart = withTimeout(TIMEOUT_MS) {
            opsRepo.observeSpareParts(shop.id).first { list ->
                list.firstOrNull { it.id == partId }?.stockQty == 2L
            }.first { it.id == partId }
        }
        assertEquals(2L, updatedPart.stockQty)

        // 7. Complete repair -> Generate Invoice -> Record Payment -> Deliver
        val repairWithPart = withTimeout(TIMEOUT_MS) {
            opsRepo.observeRepairs(shop.id).first { list ->
                list.firstOrNull { it.id == createdTicket.id }?.partsTotal == 180.0
            }.first { it.id == createdTicket.id }
        }
        withTimeout(TIMEOUT_MS) {
            opsRepo.transitionRepairStatus(shop, repairWithPart, RepairStatus.REPAIR_COMPLETED, "Tested FaceID and touch", StaffRole.OWNER).getOrThrow()
        }
        val completedRepair = withTimeout(TIMEOUT_MS) {
            opsRepo.observeRepairs(shop.id).first { list ->
                list.firstOrNull { it.id == createdTicket.id }?.status == RepairStatus.REPAIR_COMPLETED.name
            }.first { it.id == createdTicket.id }
        }

        // Labour 50 + Parts 180 - Discount 10 = 220; Tax 10% = 22; Grand Total = 242.0
        val invoice = withTimeout(TIMEOUT_MS) {
            opsRepo.generateOrUpdateInvoice(
                shop = shop,
                repair = completedRepair,
                labourCharge = 50.0,
                discountAmount = 10.0,
                taxRatePercent = 10.0,
                lineItemsSummary = "1x iPhone 15 Pro OLED",
            ).getOrThrow()
        }
        assertEquals(242.0, invoice.grandTotal, 0.01)

        withTimeout(TIMEOUT_MS) {
            opsRepo.recordPayment(
                shop = shop,
                invoice = invoice,
                repair = completedRepair,
                amount = 242.0,
                method = PaymentMethod.UPI,
                isAdvance = false,
                referenceNumber = "UPI-998811",
            ).getOrThrow()
        }

        val paidInvoice = withTimeout(TIMEOUT_MS) {
            opsRepo.observeInvoices(shop.id).first { list ->
                list.firstOrNull { it.id == invoice.id }?.paymentStatus == PaymentStatus.PAID.name
            }.first { it.id == invoice.id }
        }
        assertEquals(0.0, paidInvoice.balanceDue, 0.01)

        // Deliver device & verify public tracking
        withTimeout(TIMEOUT_MS) {
            opsRepo.transitionRepairStatus(shop, completedRepair, RepairStatus.DELIVERED, "Picked up by customer", StaffRole.OWNER).getOrThrow()
        }
        val tracking = withTimeout(TIMEOUT_MS) {
            shopRepo.lookupPublicTracking(createdTicket.ticketNumber, uniqueMobile.takeLast(4)).getOrThrow()
        }
        assertNotNull(tracking)
        assertEquals(RepairStatus.DELIVERED.name, tracking!!.status)
        assertEquals(PaymentStatus.PAID.name, tracking.paymentStatus)

        // Stock-in adjustment test
        withTimeout(TIMEOUT_MS) {
            opsRepo.adjustPartStock(shop, updatedPart, 5L, InventoryTxType.STOCK_IN, "Restock shipment").getOrThrow()
        }
        val restocked = withTimeout(TIMEOUT_MS) {
            opsRepo.observeSpareParts(shop.id).first { list ->
                list.firstOrNull { it.id == partId }?.stockQty == 7L
            }.first { it.id == partId }
        }
        assertEquals(7L, restocked.stockQty)
    }

    @Test
    fun crossShopIsolation_otherShopOwnerDeniedDirectAccess() = runBlocking {
        val ownerAEmail = "ownera_${UUID.randomUUID().toString().take(8)}@repairpilot.test"
        val ownerBEmail = "ownerb_${UUID.randomUUID().toString().take(8)}@repairpilot.test"

        val uidA = signInTestUser(ownerAEmail)
        val repoA = ShopRepository(firestore, auth)
        repoA.ensureUserAccount(uidA, "Owner A", ownerAEmail).getOrThrow()
        val shopA = repoA.createShop("Shop A", "SHA", "", "", "$", 8.0).getOrThrow()

        // Sign in as Owner B
        signInTestUser(ownerBEmail)
        var denied = false
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("shops").document(shopA.id).get().await()
            }
        } catch (e: Exception) {
            val fe = e.unwrapFirestoreException()
            if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Cross-tenant shop read must fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun unauthenticatedCaller_observeCustomers_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val opsRepo = RepairOperationsRepository(firestore, auth)
        var deniedOrAuthGate = false
        try {
            withTimeout(TIMEOUT_MS) {
                opsRepo.observeCustomers("shop_nonexistent").first()
            }
        } catch (e: Exception) {
            val fe = e.unwrapFirestoreException()
            if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || e is IllegalStateException) {
                deniedOrAuthGate = true
            }
        }
        assertTrue(deniedOrAuthGate)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
