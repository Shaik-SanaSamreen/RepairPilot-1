# RepairPilot — Cloud Firestore Database Schema

All tenant-scoped operational collections live under `/shops/{shopId}/...` in named database `ai-studio-android-repairpi-8dfd21d4-c5bd-46d8-b3f1-075673842483`. Every tenant document carries a denormalized `memberUids: string[]` array so Firestore `allow list` queries filter strictly by `whereArrayContains("memberUids", uid)` without cross-document `get()` calls.

## Top-Level Collections
| Collection Path | Document Key | Purpose |
| :--- | :--- | :--- |
| `/users/{userId}` | Firebase Auth UID | User profile (`displayName`, `email`, `activeShopId`, `activeRole`). |
| `/shops/{shopId}` | `shop_<16hex>` | Tenant configuration (`shopName`, `shopCode`, `currencySymbol`, `taxRatePercent`, `nextTicketSeq`, `nextInvoiceSeq`, `ownerUid`, `memberUids`). |
| `/shopInvites/{inviteCode}` | `INV_<8hex>` | Single-use staff invite codes (`shopId`, `staffName`, `role`, `specialty`, `active`, `usedByUid`). |
| `/publicTracking/{trackingKey}` | `<TICKET>_<LAST4>` | Sanitized non-PII tracking projection (`shopId`, `shopName`, `ticketNumber`, `deviceSummary`, `status`, `paymentStatus`, `totalAmount`, `paidAmount`, `balanceDue`, `timelineSummary`). `allow list: if false` prevents scraping. |

## Shop-Scoped Subcollections (`/shops/{shopId}/...`)
- **`members/{memberUid}`**: Authoritative staff RBAC record (`OWNER`, `TECHNICIAN`, `RECEPTIONIST`).
- **`technicians/{techId}`**: Technician directory, specialty, `assignedCount`, `completedCount`.
- **`customers/{customerId}`**: Customer contact record (`name`, `mobile`, `email`, `address`, `active`, `totalRepairs`). Duplicate mobile checks run before creation.
- **`devices/{deviceId}`**: Customer devices (`customerId`, `brand`, `model`, `imeiOrSerial`, `deviceType`, `color`, `condition`, `accessoriesReceived`, `reportedIssue`, `photoUris`).
- **`repairs/{repairId}`**: Repair tickets (`ticketNumber`, `status`, `priority`, `problemDescription`, `initialDiagnosis`, `estimatedCost`, `estimatedCompletionDate`, `assignedTechUid`, `labourCharge`, `partsTotal`, `discountAmount`, `taxRatePercent`, `totalAmount`, `paidAmount`, `invoiceId`).
  - **`statusHistory/{historyId}`**: Immutable status transition timeline (`fromStatus`, `toStatus`, `note`, `changedByUid`, `changedByName`).
  - **`notes/{noteId}`**: Immutable diagnosis and repair notes (`content`, `isDiagnosis`, `authorUid`, `authorRole`).
  - **`partsUsed/{usageId}`**: Immutable parts consumed (`partId`, `partName`, `sku`, `quantity`, `unitPrice`, `lineTotal`).
- **`parts/{partId}`**: Spare part inventory (`name`, `sku`, `category`, `stockQty`, `purchasePrice`, `sellingPrice`, `minStockLevel`, `supplierName`, `supplierContact`).
- **`inventoryTransactions/{txId}`**: Immutable stock movement ledger (`STOCK_IN`, `STOCK_OUT`, `REPAIR_CONSUME`, `stockBefore`, `stockAfter`, `quantityDelta`).
- **`invoices/{invoiceId}`**: Billing invoice (`invoiceNumber`, `repairId`, `labourCharge`, `partsTotal`, `discountAmount`, `taxRatePercent`, `taxAmount`, `grandTotal`, `paidAmount`, `balanceDue`, `paymentStatus`).
- **`payments/{paymentId}`**: Immutable payment transactions (`invoiceId`, `repairId`, `amount`, `method`, `isAdvance`, `referenceNumber`, `receivedByUid`).
- **`expenses/{expenseId}`**: Operating and procurement expenses (`title`, `category`, `amount`, `note`).
- **`notifications/{notificationId}`**: In-app alerts for ticket intake, status transitions, and low-stock triggers.
- **`auditLogs/{logId}`**: Immutable operational audit trail (`action`, `entityType`, `entityId`, `summary`, `actorUid`, `actorRole`).
