# RepairPilot — Mobile Repair Shop Management System

RepairPilot is a multi-tenant, role-based Android application built for mobile repair shops to manage the entire repair lifecycle—from customer & device intake to technician diagnosis, spare parts inventory, billing/PDF invoicing, payment collection, analytics, and customer repair status tracking.

---

## Core Features

- **Google Authentication & Multi-Role RBAC**
  - Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption` / silent `GetGoogleIdOption`).
  - Shop Owner creates a multi-tenant shop workspace; Technicians and Receptionists join via single-use invite codes (`shopInvites`).
  - Role-gated UI and server-enforced Firestore Security Rules (`OWNER`, `TECHNICIAN`, `RECEPTIONIST`).
- **Real-Time Operations Dashboard**
  - Live KPI cards: Total Repairs, Pending Intake, In Progress, Completed, Delivered, Today's Revenue, Pending Payments, and Low-Stock Alerts.
  - 7-day Revenue Trend chart, quick-action shortcuts, recent tickets, and recent transactions.
- **Customer & Device Registry**
  - Customer registration with duplicate mobile number prevention, search, and edit/deactivation.
  - Multi-device tracking per customer (brand, model, IMEI/serial, type, color, physical condition, accessories received, reported issue) with zero-permission Android Photo Picker (`PickVisualMedia`).
- **Validated 12-State Repair Ticket Workflow**
  - `Received → Diagnosing → Awaiting Approval → Approved → In Progress (⇄ Waiting for Parts) → Repair Completed → Ready for Pickup → Delivered`
  - Exception branches: `Rejected`, `Cancelled`, `Unable to Repair`.
  - Immutable `statusHistory`, official diagnosis notes, technician assignment, and repair notes.
- **Spare Parts Inventory & Stock Ledger**
  - SKU catalog with buy/sell pricing, supplier info, and minimum stock threshold alerts.
  - Transactional Stock-In, Stock-Out, and Repair Consumption (`REPAIR_CONSUME`) that deducts stock, prevents negative inventory, updates repair totals, and triggers low-stock alerts.
- **Billing, Payments & Native PDF Invoices**
  - `BigDecimal` (`HALF_UP`) calculation for labour, parts, discounts, tax %, and balance due (`BillingCalculator`).
  - Supports `Cash`, `UPI`, `Bank Transfer`, and `Card`, including advance and partial payments.
  - Exports branded invoices using Android `PdfDocument` and shares them via `FileProvider`.
- **Customer Tracking Portal & Auditability**
  - Non-PII `publicTracking` lookup keyed by `TICKETNUMBER_LAST4MOBILE` (`allow list: if false` against scraping).
  - Analytics reports with shareable CSV summary, shop expenses ledger, notifications, immutable audit logs, and adaptive `NavigationBar` / `NavigationRail` layouts.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin (`2.2.10`), Java 11 bytecode target |
| **UI Framework** | Jetpack Compose (`composeBom 2024.09.00`), Material Design 3, Material Icons Extended |
| **Architecture & State** | MVVM + Repository Pattern, `ViewModel`, `StateFlow`, `collectAsStateWithLifecycle`, Kotlin Coroutines & Flow (`1.10.2`) |
| **Navigation & Media** | Compose Navigation (`2.8.9`) / state-driven adaptive shell (`NavigationBar` + `NavigationRail`), Coil Compose (`2.7.0`), Android Photo Picker (`PickVisualMedia`) |
| **Authentication** | Firebase Auth + Jetpack Credential Manager (`1.5.0`) + Google Identity (`googleid 1.1.1`) |
| **Backend & Database** | Cloud Firestore (Firebase BoM `34.17.0`, named Enterprise database) + Zero-Trust `firestore.rules` |
| **PDF & Notifications** | Android `android.graphics.pdf.PdfDocument`, `FileProvider`, `NotificationCompat` |
| **Build & Tooling** | Android Gradle Plugin (`9.1.1`), Gradle Kotlin DSL, Google Services (`4.5.0`), Secrets Gradle Plugin (`2.0.1`), `compileSdk 36` / `targetSdk 36` / `minSdk 24` |
| **Testing** | JUnit 4 (`4.13.2`), Robolectric (`4.16.1`), `kotlinx-coroutines-test`, `@firebase/rules-unit-testing` (Node test runner + Firebase Firestore/Auth Emulators) |

---

## Project Structure

- `app/src/main/java/com/example/MainActivity.kt` — Edge-to-edge activity host.
- `app/src/main/java/com/example/ui/RepairPilotApp.kt` — Auth gate, adaptive navigation shell, and screen routing.
- `app/src/main/java/com/example/model/RepairPilotModels.kt` — Domain models, enums, and state machine transitions.
- `app/src/main/java/com/example/data/` — `ShopRepository`, `RepairOperationsRepository`, and `FirestoreErrorHandler`.
- `app/src/main/java/com/example/viewmodel/RepairPilotViewModel.kt` — Reactive state aggregation & KPIs.
- `app/src/main/java/com/example/ui/` — Modular UI screens (`auth`, `dashboard`, `repairs`, `customers`, `inventory`, `billing`, `more`, `components`, `theme`).
- `app/src/main/java/com/example/util/` — `BillingCalculator`, `PdfInvoiceGenerator`, `LocalNotifier`.
- `firestore.rules` & `firebase-blueprint.json` — Security rules and schema specification.
- `firestore.test.js` & `app/src/test/` — Security rules, repository integration, billing math, and Robolectric tests.

---

## Additional Documentation

- `STARTER_GUIDE.md` — First-time setup, shop onboarding, role guide, and testing instructions.
- `DATABASE_SCHEMA.md` — Firestore collections and subcollection structure.
- `API_DOCUMENTATION.md` — Repository transactions and RBAC enforcement details.
- `DEVELOPMENT_PROGRESS.md` — Implementation phase summary.
- `TESTING_REPORT.md` — Executed test results.
