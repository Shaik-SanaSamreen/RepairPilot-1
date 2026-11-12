# RepairPilot — Service & Security Rules Contract

Because RepairPilot runs directly on Cloud Firestore + Firebase Auth in this native Android build, backend contracts are implemented via transactional repositories (`ShopRepository`, `RepairOperationsRepository`) and enforced server-side by `firestore.rules`.

## Authentication & Role-Based Access Control
- **Google Sign-In**: Credential Manager (`GetSignInWithGoogleOption` / `GetGoogleIdOption`) exchanges Google ID tokens for Firebase Auth sessions.
- **Roles**:
  - `OWNER`: Full shop management, settings, expenses, inventory SKU creation, team invites, repair overrides.
  - `TECHNICIAN`: Assigned repair bench, status transitions, diagnosis/repair notes, spare parts consumption & stock adjustments.
  - `RECEPTIONIST`: Customer & device registration, repair ticket intake, tracking, billing & payment collection.

## Core Transactional Operations
1. `ShopRepository.createShop(...)` -> Atomically creates `/shops/{shopId}`, owner `members/{uid}`, `technicians/tech_{uid}`, initial `auditLogs` entry, and updates `/users/{uid}`.
2. `ShopRepository.joinShopWithInvite(code)` -> Atomically redeems `/shopInvites/{code}`, creates `members/{uid}`, appends UID to `shop.memberUids`, and sets `users/{uid}.activeShopId`.
3. `RepairOperationsRepository.addCustomer(...)` -> Enforces non-duplicate `mobile` check within the shop before persisting `/shops/{shopId}/customers/{id}`.
4. `RepairOperationsRepository.createRepairTicket(...)` -> Runs Firestore transaction incrementing `shop.nextTicketSeq`, creating `repairs/{id}`, initial `statusHistory`, `auditLogs`, `notifications`, and sanitized `publicTracking/{TICKET_LAST4}`.
5. `RepairOperationsRepository.transitionRepairStatus(...)` -> Validates allowed state machine transitions (`RepairStatus.allowedNextStatuses()`), appends `statusHistory`, updates `repairs/{id}` and `publicTracking`, and creates customer/staff `notifications`.
6. `RepairOperationsRepository.consumePartOnRepair(...)` -> Runs Firestore transaction checking `stockQty >= qty`, decrementing `parts/{partId}.stockQty`, writing `partsUsed`, writing `inventoryTransactions (REPAIR_CONSUME)`, recalculating `repair.partsTotal` & `totalAmount` via `BillingCalculator`, and emitting a low-stock notification if `stockAfter <= minStockLevel`.
7. `RepairOperationsRepository.generateOrUpdateInvoice(...)` & `recordPayment(...)` -> Uses `BigDecimal` (`HALF_UP`) math in `BillingCalculator`, updates `invoices/{id}`, `repairs/{id}`, `publicTracking`, and appends immutable `payments/{id}`.
8. `ShopRepository.lookupPublicTracking(ticketNumber, mobileLast4)` -> Direct document `.get()` on `/publicTracking/{TICKET}_{LAST4}` (`allow list: if false` in rules).
