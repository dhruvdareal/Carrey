const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_rental_staff";
const BOB_UID = "bob_rental_staff";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCustomerPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Aarav Sharma",
    phone: "9876543210",
    dob: "15/08/1995",
    email: "aarav.sharma@example.com",
    address: "42 MG Road, Indiranagar, Bengaluru, Karnataka",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Unauthenticated user cannot read, list, create, update, or delete customers", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("customers")
      .doc("cust_1")
      .set(validCustomerPayload(ALICE_UID));
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("customers").doc("cust_1").get());
  await assertFails(
    unauthDb.collection("customers").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    unauthDb
      .collection("customers")
      .doc("cust_2")
      .set(validCustomerPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("customers").doc("cust_1").update({ name: "Hacker" })
  );
  await assertFails(unauthDb.collection("customers").doc("cust_1").delete());
});

test("Authenticated owner: valid Kotlin create, partial update, get, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = validCustomerPayload(ALICE_UID);

  // Create customer (mirrors CustomerRepository.addCustomer - no @DocumentId `id` key)
  await assertSucceeds(
    aliceDb.collection("customers").doc("cust_alice_1").set(payload)
  );

  // Get single customer
  await assertSucceeds(
    aliceDb.collection("customers").doc("cust_alice_1").get()
  );

  // Scoped list query (mirrors CustomerRepository.observeCustomers)
  await assertSucceeds(
    aliceDb.collection("customers").where("userId", "==", ALICE_UID).get()
  );

  // Partial update of mutable fields (createdAt and userId preserved)
  await assertSucceeds(
    aliceDb.collection("customers").doc("cust_alice_1").update({
      phone: "9123456789",
      address: "18 Koramangala 4th Block, Bengaluru",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete own customer
  await assertSucceeds(
    aliceDb.collection("customers").doc("cust_alice_1").delete()
  );
});

test("Cross-user isolation: user cannot read, list, update, or delete another user's customers", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("customers")
      .doc("bob_cust_1")
      .set(validCustomerPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cannot get Bob's customer
  await assertFails(aliceDb.collection("customers").doc("bob_cust_1").get());

  // Cannot query Bob's customers
  await assertFails(
    aliceDb.collection("customers").where("userId", "==", BOB_UID).get()
  );

  // Unfiltered list query is rejected (rules are not filters)
  await assertFails(aliceDb.collection("customers").get());

  // Cannot spoof Bob's userId on create
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("spoofed_1")
      .set(validCustomerPayload(BOB_UID))
  );

  // Cannot update or delete Bob's customer
  await assertFails(
    aliceDb.collection("customers").doc("bob_cust_1").update({
      name: "Hijacked Name",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("customers").doc("bob_cust_1").delete());
});

test("Schema & validation bounds reject ghost fields, @DocumentId id, invalid phone/email, immutable changes, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Reject ghost field ("Shadow Update Test")
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_ghost")
      .set(validCustomerPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject @DocumentId `id` written into body
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_with_id")
      .set(validCustomerPayload(ALICE_UID, { id: "cust_with_id" }))
  );

  // Reject non-10-digit phone number
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_bad_phone")
      .set(validCustomerPayload(ALICE_UID, { phone: "12345" }))
  );
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_alpha_phone")
      .set(validCustomerPayload(ALICE_UID, { phone: "98765abcde" }))
  );

  // Reject invalid email
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_bad_email")
      .set(validCustomerPayload(ALICE_UID, { email: "not-an-email" }))
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_future")
      .set(
        validCustomerPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Reject oversized address (> 300 chars)
  await assertFails(
    aliceDb
      .collection("customers")
      .doc("cust_long_addr")
      .set(validCustomerPayload(ALICE_UID, { address: "A".repeat(301) }))
  );

  // Seed valid customer and test immutable field mutations
  await assertSucceeds(
    aliceDb
      .collection("customers")
      .doc("cust_imm")
      .set(validCustomerPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("customers").doc("cust_imm").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("customers").doc("cust_imm").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
