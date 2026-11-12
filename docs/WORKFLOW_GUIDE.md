# RepairPilot — Complete Workflow Guide

This guide explains the full operational workflow inside **RepairPilot**, including user roles, ticket status transitions, inventory & billing automation, a step-by-step verification walkthrough, and a client presentation script.

---

## 1. End-to-End Shop Operations Flow

```
Google Sign-In
   ├──► Shop Owner: Create Shop Workspace (Prefix, Currency, Tax %)
   │       └──► Generate Invite Codes for Technicians & Receptionists
   └──► Staff: Redeem Invite Code ("Join Staff")
           │
           ▼
[1] Inventory Setup (Add Spare Parts, SKU, Buy/Sell Price, Min Alert)
           │
           ▼
[2] Customer & Device Intake (Unique Mobile Check + Multi-Device + Photos)
           │
           ▼
[3] Create Repair Ticket (Auto Ticket # e.g. RP-1001, Priority, Tech Assignment)
           │
           ▼
[4] Repair Execution & Bench Workflow
    ├── Status Transitions (Validated State Machine + Audit History)
    ├── Diagnosis & Repair Notes
    └── Consume Spare Parts (Atomic Stock Deduction + Low-Stock Alert + Bill Update)
           │
           ▼
[5] Billing & Payment Collection
    ├── Generate/Update Invoice (Labour + Parts - Discount + Tax %)
    ├── Record Payments (Cash / UPI / Bank Transfer / Card — Full, Partial, Advance)
    └── Export & Share Branded PDF Invoice
           │
           ▼
[6] Delivery & Customer Tracking
    └── Customer checks status & balance via Ticket # + Last 4 Mobile Digits
```

---

## 2. User Roles & Permissions (RBAC)

| Capability | Shop Owner (`OWNER`) | Receptionist (`RECEPTIONIST`) | Technician (`TECHNICIAN`) |
| :--- | :---: | :---: | :---: |
| Create Shop / Edit Shop Settings | Yes | — | — |
| Invite Staff & Add Technicians | Yes | — | — |
| View Expenses & Log Shop Expenses | Yes | — | — |
| Add New Spare Part SKUs | Yes | — | — |
| Adjust Stock (`Stock In` / `Stock Out`) | Yes | — | Yes |
| Consume Parts on a Repair Ticket | Yes | — | Yes |
| Register/Edit Customers & Devices | Yes | Yes | View Only |
| Create Repair Tickets & Assign Techs | Yes | Yes | View Bench / All |
| Transition Repair Status & Add Notes | Yes *(+ Override)* | Yes | Yes |
| Generate Invoices & Record Payments | Yes | Yes | — |
| View Dashboard Revenue & Reports | Yes | Yes | Operational KPIs Only |
| Audit Trail, Notifications & Tracking | Yes | Yes | Yes |

---

## 3. Repair Ticket Status Workflow (12 States)

Every repair ticket enforces valid transitions and appends an immutable entry (`fromStatus`, `toStatus`, `note`, `changedByName`, timestamp) to **Status Audit History**.

### Standard Forward Path
1. **`Received`** → Device checked in at front desk.
2. **`Diagnosing`** → Technician inspects device and logs initial findings.
3. **`Awaiting Approval`** → Cost/diagnosis shared with customer for confirmation.
4. **`Approved`** → Customer approved repair estimate.
5. **`In Progress`** ⇄ **`Waiting for Parts`** → Active bench repair (or paused while sourcing parts).
6. **`Repair Completed`** → Bench repair and QC testing finished.
7. **`Ready for Pickup`** → Ready at front desk; customer notified.
8. **`Delivered`** *(Terminal)* → Handed back to customer.

### Exception / Branch Paths
- **`Rejected`** *(Terminal)* — Triggered from `Awaiting Approval` if customer declines estimate.
- **`Cancelled`** *(Terminal)* — Available from `Received`, `Diagnosing`, `Awaiting Approval`, `Approved`, `In Progress`, `Waiting for Parts`, or `Unable to Repair`.
- **`Unable to Repair`** — Available from `Diagnosing`, `In Progress`, or `Waiting for Parts`; can move to `Ready for Pickup`, `Delivered`, or `Cancelled`.
- **Owner Override:** Shop Owners can reopen a terminal ticket to `In Progress` or `Ready for Pickup` if needed.

### Transition Matrix

