# RepairPilot — Flutter Setup Guide & Master Prompt (iOS, Android & Web)

Use this document when building a cross-platform **Flutter** version of **RepairPilot** (supporting **iPhone/iPad**, **Android**, and **Web**) connected to your existing Firebase backend or a fresh Firebase project.

---

## Part 1: Quick Start Guide for Flutter

1. **Prerequisites:**
   - Install **Flutter SDK** (`flutter doctor` to verify).
   - Install **Firebase CLI** and **FlutterFire CLI** (`dart pub global activate flutterfire_cli`).
   - Use **VS Code / Cursor / Android Studio** (macOS with Xcode is required to compile iOS `.ipa` builds; Windows/Linux can build Android `.apk`/`.aab` and Web).
2. **Create & Connect to Existing RepairPilot Firebase Project:**
   ```bash
   flutter create repair_pilot_flutter
   cd repair_pilot_flutter
   flutterfire configure --project=gen-lang-client-0844596965
   ```
   - When connecting to the existing named Firestore database in this project, initialize Firestore with:
     ```dart
     FirebaseFirestore.instanceFor(
       app: Firebase.app(),
       databaseId: 'ai-studio-android-repairpi-8dfd21d4-c5bd-46d8-b3f1-075673842483',
     )
     ```
3. **Run & Deploy Targets:**
   - **iOS (macOS):** `flutter run -d ios` / `flutter build ipa`
   - **Android:** `flutter run -d android` / `flutter build apk --release`
   - **Web (Deployable to Vercel / Firebase Hosting):** `flutter build web` (deploy `build/web` to Vercel)

---

## Part 2: Copy-Paste Master Prompt for Any AI Agent

Copy everything inside the prompt section below and paste it into your AI coding agent inside an empty Flutter repository:

````markdown
# MASTER PROMPT — BUILD REPAIRPILOT (FLUTTER CROSS-PLATFORM: iOS, ANDROID & WEB)

Act as a Senior Flutter Architect, Backend/Firebase Engineer, UI/UX Designer, and QA Engineer.
Build **RepairPilot**, a complete, production-ready, multi-tenant **Mobile Repair Shop Management System** using **Flutter (Dart)** supporting **iOS, Android, and Web**, backed by **Firebase Authentication (Google Sign-In)** and **Cloud Firestore** with Zero-Trust security rules.

Do NOT build a UI mockup with hardcoded/mock data. Implement complete state management, transactional database operations, role-based access control, validation, PDF invoice generation, and automated unit + widget + rules tests.

---

## 1. TECH STACK & PACKAGES
- **Framework:** Flutter (latest stable, Material 3, adaptive layout for Mobile `NavigationBar` + Tablet/Web `NavigationRail`).
- **State Management & Routing:** `flutter_riverpod` + `go_router`.
- **Backend & Auth:** `firebase_core`, `firebase_auth`, `google_sign_in`, `cloud_firestore` (configure for named database ID if provided, defaulting to `FirebaseFirestore.instanceFor(app: Firebase.app(), databaseId: ...)` via `--dart-define=FIRESTORE_DB_ID=...` or `(default)`).
- **Utilities:** `intl` (currency/dates), `decimal` (exact financial math, no floating-point drift), `image_picker` (zero-permission photo selection on iOS/Android), `pdf` + `printing` (A4 PDF invoice generation & sharing), `fl_chart` (7-day revenue bar chart), `uuid`.

---

## 2. MULTI-TENANCY & ROLE-BASED ACCESS CONTROL (RBAC)
Every shop is isolated under `/shops/{shopId}`. Every operational document inside a shop stores a denormalized `memberUids: List<String>` field so all Firestore list queries filter via `.where('memberUids', arrayContains: currentUid)` without cross-document reads in list rules.

Support 3 roles stored in `/shops/{shopId}/members/{uid}`:
1. **`OWNER` (Shop Owner / Admin):** Full access—shop settings, team invites, technicians, expenses, adding spare part SKUs, inventory adjustments, repairs (including terminal-state override), billing/payments, reports, audit logs.
2. **`TECHNICIAN`:** View bench/shop repairs, update repair status along valid transitions, add official diagnosis/repair notes, consume spare parts on repairs, adjust stock (`STOCK_IN`/`STOCK_OUT`), view repair tracking & notifications. Restricted from shop settings, expenses, and financial totals.
3. **`RECEPTIONIST`:** Register/edit customers and devices, create repair tickets, assign technicians, manage workflow updates, generate invoices, record payments, export PDFs, view reports & tracking. Restricted from shop settings, expenses, and adding/modifying spare part SKUs.

---

