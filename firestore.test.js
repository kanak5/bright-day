const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validJournalEntryPayload(uid = ALICE_UID, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    dateKey: "2026-11-24",
    title: "Morning Tea in the Garden",
    content: "I sat by the sunny window with warm tea and listened to the birds singing.",
    entryType: "AUDIO",
    audioFileName: "entry_1732480000.m4a",
    audioDurationSec: 34,
    primaryEmotion: "PEACEFUL",
    sentimentPolarity: "POSITIVE",
    sentimentScore: 88,
    positiveHighlight: "Enjoying morning tea while listening to the birds brought calm joy.",
    promptUsed: "What is one small thing that made you feel cozy today?",
    keyTopics: ["Tea", "Garden", "Morning"],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validWeeklySummaryPayload(uid = ALICE_UID, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    weekLabel: "Nov 18 - Nov 24",
    headline: "A Week Full of Warm Mornings and Kindness",
    summaryText: "You shared peaceful mornings with warm tea and enjoyed hearing from family this week.",
    highlights: [
      "Listening to birds in the sunny garden on Tuesday",
      "Laughing over old family photos on Sunday",
    ],
    encouragingClosing: "Your days hold so many gentle, bright moments.",
    positiveEntryCount: 5,
    totalEntryCount: 6,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPreferencesPayload(uid = ALICE_UID, ts = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    morningReminderEnabled: true,
    morningHour: 9,
    morningMinute: 30,
    eveningReminderEnabled: true,
    eveningHour: 18,
    eveningMinute: 30,
    reminderMessage: "Time to share a gentle thought in BrightDay.",
    highContrastDark: false,
    textScaleLevel: "LARGE",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user: denied all reads and writes across collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/journal_entries`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").set(validJournalEntryPayload())
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/weekly_summaries`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").get());
});

test("JournalEntry: owner create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").set(validJournalEntryPayload(ALICE_UID, created))
  );

  // Mirror Kotlin observeEntries query
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).orderBy("createdAt", "desc").get()
  );

  // Mirror Kotlin partial update (mutable fields only, preserving createdAt & userId)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").update({
      title: "Sunny Morning Tea",
      primaryEmotion: "JOYFUL",
      sentimentScore: 94,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").delete()
  );
});

test("JournalEntry: rejects cross-user access, ghost fields, @DocumentId id, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").set(validJournalEntryPayload(ALICE_UID, created))
  );

  // Cross-user read/write/delete denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/journal_entries`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").delete());

  // Reject unexpected @DocumentId `id` field or ghost field
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_2").set({
      ...validJournalEntryPayload(ALICE_UID, created),
      id: "entry_2",
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_3").set({
      ...validJournalEntryPayload(ALICE_UID, created),
      isAdmin: true,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_4").set(
      validJournalEntryPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Reject invalid emotion enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_5").set({
      ...validJournalEntryPayload(ALICE_UID, created),
      primaryEmotion: "ANGRY_UNKNOWN",
    })
  );

  // Reject mutating immutable createdAt or userId
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/journal_entries`).doc("entry_1").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("WeeklySummary: owner create, update, list succeed; cross-user and invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/weekly_summaries`).doc("2026-W47").set(validWeeklySummaryPayload(ALICE_UID, created))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/weekly_summaries`).get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/weekly_summaries`).doc("2026-W47").update({
      headline: "Updated Bright Moments",
      positiveEntryCount: 6,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/weekly_summaries`).doc("2026-W47").get());
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/weekly_summaries`).doc("2026-W48").set({
      ...validWeeklySummaryPayload(ALICE_UID, created),
      ghostField: "bad",
    })
  );
});

test("UserPreferences: owner create, get, update succeed; cross-user and invalid scale fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").set(validPreferencesPayload(ALICE_UID, created))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").update({
      highContrastDark: true,
      textScaleLevel: "EXTRA_LARGE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").get());
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("settings").update({
      textScaleLevel: "GIANT_INVALID",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