| Current Status | Allowed Next Statuses |
| :--- | :--- |
| `Received` | `Diagnosing`, `Approved`, `Cancelled` |
| `Diagnosing` | `Awaiting Approval`, `Approved`, `In Progress`, `Unable to Repair`, `Cancelled` |
| `Awaiting Approval` | `Approved`, `Rejected`, `Cancelled` |
| `Approved` | `In Progress`, `Waiting for Parts`, `Cancelled` |
| `In Progress` | `Waiting for Parts`, `Repair Completed`, `Unable to Repair`, `Cancelled` |
| `Waiting for Parts` | `In Progress`, `Repair Completed`, `Unable to Repair`, `Cancelled` |
| `Repair Completed` | `Ready for Pickup`, `Delivered` |
| `Ready for Pickup` | `Delivered` |
| `Unable to Repair` | `Ready for Pickup`, `Delivered`, `Cancelled` |
| `Delivered` / `Rejected` / `Cancelled` | *Terminal (Owner override only)* |

---

## 4. Automated Inventory, Billing & Tracking Sync

- **Duplicate Customer Guard:** Adding a customer checks normalized mobile numbers inside the shop to prevent duplicate profiles.
- **Transactional Spare Part Consumption:** Consuming a part on a repair ticket atomically:
  1. Verifies `stockQty >= quantity` (blocks negative stock).
  2. Decrements `stockQty` on the part.
  3. Writes an immutable `REPAIR_CONSUME` entry to the **Stock Ledger**.
  4. Adds the part cost (`unitPrice × quantity`) to the ticket's `partsTotal` and recalculates `totalAmount`.
  5. Creates a **Low Stock Alert** notification if `stockAfter <= minStockLevel`.
- **Exact Financial Calculations (`BigDecimal`):**
  - `subtotal = labourCharge + partsTotal`
  - `discountApplied = min(discountAmount, subtotal)`
  - `taxableAmount = subtotal - discountApplied`
  - `taxAmount = taxableAmount × (taxRatePercent / 100)`
  - `grandTotal = taxableAmount + taxAmount`
  - `balanceDue = max(0, grandTotal - paidAmount)`
  - `paymentStatus`: `UNPAID` (`paid == 0`), `PARTIAL` (`0 < paid < grandTotal`), or `PAID` (`balanceDue <= 0.01`).
- **Sanitized Public Tracking:** Ticket creation, status changes, invoice updates, and payments automatically sync a non-PII summary (`ticketNumber`, `deviceSummary`, `status`, `paymentStatus`, `totalAmount`, `paidAmount`, `balanceDue`) keyed by `<TICKET>_<LAST4_MOBILE>`. Collection listing is disabled so customer data cannot be scraped.

---

## 5. Step-by-Step Verification Checklist (Demo / QA)

Use this 6-step walkthrough to test or demonstrate the full workflow:

1. **Create Shop:** Sign in with Google → enter Shop Name, Prefix (`RP`), Currency, Tax % → tap **Launch Shop Workspace**.
2. **Add Stock:** Open **Inventory** → tap **+** → add a part (e.g., `OLED Display`, Qty `5`, Min Alert `2`, Sell Price `150`).
3. **Register Customer & Device:** Open **Customers** → tap **+** → save customer (`9876543210`) → open customer → tap **Register Device** → save phone details.
4. **Create & Run Repair Ticket:**
   - Open **Repairs** → tap **+** → pick Customer + Device + Problem + Labour (`50`) → **Create Repair Ticket** (e.g., `RP-1001`).
   - Open `RP-1001` → move `Received → Diagnosing → Approved → In Progress`.
   - Tap **Add Note** (enable **Mark as Official Diagnosis**) → save.
   - Tap **Use Part** → select `OLED Display` (Qty `1`) → **Deduct & Add** (verify Inventory drops to `4` and ticket total updates).
   - Move status to **Repair Completed** → **Ready for Pickup**.
5. **Invoice, Payment & PDF:**
   - Tap the **Invoice icon** on the ticket (or **Dashboard → Record Payment**).
   - Tap **+** → **Save Invoice** → tap **Record Payment** (`UPI` or `Cash`) → tap **PDF Invoice** to export/share.
   - Return to the ticket and tap **Move to Delivered**.
6. **Verify Analytics & Tracking:**
   - Check **Dashboard** KPIs and 7-day revenue chart.
   - Open **More → Repair Tracking → Customer Portal Check** → enter `RP-1001` and `3210` → confirm **Delivered** & **Paid**.
   - Inspect **More → Audit Trail** and **Notifications**.

---

## 6. How to Explain This to a Client

> *"RepairPilot tracks every device from drop-off to payment in one place. At reception, staff log the customer, device condition, and photos so there are never disputes about prior damage. On the repair bench, technicians move the job through clear stages—Diagnosing, Waiting for Approval, In Progress, Waiting for Parts, Ready for Pickup—while every update is timestamped. When a technician uses a spare part, inventory deducts automatically, low-stock alerts trigger early, and the part is added straight to the bill. At checkout, you generate a tax-ready PDF invoice, accept Cash, UPI, Card, or partial payments, and give customers instant status tracking using just their ticket number and the last 4 digits of their phone."*