## 3. FIRESTORE DATABASE SCHEMA
Implement models (`fromFirestore` / `toMap` without writing document `id` as a field) and repositories for:
- `/users/{uid}`: `uid`, `displayName`, `email`, `activeShopId`, `activeRole`, timestamps.
- `/shops/{shopId}`: `ownerUid`, `shopName`, `shopCode`, `phone`, `address`, `currencySymbol`, `taxRatePercent`, `nextTicketSeq`, `nextInvoiceSeq`, `memberUids`.
- `/shops/{shopId}/members/{uid}`: `uid`, `shopId`, `displayName`, `email`, `role`, `specialty`, `active`, `inviteCode`, `memberUids`.
- `/shopInvites/{inviteCode}`: single-use team join code (`shopId`, `shopName`, `ownerUid`, `staffName`, `role`, `specialty`, `active`, `usedByUid`).
- `/shops/{shopId}/technicians/{techId}`: `uid`, `name`, `phone`, `specialty`, `active`, `assignedCount`, `completedCount`, `memberUids`.
- `/shops/{shopId}/customers/{customerId}`: `name`, `mobile` (duplicate mobile check enforced per shop), `email`, `address`, `active`, `totalRepairs`, `memberUids`.
- `/shops/{shopId}/devices/{deviceId}`: `customerId`, `customerName`, `brand`, `model`, `imeiOrSerial`, `deviceType`, `color`, `condition`, `accessoriesReceived`, `reportedIssue`, `photoUris`, `memberUids`.
- `/shops/{shopId}/repairs/{repairId}`: `ticketNumber` (`<SHOP_CODE>-<SEQ>` via Firestore transaction), `customerId`, `customerName`, `customerMobile`, `deviceId`, `deviceSummary`, `problemDescription`, `initialDiagnosis`, `estimatedCost`, `estimatedCompletionDate`, `assignedTechUid`, `assignedTechName`, `priority` (`LOW`, `NORMAL`, `HIGH`, `URGENT`), `status`, `intakeCondition`, `repairNotes`, `labourCharge`, `partsTotal`, `discountAmount`, `taxRatePercent`, `totalAmount`, `paidAmount`, `invoiceId`, `photoUris`, `memberUids`.
  - Subcollections (append-only / immutable):
    - `statusHistory/{historyId}`: `fromStatus`, `toStatus`, `note`, `changedByUid`, `changedByName`.
    - `notes/{noteId}`: `content`, `isDiagnosis`, `authorUid`, `authorName`, `authorRole`.
    - `partsUsed/{usageId}`: `partId`, `partName`, `sku`, `quantity`, `unitPrice`, `lineTotal`, `addedByUid`.
- `/shops/{shopId}/parts/{partId}`: `name`, `sku`, `category`, `stockQty`, `purchasePrice`, `sellingPrice`, `minStockLevel`, `supplierName`, `supplierContact`, `active`, `memberUids`.
- `/shops/{shopId}/inventoryTransactions/{txId}` (immutable): `partId`, `partName`, `txType` (`STOCK_IN`, `STOCK_OUT`, `REPAIR_CONSUME`), `quantityDelta`, `stockBefore`, `stockAfter`, `unitCost`, `referenceTicket`, `note`, `performedByUid`.
- `/shops/{shopId}/invoices/{invoiceId}`: `invoiceNumber` (`INV-<SEQ>`), `repairId`, `ticketNumber`, `customerId`, `customerName`, `customerMobile`, `deviceSummary`, `labourCharge`, `partsTotal`, `discountAmount`, `taxRatePercent`, `taxAmount`, `grandTotal`, `paidAmount`, `balanceDue`, `paymentStatus` (`UNPAID`, `PARTIAL`, `PAID`), `lineItemsSummary`.
- `/shops/{shopId}/payments/{paymentId}` (immutable): `invoiceId`, `repairId`, `ticketNumber`, `customerName`, `amount`, `method` (`CASH`, `UPI`, `BANK_TRANSFER`, `CARD`), `isAdvance`, `referenceNumber`, `receivedByUid`, `receivedByName`.
- `/shops/{shopId}/expenses/{expenseId}`: `title`, `category`, `amount`, `note`, `recordedByUid`.
- `/shops/{shopId}/notifications/{notificationId}`: `title`, `message`, `eventType`, `repairId`, `ticketNumber`, `isRead`.
- `/shops/{shopId}/auditLogs/{logId}` (immutable): `action`, `entityType`, `entityId`, `summary`, `actorUid`, `actorName`, `actorRole`.
- `/publicTracking/{ticketNumber_mobileLast4}`: Sanitized non-PII tracking projection (`shopId`, `shopName`, `ticketNumber`, `deviceSummary`, `status`, `estimatedCompletionDate`, `paymentStatus`, `totalAmount`, `paidAmount`, `balanceDue`, `timelineSummary`). Collection listing (`allow list: if false`) must be denied in `firestore.rules`.

---

