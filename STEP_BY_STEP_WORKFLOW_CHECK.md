# RepairPilot — Step-by-Step Guide to Check the Complete Workflow

Follow these **8 steps in order** inside the RepairPilot app to test and verify every part of the workflow from sign-in to delivery, billing, and customer tracking.

---

## Step 1: Sign In & Create Your Shop
1. On the welcome screen, tap **Sign in with Google** and pick your Google account.
2. On the **Create Shop** tab:
   - **Shop Name:** `Apex Mobile Lab`
   - **Ticket Prefix:** `RP`
   - **Currency:** `$` *(or `₹`)*
   - **Tax %:** `10`
3. Tap **Launch Shop Workspace**.
   - **Expected Result:** You land on the **Dashboard** with the **Shop Owner** badge at the top.

---

## Step 2: Add a Spare Part to Inventory
1. Tap the **Inventory** tab (bottom bar).
2. Tap **+** (bottom-right) to open **Add Spare Part**.
3. Enter:
   - **Part Name:** `iPhone 15 OLED`
   - **SKU:** `IP15-OLED`
   - **Qty:** `3` | **Min Alert:** `2`
   - **Buy Cost:** `100` | **Sell Price:** `180`
4. Tap **Save Part**.
   - **Expected Result:** The part appears under **Spare Parts**, and switching to the **Stock Ledger** tab shows `+3` (`Opening stock`).

---

## Step 3: Register a Customer & Their Device
1. Tap the **Customers** tab.
2. Tap **+**, enter **Customer Name** (`Jordan Miles`) and **Mobile Number** (`9876543210`), and tap **Save**.
   - *(Optional check: Try adding another customer with `9876543210` — duplicate mobile numbers are blocked.)*
3. Tap **Jordan Miles** in the customer list to open their profile.
4. Tap **Register Device**, enter:
   - **Brand:** `Apple` | **Model:** `iPhone 15`
   - **Reported Issue:** `Cracked screen and dead touch`
5. Tap **Register**.
   - **Expected Result:** The device appears under **Devices (1)** on Jordan's profile.

---

## Step 4: Create a Repair Ticket
1. Tap the **Repairs** tab.
2. Tap **+** (bottom-right) to open **Create Repair Ticket**.
3. Verify **Jordan Miles** and **Apple iPhone 15** are selected, then enter:
   - **Problem Description:** `Display shattered after drop`
   - **Est. Cost:** `250` | **Labour Fee:** `50`
   - **Priority:** Tap `High`
4. Tap **Create Repair Ticket**.
   - **Expected Result:** Ticket **`RP-1001`** appears in the Repairs list with status **Received**.

---

## Step 5: Run the Bench Workflow (Status, Diagnosis & Spare Parts)
1. Tap **`RP-1001`** to open **Repair Detail**.
2. Under **Workflow Actions**, advance the status step by step:
   - Tap **Move to Diagnosing** → **Confirm**.
   - Tap **Add Note** → turn ON **Mark as Official Diagnosis** → type `OLED flex torn` → **Save**.
   - Tap **Move to Awaiting Approval** → **Confirm**.
   - Tap **Move to Approved** → **Confirm**.
   - Tap **Move to In Progress** → **Confirm**.
3. Consume the spare part:
   - Tap **Use Part** → tap **iPhone 15 OLED** → Quantity `1` → tap **Deduct & Add**.
   - **Expected Result:** **Spare Parts Used (1)** appears, the ticket total updates (`50 + 180 + 10% tax = $253.00`), **Inventory** drops from `3` to `2`, and a **Low Stock Alert** notification is created (`2 <= Min Alert 2`).
4. Complete bench work:
   - Tap **Move to Repair Completed** → **Confirm**.
   - Tap **Move to Ready for Pickup** → **Confirm**.

---

## Step 6: Generate Invoice, Record Payment & Export PDF
1. On **`RP-1001`**, tap the **Receipt / Invoice icon** in the top-right corner (or go to **Dashboard → Record Payment**).
2. Tap **+** (bottom-right) → verify **`RP-1001`** is selected → tap **Save Invoice**.
   - **Expected Result:** Invoice **`INV-5001`** shows **Unpaid** (`$253.00` due).
3. Tap **Record Payment** on the invoice card:
   - Keep full amount (`253.0`), select **`UPI`** (or **`Cash`**), optionally enter a reference, and tap **Record Payment**.
   - **Expected Result:** Invoice badge updates to **Paid** (`Due: $0.00`), and the payment appears in the **Payments** tab.
4. Tap **PDF Invoice** to generate and preview/share the invoice PDF.
5. Press **Back** to return to **Repairs**, open **`RP-1001`**, and tap **Move to Delivered** → **Confirm**.

---

## Step 7: Verify Customer Tracking Portal
1. Tap the **More** tab → **Repair Tracking & Customer Portal**.
2. Under **Customer Portal Check (No PII)**:
   - **Ticket #:** `RP-1001`
   - **Mobile Last 4 Digits:** `3210` *(from `9876543210`)*
3. Tap **Verify Customer View**.
   - **Expected Result:** Displays **`RP-1001`**, **Delivered**, **Paid**, and **`$0.00` due** without exposing customer phone/email/address.

---

## Step 8: Check Dashboard, Reports & Audit Trail
1. Tap **Dashboard**:
   - **Expected Result:** `Total Repairs = 1`, `Delivered = 1`, `Today's Revenue = $253.00`, `Pending Payments = $0.00`, the 7-day revenue chart updates, and **Recent Transactions** shows the payment.
2. Tap **More**:
   - **Reports & Analytics:** Review Daily/Weekly/Monthly revenue, profit, and technician metrics.
   - **Notifications:** Confirm status updates and low-stock alert appear.
   - **Audit Trail:** Confirm every action is permanently logged.
