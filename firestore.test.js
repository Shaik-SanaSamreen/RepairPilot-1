const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_A_UID = "owner_a_uid";
const TECH_A_UID = "tech_a_uid";
const RECEPT_A_UID = "recept_a_uid";
const OWNER_B_UID = "owner_b_uid";
const SHOP_A_ID = "shop_a_001";
const SHOP_B_ID = "shop_b_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedShopAWithStaff() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const members = [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID];
    await db.collection("shops").doc(SHOP_A_ID).set({
      ownerUid: OWNER_A_UID,
      shopName: "Apex Mobile Fix",
      shopCode: "APEX",
      phone: "555-0100",
      address: "100 Main St",
      currencySymbol: "$",
      taxRatePercent: 8.5,
      nextTicketSeq: 1001,
      nextInvoiceSeq: 5001,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await db.collection("shops").doc(SHOP_A_ID).collection("members").doc(OWNER_A_UID).set({
      uid: OWNER_A_UID,
      shopId: SHOP_A_ID,
      displayName: "Alice Owner",
      email: "alice@apex.com",
      role: "OWNER",
      active: true,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await db.collection("shops").doc(SHOP_A_ID).collection("members").doc(TECH_A_UID).set({
      uid: TECH_A_UID,
      shopId: SHOP_A_ID,
      displayName: "Bob Tech",
      email: "bob@apex.com",
      role: "TECHNICIAN",
      specialty: "Micro-soldering",
      active: true,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await db.collection("shops").doc(SHOP_A_ID).collection("members").doc(RECEPT_A_UID).set({
      uid: RECEPT_A_UID,
      shopId: SHOP_A_ID,
      displayName: "Carol FrontDesk",
      email: "carol@apex.com",
      role: "RECEPTIONIST",
      active: true,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });
}

test("Unauthenticated user: denied across all collections", async () => {
  await seedShopAWithStaff();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("shops").doc(SHOP_A_ID).get());
  await assertFails(unauthDb.collection("shops").doc(SHOP_A_ID).collection("customers").get());
  await assertFails(unauthDb.collection("shops").doc(SHOP_A_ID).collection("repairs").get());
  await assertFails(unauthDb.collection("publicTracking").doc("APEX1001_1234").get());
});

test("Shop Owner: can create shop and bootstrap OWNER member doc", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_A_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_A_ID).set({
      ownerUid: OWNER_A_UID,
      shopName: "Apex Mobile Fix",
      shopCode: "APEX",
      currencySymbol: "$",
      taxRatePercent: 8.5,
      nextTicketSeq: 1001,
      nextInvoiceSeq: 5001,
      memberUids: [OWNER_A_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_A_ID).collection("members").doc(OWNER_A_UID).set({
      uid: OWNER_A_UID,
      shopId: SHOP_A_ID,
      displayName: "Alice Owner",
      role: "OWNER",
      active: true,
      memberUids: [OWNER_A_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Cross-Shop Tenant Isolation: Shop B owner cannot read or list Shop A data", async () => {
  await seedShopAWithStaff();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(SHOP_A_ID).collection("customers").doc("cust_1").set({
      name: "John Doe",
      mobile: "9876543210",
      active: true,
      totalRepairs: 1,
      memberUids: [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const ownerBDb = testEnv.authenticatedContext(OWNER_B_UID).firestore();
  await assertFails(ownerBDb.collection("shops").doc(SHOP_A_ID).get());
  await assertFails(ownerBDb.collection("shops").doc(SHOP_A_ID).collection("customers").doc("cust_1").get());
  await assertFails(ownerBDb.collection("shops").doc(SHOP_A_ID).collection("customers").get());
  // Querying with Shop B's own UID returns 0 docs (never leaks Shop A's cust_1)
  const ownUidSnap = await assertSucceeds(
    ownerBDb.collection("shops").doc(SHOP_A_ID).collection("customers")
      .where("memberUids", "array-contains", OWNER_B_UID)
      .get()
  );
  if (ownUidSnap.size !== 0) {
    throw new Error(`Expected 0 docs for Shop B, got ${ownUidSnap.size}`);
  }
  // Querying with Shop A's UID is strictly rejected
  await assertFails(
    ownerBDb.collection("shops").doc(SHOP_A_ID).collection("customers")
      .where("memberUids", "array-contains", OWNER_A_UID)
      .get()
  );
});

test("Client Query Alignment: Shop A staff can list customers with memberUids filter, fails without filter", async () => {
  await seedShopAWithStaff();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("shops").doc(SHOP_A_ID).collection("customers").doc("cust_1").set({
      name: "Jane Smith",
      mobile: "5551234567",
      active: true,
      totalRepairs: 0,
      memberUids: [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const techDb = testEnv.authenticatedContext(TECH_A_UID).firestore();
  await assertSucceeds(
    techDb.collection("shops").doc(SHOP_A_ID).collection("customers")
      .where("memberUids", "array-contains", TECH_A_UID)
      .get()
  );
  // Unfiltered list fails
  await assertFails(techDb.collection("shops").doc(SHOP_A_ID).collection("customers").get());
});

test("RBAC Enforcement: Technician cannot create customers or expenses, but can update repair status", async () => {
  await seedShopAWithStaff();
  const members = [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID];
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("shops").doc(SHOP_A_ID).collection("repairs").doc("rep_1").set({
      ticketNumber: "RP-1001",
      customerId: "cust_1",
      customerName: "Jane",
      customerMobile: "5551234567",
      deviceId: "dev_1",
      deviceSummary: "iPhone 15 Pro",
      problemDescription: "Cracked OLED",
      estimatedCost: 220,
      priority: "HIGH",
      status: "RECEIVED",
      labourCharge: 60,
      partsTotal: 0,
      discountAmount: 0,
      taxRatePercent: 8.5,
      totalAmount: 65.1,
      paidAmount: 0,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const techDb = testEnv.authenticatedContext(TECH_A_UID).firestore();
  // Technician CANNOT create customer
  await assertFails(
    techDb.collection("shops").doc(SHOP_A_ID).collection("customers").doc("cust_2").set({
      name: "Bad Actor",
      mobile: "123456",
      active: true,
      totalRepairs: 0,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  // Technician CANNOT create expense
  await assertFails(
    techDb.collection("shops").doc(SHOP_A_ID).collection("expenses").doc("exp_1").set({
      title: "Rent",
      category: "Utilities",
      amount: 500,
      recordedByUid: TECH_A_UID,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  // Technician CAN update repair status & diagnosis
  await assertSucceeds(
    techDb.collection("shops").doc(SHOP_A_ID).collection("repairs").doc("rep_1").update({
      status: "DIAGNOSING",
      initialDiagnosis: "Display flex torn, needs OLED replacement",
      updatedAt: nowTs(),
    })
  );
});

test("Terminal State Locking: Delivered repair cannot be altered by Technician or Receptionist", async () => {
  await seedShopAWithStaff();
  const members = [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID];
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("shops").doc(SHOP_A_ID).collection("repairs").doc("rep_done").set({
      ticketNumber: "RP-1002",
      customerId: "cust_1",
      customerName: "Jane",
      customerMobile: "5551234567",
      deviceId: "dev_1",
      deviceSummary: "Pixel 9",
      problemDescription: "Battery drain",
      estimatedCost: 90,
      priority: "NORMAL",
      status: "DELIVERED",
      labourCharge: 40,
      partsTotal: 45,
      discountAmount: 0,
      taxRatePercent: 8.5,
      totalAmount: 92.23,
      paidAmount: 92.23,
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const techDb = testEnv.authenticatedContext(TECH_A_UID).firestore();
  await assertFails(
    techDb.collection("shops").doc(SHOP_A_ID).collection("repairs").doc("rep_done").update({
      status: "IN_PROGRESS",
      updatedAt: nowTs(),
    })
  );
});

test("Shadow Update & Append-Only Tests: Reject ghost fields and immutable ledger edits", async () => {
  await seedShopAWithStaff();
  const members = [OWNER_A_UID, TECH_A_UID, RECEPT_A_UID];
  const ownerDb = testEnv.authenticatedContext(OWNER_A_UID).firestore();

  // Ghost field rejected on customer create
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_A_ID).collection("customers").doc("cust_ghost").set({
      name: "Ghost User",
      mobile: "5550001111",
      active: true,
      totalRepairs: 0,
      isAdmin: true, // Ghost field
      memberUids: members,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Append-only audit log: create succeeds, update/delete fail
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_A_ID).collection("auditLogs").doc("log_1").set({
      action: "CREATE_SHOP",
      entityType: "SHOP",
      entityId: SHOP_A_ID,
      summary: "Created shop",
      actorUid: OWNER_A_UID,
      actorName: "Alice Owner",
      actorRole: "OWNER",
      memberUids: members,
      createdAt: nowTs(),
    })
  );
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_A_ID).collection("auditLogs").doc("log_1").update({
      summary: "Tampered summary",
    })
  );
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_A_ID).collection("auditLogs").doc("log_1").delete()
  );
});

test("Public Tracking: list is blocked (no scraping), exact get succeeds for signed-in user", async () => {
  await seedShopAWithStaff();
  const ownerDb = testEnv.authenticatedContext(OWNER_A_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("publicTracking").doc("RP-1001_4567").set({
      shopId: SHOP_A_ID,
      shopName: "Apex Mobile Fix",
      ticketNumber: "RP-1001",
      deviceSummary: "iPhone 15 Pro",
      status: "IN_PROGRESS",
      estimatedCompletionDate: "2026-11-14",
      paymentStatus: "PARTIAL",
      totalAmount: 220,
      paidAmount: 50,
      balanceDue: 170,
      timelineSummary: ["RECEIVED • Nov 12", "IN_PROGRESS • Nov 12"],
      updatedAt: nowTs(),
    })
  );

  const customerDb = testEnv.authenticatedContext("random_customer_uid").firestore();
  await assertFails(customerDb.collection("publicTracking").get()); // list blocked
  await assertSucceeds(customerDb.collection("publicTracking").doc("RP-1001_4567").get()); // exact get allowed
});