## 4. CORE BUSINESS RULES & STATE MACHINE
1. **12-State Repair Lifecycle (Strict Transitions):**
   - `RECEIVED` -> `DIAGNOSING`, `APPROVED`, `CANCELLED`
   - `DIAGNOSING` -> `AWAITING_CUSTOMER_APPROVAL`, `APPROVED`, `IN_PROGRESS`, `UNABLE_TO_REPAIR`, `CANCELLED`
   - `AWAITING_CUSTOMER_APPROVAL` -> `APPROVED`, `REJECTED`, `CANCELLED`
   - `APPROVED` -> `IN_PROGRESS`, `WAITING_FOR_PARTS`, `CANCELLED`
   - `IN_PROGRESS` -> `WAITING_FOR_PARTS`, `REPAIR_COMPLETED`, `UNABLE_TO_REPAIR`, `CANCELLED`
   - `WAITING_FOR_PARTS` -> `IN_PROGRESS`, `REPAIR_COMPLETED`, `UNABLE_TO_REPAIR`, `CANCELLED`
   - `REPAIR_COMPLETED` -> `READY_FOR_PICKUP`, `DELIVERED`
   - `READY_FOR_PICKUP` -> `DELIVERED`
   - `UNABLE_TO_REPAIR` -> `READY_FOR_PICKUP`, `DELIVERED`, `CANCELLED`
   - `DELIVERED`, `REJECTED`, `CANCELLED` -> Terminal (Owner override only).
2. **Atomic Part Consumption:** Use a Firestore transaction to verify `stockQty >= qty`, decrement stock, write `partsUsed` + `inventoryTransactions`, recalculate `repair.partsTotal` and `repair.totalAmount`, and create a `LOW_STOCK` notification if `stockAfter <= minStockLevel`.
3. **Exact Billing Math (Decimal / Half-Up):**
   - `subtotal = labourCharge + partsTotal`
   - `discountApplied = min(discountAmount, subtotal)`
   - `taxable = subtotal - discountApplied`
   - `taxAmount = taxable * (taxRatePercent / 100)`
   - `grandTotal = taxable + taxAmount`
   - `balanceDue = max(0, grandTotal - paidAmount)`
   - Re-sync `publicTracking` whenever ticket status, invoice totals, or payments change (always reading fresh repair totals, never stale UI state).

---

## 5. SCREENS & UX
1. **Onboarding & Google Sign-In Screen** (3-slide pager + Google Sign-In button).
2. **Shop Setup & Portal Screen** (3 tabs: `Create Shop`, `Join Staff` with invite code, `Track Ticket` public lookup without needing a shop).
3. **Adaptive App Shell** (`NavigationBar` on phones < 600dp, `NavigationRail` on tablets/web >= 600dp):
   - **Dashboard:** Role pill, unread notifications badge, low-stock alert banner, quick action chips (`New Ticket`, `Add Customer`, `Track Status`, `Record Payment`), KPI grid, 7-day revenue bar chart, recent tickets, recent transactions.
   - **Repairs:** Search by ticket/customer/mobile/device, status filter chips, `My Bench` filter for technicians, Create Ticket bottom sheet (with `image_picker`), and **Repair Detail Screen** (horizontal workflow stepper, transition action buttons, diagnosis/repair notes, spare parts consumed, technician assignment, and status audit timeline).
   - **Customers & Devices:** Searchable customer list, customer detail with multiple devices & repair history, duplicate mobile validation.
   - **Inventory:** Parts catalog + Stock Ledger tabs, low-stock filter, Stock-In / Stock-Out dialogs.
   - **Billing & Invoices:** Unpaid filter, generate/update itemized invoice dialog with live tax/total preview, record payment dialog (Cash/UPI/Bank/Card + Advance toggle), and **PDF Invoice generation & sharing**.
   - **More Hub:** Repair Tracking (Staff Lookup + Non-PII Customer Check), Reports & Analytics (Daily/Weekly/Monthly revenue, Expenses, Net Profit, Technician Performance, CSV export), Technicians & Staff Invites (`Sync Access` action), Shop Expenses (`OWNER`), Notifications, Immutable Audit Trail, and Shop Settings (`OWNER`).

---

## 6. EXECUTION PHASES & DELIVERABLES
1. Initialize Flutter project structure (`lib/models`, `lib/repositories`, `lib/providers`, `lib/ui`, `lib/utils`), theme (Navy `#0F294A`, Royal Blue `#2563EB`, Cyan `#06B6D4`, `Plus Jakarta Sans` + `Inter` + `JetBrains Mono`), and iOS/Android permissions (`NSPhotoLibraryUsageDescription` in `ios/Runner/Info.plist`).
2. Write hardened `firestore.rules` enforcing zero-trust auth, `memberUids` tenant isolation, RBAC, and immutable ledgers/audits.
3. Implement all models, `BillingCalculator`, repositories, Riverpod providers, and responsive UI screens.
4. Add unit tests for `BillingCalculator` & status transitions, widget tests for key flows, and Firestore security rule tests.
5. Verify `flutter analyze` and `flutter test` pass with 0 errors, then provide clear iOS, Android, and Web build/run instructions.
````
