# RepairPilot — Automated Testing & Verification Report

## Summary
- **Tier 1 Firestore Security Rules (`firestore.test.js`)**: **8 / 8 Passed** (`FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`)
- **Tier 2 Kotlin Repository & Emulator Rule Suite (`:app:testDebugUnitTest`)**: **9 / 9 Passed**
- **Mechanical Verification Greps (`app/src/main/`)**: **0 prohibited matches** (`signInAnonymously`, `signInWithEmailAndPassword`, `.apps.googleusercontent.com`, parameterless `Firebase.firestore` / `FirebaseFirestore.getInstance()`).

## Executed Test Suites

### 1. Firestore Security Rules (`firestore.test.js`) — 8/8 Passed
- Shop creation and owner `ShopMember` provisioning (`OWNER` role).
- Cross-tenant isolation: unauthenticated and non-member reads/lists rejected; empty `whereArrayContains("memberUids", uid)` returns 0 docs without leaking other shop records.
- RBAC enforcement on shop settings, expenses, and spare parts creation/consumption.
- Immutability of `auditLogs`, `payments`, `inventoryTransactions`, `statusHistory`, `notes`, and `partsUsed`.
- `publicTracking` single-document lookup allowed without PII; collection listing (`allow list: if false`) denied.

### 2. Android Unit, Robolectric & Emulator Integration Tests (`gradle :app:testDebugUnitTest`) — 9/9 Passed
- `RepairPilotRepositoryRuleTest`:
  - `fullEndToEndWorkflow_shopToDeliveryAndPublicTracking_persistsAndEnforcesRules` — **PASSED** (verifies Shop creation -> Customer registration & duplicate mobile rejection -> Device registration -> Spare part addition -> Repair ticket creation -> Status workflow transitions & diagnosis notes -> Insufficient stock guard -> Part consumption & stock decrement -> Invoice calculation -> UPI payment recording -> Delivery & `publicTracking` `PAID` projection -> Stock-in adjustment).
  - `crossShopIsolation_otherShopOwnerDeniedDirectAccess` — **PASSED**
  - `unauthenticatedCaller_observeCustomers_failsWithPermissionDenied` — **PASSED**
- `BillingAndWorkflowUnitTest`:
  - `billingCalculator_computesDecimalTaxDiscountAndBalance` — **PASSED**
  - `billingCalculator_capsDiscountAtSubtotalAndMarksPaid` — **PASSED**
  - `repairStatus_enforcesValidWorkflowTransitions` — **PASSED**
  - `trackingKey_sanitizesTicketAndLast4Digits` — **PASSED**
- `ExampleRobolectricTest`:
  - `read string from context` (verifies `app_name == "RepairPilot"` and non-blank `firestore_database_id`) — **PASSED**
- `ExampleUnitTest`:
  - `addition_isCorrect` — **PASSED**
