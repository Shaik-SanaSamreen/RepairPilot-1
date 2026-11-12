# RepairPilot — Starter Guide

This guide helps shop owners, staff, and developers get started with **RepairPilot**—from first launch and team onboarding to running automated tests locally.

---

## Part 1: Shop Owner & Staff Quick Start

### 1. First Launch & Sign In
1. Open **RepairPilot** and swipe through the onboarding cards.
2. Tap **Sign in with Google** and pick your Google account.

### 2. Create Your Shop Workspace (Shop Owner)
1. On the **Create Shop** tab:
   - Enter your **Shop Name** (e.g., `Apex Mobile Lab`).
   - Set a short **Ticket Prefix** (e.g., `RP` → tickets become `RP-1001`, `RP-1002`).
   - Choose your **Currency** (`$`, `₹`, `€`, etc.) and **Default Tax %**.
   - Add your business phone and address (shown on exported PDF invoices).
2. Tap **Launch Shop Workspace**. You are automatically assigned the **Shop Owner** (`OWNER`) role.

### 3. Invite Technicians & Receptionists
1. Go to **More → Technicians & Staff Team**.
2. Tap **Invite Staff**, enter the staff member's name, choose **Technician** or **Receptionist**, and generate a single-use invite code (e.g., `INV_A1B2C3D4`).
3. Have your staff member sign in with their Google account, select the **Join Staff** tab, and enter the invite code.
4. *(Tip)* After new staff join, tap **Sync Access** under Shop Members if needed so existing tickets/customers are visible to the newly added team member.

### 4. Daily Repair Shop Routine
1. **Stock Spare Parts (`Inventory` tab):** Add screens, batteries, and ports with SKU, selling price, and minimum stock alert levels.
2. **Register Customer & Device (`Customers` tab):** Add the customer's name and mobile number, open their profile, tap **Register Device**, and attach intake photos if needed.
3. **Create a Repair Ticket (`Repairs` tab):** Tap **+**, pick Customer + Device, enter the reported issue, labour fee, priority, and assigned technician.
4. **Update Bench Progress (`Repair Detail`):**
   - Advance status step-by-step: `Received → Diagnosing → Awaiting Approval → Approved → In Progress → Repair Completed → Ready for Pickup → Delivered`.
   - Tap **Add Note** to log official diagnosis or bench notes.
   - Tap **Use Part** to consume a spare part—inventory deducts automatically and updates the ticket total.
5. **Invoice & Collect Payment (`Billing`):**
   - Open the invoice icon on the ticket (or **Dashboard → Record Payment**).
   - Tap **+** to generate/update the itemized invoice, tap **Record Payment** (`Cash`, `UPI`, `Bank Transfer`, or `Card`), and tap **PDF Invoice** to share a copy with the customer.
6. **Customer Tracking (`Track Ticket` / `More → Repair Tracking`):**
   - Customers only need their **Ticket Number** + **Last 4 digits of their mobile** to view repair status and remaining balance.

---

## Part 2: Developer Setup & Verification

### Prerequisites
- Android Studio / JDK 11+ (Gradle configured for `compileSdk 36`, `minSdk 24`, `targetSdk 36`)
- Firebase project provisioned with `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`)
- Node.js + Firebase CLI (`firebase-tools`) for running local emulator security rule tests

### Build the App
```bash
gradle :app:assembleDebug
```

### Run Security Rules Tests (Tier 1 — Node + Emulator)
```bash
firebase emulators:start --only firestore,auth --project demo-no-project
# In another terminal:
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Run Full Android Unit, Robolectric & Emulator Tests (Tier 2)
With the Firestore (`127.0.0.1:8085`) and Auth (`127.0.0.1:9099`) emulators running:
```bash
gradle :app:testDebugUnitTest
```

### Distribution Options
- **Browser Preview:** Share the AI Studio **Shared App URL** so testers can run RepairPilot in the Streaming Android Emulator.
- **Device Install / Store Release:** Generate the **APK** or **AAB** via AI Studio's settings menu (or `gradle :app:assembleRelease` with release keystore credentials configured).
