#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const http = require("http");
const { URL } = require("url");
const { createRequire } = require("module");

const DASHBOARD_HTML_PATH = path.join(__dirname, "local_admin_dashboard.html");

function parseArgs(argv) {
  return Object.fromEntries(
    argv.map((entry) => {
      const [rawKey, ...rest] = entry.split("=");
      return [rawKey.replace(/^--/, ""), rest.join("=")];
    })
  );
}

function printHelp() {
  console.log(`PocketCraft Local Firestore Admin Dashboard

Usage:
  node scripts/local_admin_dashboard.cjs --service-account=/absolute/path/service-account.json

Options:
  --service-account=PATH   Firebase service account JSON path
  --port=4310              Local port to bind on 127.0.0.1
  --project-id=ID          Override Firebase project id
  --help                   Show this help

Notes:
  - This only binds to localhost.
  - You can also use GOOGLE_APPLICATION_CREDENTIALS instead of --service-account.
  - Existing app-visible broadcast fields are preserved: broadcasts, polls, and opt-in campaigns.
`);
}

const args = parseArgs(process.argv.slice(2));
if (Object.prototype.hasOwnProperty.call(args, "help")) {
  printHelp();
  process.exit(0);
}

function loadFirebaseAdminModules() {
  const functionsRequire = createRequire(path.join(__dirname, "../functions/package.json"));

  try {
    const appModule = functionsRequire("firebase-admin/app");
    const firestoreModule = functionsRequire("firebase-admin/firestore");
    return {
      ...appModule,
      ...firestoreModule
    };
  } catch (error) {
    console.error("Firebase Admin dependencies are not installed for the `functions` workspace.");
    console.error("Run `cd functions && npm install` once, then start the dashboard again.");
    console.error("");
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}

const {
  initializeApp,
  applicationDefault,
  cert,
  getFirestore,
  FieldValue,
  Timestamp
} = loadFirebaseAdminModules();

const port = Number(args.port || 4310);
if (!Number.isFinite(port) || port <= 0) {
  console.error("Invalid --port value.");
  process.exit(1);
}

const serviceAccountPath = args["service-account"] || process.env.GOOGLE_APPLICATION_CREDENTIALS || "";
const explicitProjectId = args["project-id"] || process.env.FIREBASE_PROJECT_ID || "";
let loadedServiceAccount = null;

function buildCredential() {
  if (serviceAccountPath) {
    const absolutePath = path.resolve(serviceAccountPath);
    const raw = fs.readFileSync(absolutePath, "utf8");
    loadedServiceAccount = JSON.parse(raw);
    return cert(loadedServiceAccount);
  }
  return applicationDefault();
}

let app;
let db;

try {
  const options = {
    credential: buildCredential()
  };
  let detectedConfigProjectId = "";
  try {
    const firebasercPath = path.join(__dirname, "../.firebaserc");
    if (fs.existsSync(firebasercPath)) {
      const rc = JSON.parse(fs.readFileSync(firebasercPath, "utf8"));
      detectedConfigProjectId = rc.projects?.default || rc.projects?.Mailing || "";
    }
  } catch (_) {}

  const resolvedProjectId =
    explicitProjectId ||
    loadedServiceAccount?.project_id ||
    loadedServiceAccount?.projectId ||
    process.env.GCLOUD_PROJECT ||
    detectedConfigProjectId ||
    "";
  if (resolvedProjectId) {
    options.projectId = resolvedProjectId;
  }
  app = initializeApp(options);
  db = getFirestore(app);
} catch (error) {
  console.error("Failed to initialize Firebase Admin.");
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload, null, 2);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(body);
}

function sendHtml(res, html) {
  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(html);
}

function readRequestBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) {
        reject(new Error("Request body too large"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(body));
    req.on("error", reject);
  });
}

function toIso(value) {
  if (!value) return null;
  if (value instanceof Timestamp) return value.toDate().toISOString();
  if (value instanceof Date) return value.toISOString();
  if (typeof value.toDate === "function") return value.toDate().toISOString();
  if (typeof value === "string") return value;
  return null;
}

function ensureString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function ensureBoolean(value, fallback = false) {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (["true", "1", "yes", "on"].includes(normalized)) return true;
    if (["false", "0", "no", "off"].includes(normalized)) return false;
  }
  return fallback;
}

function ensureNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function parseOptions(optionsValue) {
  if (Array.isArray(optionsValue)) {
    return optionsValue.map((entry) => ensureString(entry)).filter(Boolean);
  }
  if (typeof optionsValue === "string") {
    return optionsValue
      .split("\n")
      .map((entry) => entry.trim())
      .filter(Boolean);
  }
  return [];
}

function parseOptionalTimestamp(value, fieldName) {
  if (!value) return null;
  const asString = ensureString(value);
  if (!asString) return null;
  const date = new Date(asString);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Invalid ${fieldName}. Use a valid date/time.`);
  }
  return Timestamp.fromDate(date);
}

function normalizeInteractionType(value) {
  const interactionType = ensureString(value) || "none";
  return ["none", "question", "opt_in"].includes(interactionType) ? interactionType : "none";
}

function normalizeType(value) {
  const type = ensureString(value) || "info";
  return ["info", "warning", "critical"].includes(type) ? type : "info";
}

function normalizePromotionTargetGroup(value) {
  const targetGroup = ensureString(value).toLowerCase();
  return ["free", "pro"].includes(targetGroup) ? targetGroup : "free";
}

function normalizeRemoteCommandType(value) {
  const commandType = ensureString(value);
  return ["clear_cache", "force_relay_refetch", "force_reconnect", "show_message", "trigger_rating_prompt"].includes(commandType)
    ? commandType
    : "show_message";
}

function serializeFirestoreValue(value) {
  if (value == null) return null;
  if (value instanceof Timestamp) return value.toDate().toISOString();
  if (value instanceof Date) return value.toISOString();
  if (typeof value?.toDate === "function") return value.toDate().toISOString();
  if (Array.isArray(value)) return value.map(serializeFirestoreValue);
  if (typeof value === "object") {
    const entries = Object.entries(value).map(([key, entryValue]) => [key, serializeFirestoreValue(entryValue)]);
    return Object.fromEntries(entries);
  }
  return value;
}

function serializeBroadcast(doc, resultsById) {
  const data = doc.data() || {};
  const rawQuestion = data.question && typeof data.question === "object" ? data.question : {};
  const result = resultsById.get(doc.id) || null;
  const optionCounts = result && result.optionCounts && typeof result.optionCounts === "object"
    ? result.optionCounts
    : {};
  const optIns = Array.isArray(result?.optIns) ? result.optIns : [];

  return {
    id: doc.id,
    active: !!data.active,
    title: ensureString(data.title),
    body: ensureString(data.body),
    type: normalizeType(data.type),
    dismissible: ensureBoolean(data.dismissible, true),
    targetMinVersion: ensureNumber(data.targetMinVersion, 0),
    targetMaxVersion: ensureNumber(data.targetMaxVersion, 0),
    interactionType: normalizeInteractionType(data.interactionType),
    question: {
      prompt: ensureString(rawQuestion.prompt),
      options: parseOptions(rawQuestion.options)
    },
    createdAt: toIso(data.createdAt),
    updatedAt: toIso(data.updatedAt),
    startDate: toIso(data.startDate),
    expiryDate: toIso(data.expiryDate),
    showTimer: ensureBoolean(data.showTimer, false),
    timerExpiresAt: toIso(data.timerExpiresAt),
    resultSummary: {
      type: ensureString(result?.type),
      optionCounts,
      optInsCount: optIns.length
    }
  };
}

async function loadResultsMap() {
  const snapshot = await db.collection("broadcast_results").get();
  const map = new Map();
  snapshot.forEach((doc) => map.set(doc.id, doc.data() || {}));
  return map;
}

async function listBroadcasts() {
  let snapshot;
  try {
    snapshot = await db.collection("broadcasts").orderBy("createdAt", "desc").get();
  } catch (error) {
    snapshot = await db.collection("broadcasts").get();
  }
  const resultsById = await loadResultsMap();
  return snapshot.docs
    .map((doc) => serializeBroadcast(doc, resultsById))
    .sort((left, right) => {
      const leftTime = Date.parse(left.createdAt || "") || 0;
      const rightTime = Date.parse(right.createdAt || "") || 0;
      return rightTime - leftTime;
    });
}

async function saveBroadcast(input) {
  const id = ensureString(input.id);
  const title = ensureString(input.title);
  const body = ensureString(input.body);
  const interactionType = normalizeInteractionType(input.interactionType);
  const questionPrompt = ensureString(input.questionPrompt);
  const questionOptions = parseOptions(input.questionOptions);

  if (!title) {
    throw new Error("Title is required.");
  }
  if (interactionType === "question" && questionOptions.length < 2) {
    throw new Error("Polls need at least 2 options.");
  }
  if (interactionType === "opt_in" && questionOptions.length < 1) {
    throw new Error("Opt-in campaigns need at least 1 button label.");
  }

  const ref = id ? db.collection("broadcasts").doc(id) : db.collection("broadcasts").doc();
  const existing = await ref.get();
  const payload = {
    active: ensureBoolean(input.active, true),
    title,
    body,
    type: normalizeType(input.type),
    dismissible: ensureBoolean(input.dismissible, true),
    targetMinVersion: ensureNumber(input.targetMinVersion, 0),
    targetMaxVersion: ensureNumber(input.targetMaxVersion, 0),
    interactionType,
    question: {
      prompt: questionPrompt,
      options: questionOptions
    },
    updatedAt: FieldValue.serverTimestamp()
  };

  const startDate = parseOptionalTimestamp(input.startDate, "startDate");
  const expiryDate = parseOptionalTimestamp(input.expiryDate, "expiryDate");
  const showTimer = ensureBoolean(input.showTimer, false);
  const timerExpiresAt = showTimer ? parseOptionalTimestamp(input.timerExpiresAt, "timerExpiresAt") : null;

  payload.startDate = startDate || FieldValue.delete();
  payload.expiryDate = expiryDate || FieldValue.delete();
  payload.showTimer = showTimer;
  payload.timerExpiresAt = timerExpiresAt || FieldValue.delete();
  payload.createdAt = existing.exists && existing.get("createdAt")
    ? existing.get("createdAt")
    : FieldValue.serverTimestamp();

  if (interactionType === "none") {
    payload.question = {
      prompt: "",
      options: []
    };
  }

  await ref.set(payload, { merge: true });

  const resultsById = await loadResultsMap();
  return serializeBroadcast(await ref.get(), resultsById);
}

async function deleteBroadcast(id) {
  const ref = db.collection("broadcasts").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Broadcast not found.");
  }
  await ref.delete();
}

async function duplicateBroadcast(id) {
  const ref = db.collection("broadcasts").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Broadcast not found.");
  }

  const data = snapshot.data() || {};
  const copyRef = db.collection("broadcasts").doc();
  const clonedTitle = ensureString(data.title) ? `${ensureString(data.title)} Copy` : "Untitled Copy";
  const payload = {
    ...data,
    title: clonedTitle,
    active: false,
    startDate: null,
    expiryDate: null,
    showTimer: false,
    timerExpiresAt: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  };

  await copyRef.set(payload);
  const resultsById = await loadResultsMap();
  return serializeBroadcast(await copyRef.get(), resultsById);
}

function serializePromotion(doc) {
  const data = doc.data() || {};
  return {
    id: doc.id,
    active: !!data.active,
    title: ensureString(data.title),
    body: ensureString(data.body),
    ctaText: ensureString(data.ctaText),
    iconEmoji: ensureString(data.iconEmoji),
    targetGroup: normalizePromotionTargetGroup(data.targetGroup),
    createdAt: toIso(data.createdAt),
    updatedAt: toIso(data.updatedAt)
  };
}

async function listPromotions() {
  let snapshot;
  try {
    snapshot = await db.collection("promotions").orderBy("createdAt", "desc").get();
  } catch (error) {
    snapshot = await db.collection("promotions").get();
  }
  return snapshot.docs
    .map(serializePromotion)
    .sort((left, right) => (Date.parse(right.createdAt || "") || 0) - (Date.parse(left.createdAt || "") || 0));
}

async function savePromotion(input) {
  const id = ensureString(input.id);
  const title = ensureString(input.title);
  if (!title) {
    throw new Error("Promotion title is required.");
  }

  const ref = id ? db.collection("promotions").doc(id) : db.collection("promotions").doc();
  const existing = await ref.get();
  const payload = {
    active: ensureBoolean(input.active, true),
    title,
    body: ensureString(input.body),
    ctaText: ensureString(input.ctaText) || "Upgrade Now",
    iconEmoji: ensureString(input.iconEmoji) || "🚀",
    targetGroup: normalizePromotionTargetGroup(input.targetGroup),
    updatedAt: FieldValue.serverTimestamp(),
    createdAt: existing.exists && existing.get("createdAt")
      ? existing.get("createdAt")
      : FieldValue.serverTimestamp()
  };

  await ref.set(payload, { merge: true });
  return serializePromotion(await ref.get());
}

async function deletePromotion(id) {
  const ref = db.collection("promotions").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Promotion not found.");
  }
  await ref.delete();
}

async function duplicatePromotion(id) {
  const ref = db.collection("promotions").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Promotion not found.");
  }
  const data = snapshot.data() || {};
  const copyRef = db.collection("promotions").doc();
  await copyRef.set({
    ...data,
    title: ensureString(data.title) ? `${ensureString(data.title)} Copy` : "Untitled Promotion Copy",
    active: false,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  });
  return serializePromotion(await copyRef.get());
}

function serializeRemoteCommand(doc) {
  const data = doc.data() || {};
  const payload = data.payload && typeof data.payload === "object" ? data.payload : {};
  return {
    id: doc.id,
    target: ensureString(data.target) || "all",
    type: normalizeRemoteCommandType(data.type),
    createdAt: toIso(data.createdAt),
    expiresAt: toIso(data.expiresAt),
    payload: {
      title: ensureString(payload.title),
      message: ensureString(payload.message)
    }
  };
}

async function listRemoteCommands() {
  let snapshot;
  try {
    snapshot = await db.collection("remote_commands").orderBy("createdAt", "desc").limit(50).get();
  } catch (error) {
    snapshot = await db.collection("remote_commands").get();
  }
  return snapshot.docs
    .map(serializeRemoteCommand)
    .sort((left, right) => (Date.parse(right.createdAt || "") || 0) - (Date.parse(left.createdAt || "") || 0));
}

async function saveRemoteCommand(input) {
  const id = ensureString(input.id);
  const target = ensureString(input.target) || "all";
  const type = normalizeRemoteCommandType(input.type);
  const expiresAt = parseOptionalTimestamp(input.expiresAt, "expiresAt");
  const ref = id ? db.collection("remote_commands").doc(id) : db.collection("remote_commands").doc();
  const existing = await ref.get();

  const payload = {
    target,
    type,
    expiresAt,
    updatedAt: FieldValue.serverTimestamp(),
    createdAt: existing.exists && existing.get("createdAt")
      ? existing.get("createdAt")
      : FieldValue.serverTimestamp()
  };

  if (type === "show_message") {
    const message = ensureString(input.message);
    if (!message) {
      throw new Error("Show-message remote commands need a message.");
    }
    payload.payload = {
      title: ensureString(input.title) || "Notification",
      message
    };
  } else {
    payload.payload = {};
  }

  await ref.set(payload, { merge: true });
  return serializeRemoteCommand(await ref.get());
}

async function deleteRemoteCommand(id) {
  const ref = db.collection("remote_commands").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Remote command not found.");
  }
  await ref.delete();
}

async function duplicateRemoteCommand(id) {
  const ref = db.collection("remote_commands").doc(id);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new Error("Remote command not found.");
  }
  const data = snapshot.data() || {};
  const copyRef = db.collection("remote_commands").doc();
  const payload = {
    ...data,
    expiresAt: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  };
  if (data.type === "show_message" && data.payload && typeof data.payload === "object") {
    payload.payload = {
      ...data.payload,
      title: ensureString(data.payload.title) ? `${ensureString(data.payload.title)} Copy` : "Notification Copy"
    };
  }
  await copyRef.set(payload);
  return serializeRemoteCommand(await copyRef.get());
}

function serializeUpdateConfig(doc) {
  const data = doc.data() || {};
  return {
    id: doc.id,
    showUpdatePopup: ensureBoolean(data.showUpdatePopup, false),
    playStoreUrl: ensureString(data.playStoreUrl),
    versionCode: data.versionCode == null ? "" : ensureNumber(data.versionCode, 0),
    latestVersion: ensureString(data.latestVersion),
    latestVersionCode: data.latestVersionCode == null ? "" : ensureNumber(data.latestVersionCode, 0),
    excludeVersionCode: data.excludeVersionCode == null ? "" : ensureNumber(data.excludeVersionCode, 0),
    isForced: ensureBoolean(data.isForced, false),
    enablePlayStoreRatingPrompt: ensureBoolean(data.enablePlayStoreRatingPrompt, true)
  };
}

async function loadUpdateConfig() {
  const ref = db.collection("app_config").doc("update");
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    return {
      id: "update",
      showUpdatePopup: false,
      playStoreUrl: "",
      versionCode: "",
      latestVersion: "",
      latestVersionCode: "",
      excludeVersionCode: "",
      isForced: false,
      enablePlayStoreRatingPrompt: true
    };
  }
  return serializeUpdateConfig(snapshot);
}

async function saveUpdateConfig(input) {
  const ref = db.collection("app_config").doc("update");
  const payload = {
    showUpdatePopup: ensureBoolean(input.showUpdatePopup, false),
    playStoreUrl: ensureString(input.playStoreUrl),
    isForced: ensureBoolean(input.isForced, false),
    enablePlayStoreRatingPrompt: ensureBoolean(input.enablePlayStoreRatingPrompt, true)
  };

  const versionCode = ensureString(input.versionCode);
  const latestVersion = ensureString(input.latestVersion);
  const latestVersionCode = ensureString(input.latestVersionCode);
  const excludeVersionCode = ensureString(input.excludeVersionCode);

  payload.versionCode = versionCode ? ensureNumber(versionCode, 0) : FieldValue.delete();
  payload.latestVersion = latestVersion || FieldValue.delete();
  payload.latestVersionCode = latestVersionCode ? ensureNumber(latestVersionCode, 0) : FieldValue.delete();
  payload.excludeVersionCode = excludeVersionCode ? ensureNumber(excludeVersionCode, 0) : FieldValue.delete();

  await ref.set(payload, { merge: true });
  return loadUpdateConfig();
}

async function listBroadcastResults() {
  const [resultsSnapshot, responsesSnapshot] = await Promise.all([
    db.collection("broadcast_results").get(),
    db.collection("broadcast_responses").get()
  ]);

  const responsesByBroadcast = new Map();
  responsesSnapshot.forEach((doc) => {
    const data = doc.data() || {};
    const broadcastId = ensureString(data.broadcastId);
    if (!broadcastId) return;
    const current = responsesByBroadcast.get(broadcastId) || [];
    current.push({
      id: doc.id,
      uid: ensureString(data.uid),
      selectedOption: ensureString(data.selectedOption),
      interactionType: ensureString(data.interactionType),
      respondedAt: toIso(data.respondedAt)
    });
    responsesByBroadcast.set(broadcastId, current);
  });

  return resultsSnapshot.docs
    .map((doc) => {
      const data = doc.data() || {};
      return {
        id: doc.id,
        type: ensureString(data.type),
        optionCounts: serializeFirestoreValue(data.optionCounts || {}),
        optIns: Array.isArray(data.optIns) ? data.optIns.map(serializeFirestoreValue) : [],
        responses: responsesByBroadcast.get(doc.id) || []
      };
    })
    .sort((left, right) => right.id.localeCompare(left.id));
}

async function listBetaFeedbacks() {
  // Order by createdAt: that is the only timestamp the app itself writes, so every report has
  // one. The previous ordering key, emailCheckedAt, is written by the mail function and only on
  // its "skipped" and "failed" branches — a successfully emailed report gets emailSentAt instead,
  // and a report the function has not reached yet has neither. Firestore drops documents that
  // lack the ordering field, so ordering by emailCheckedAt silently hid every delivered report
  // and every brand new one: exactly the submissions worth reading.
  // Documents written by different app versions carry different timestamps, and no single
  // orderBy can see all of them, so each ordering is queried and the results merged. Bounded at
  // two queries of 50, which is plenty for a feedback inbox.
  const orderCandidates = ["createdAt", "emailSentAt", "emailCheckedAt"];
  const collected = new Map();
  for (const field of orderCandidates) {
    try {
      const attempt = await db.collection("beta_feedback").orderBy(field, "desc").limit(50).get();
      attempt.docs.forEach((doc) => collected.set(doc.id, doc));
    } catch (error) {
      // Missing composite index or a field never written — fall through to the next ordering.
    }
  }
  if (collected.size === 0) {
    try {
      const attempt = await db.collection("beta_feedback").limit(50).get();
      attempt.docs.forEach((doc) => collected.set(doc.id, doc));
    } catch (e) {
      // Leave the list empty; the UI reports "no submissions found".
    }
  }

  const sortKey = (doc) => {
    const data = doc.data() || {};
    const iso = toIso(data.createdAt) || toIso(data.emailSentAt) || toIso(data.emailCheckedAt) || toIso(data.updatedAt);
    return iso ? Date.parse(iso) : 0;
  };
  const snapshot = {
    docs: Array.from(collected.values()).sort((a, b) => sortKey(b) - sortKey(a)).slice(0, 50)
  };

  return snapshot.docs.map((doc) => {
    const data = doc.data() || {};
    return {
      id: doc.id,
      message: ensureString(data.message),
      userId: ensureString(data.userId),
      appVersion: ensureString(data.appVersion),
      appVersionCode: data.appVersionCode == null ? "" : ensureNumber(data.appVersionCode),
      serverVersion: ensureString(data.serverVersion),
      deviceManufacturer: ensureString(data.deviceManufacturer),
      deviceModel: ensureString(data.deviceModel),
      androidSdk: data.androidSdk == null ? "" : ensureNumber(data.androidSdk),
      source: ensureString(data.source),
      androidRelease: ensureString(data.androidRelease),
      // What the app actually attaches. The three log fields below were never written by any
      // shipped version of the app, so the dashboard's log panels and crash diagnosis had
      // nothing to read and always reported a clean run.
      appLogExcerpt: ensureString(data.appLogExcerpt),
      logFileName: ensureString(data.logFileName),
      logFilePath: ensureString(data.logFilePath),
      currentConsoleLog: ensureString(data.currentConsoleLog),
      serverLatestLog: ensureString(data.serverLatestLog),
      crashArtifacts: ensureString(data.crashArtifacts),
      runtimeState: ensureString(data.runtimeState),
      emailStatus: ensureString(data.emailStatus),
      emailError: ensureString(data.emailError),
      createdAt: toIso(data.createdAt),
      emailSentAt: toIso(data.emailSentAt || data.emailCheckedAt || data.updatedAt)
    };
  });
}

async function listRootCollections() {
  let collections = [];
  try {
    const list = await db.listCollections();
    collections = list.map((collection) => collection.id);
  } catch (error) {
    // If permissions are restricted or emulator listCollections is unsupported, fall back.
  }
  const known = [
    "app_config",
    "beta_feedback",
    "broadcast_responses",
    "broadcast_results",
    "broadcasts",
    "promotions",
    "remote_commands",
    "social_prompts",
    "users"
  ];
  const set = new Set([...collections, ...known]);
  return Array.from(set).sort((left, right) => left.localeCompare(right));
}

async function loadCollectionDocuments(collectionName, limitCount = 100) {
  const safeCollectionName = ensureString(collectionName);
  if (!safeCollectionName) {
    throw new Error("Collection name is required.");
  }

  let snapshot;
  try {
    snapshot = await db.collection(safeCollectionName).limit(limitCount).get();
  } catch (error) {
    throw new Error(`Could not load collection "${safeCollectionName}": ${error instanceof Error ? error.message : String(error)}`);
  }

  const documents = snapshot.docs.map((doc) => ({
    id: doc.id,
    ...serializeFirestoreValue(doc.data() || {})
  }));

  return {
    collection: safeCollectionName,
    count: documents.length,
    documents
  };
}

const dashboardHtml = fs.readFileSync(DASHBOARD_HTML_PATH, "utf8");

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "127.0.0.1"}`);
  const pathname = url.pathname;

  try {
    if (req.method === "GET" && pathname === "/") {
      sendHtml(res, dashboardHtml);
      return;
    }

    if (req.method === "GET" && pathname === "/api/meta") {
      sendJson(res, 200, {
        ok: true,
        projectId: app.options.projectId || process.env.GCLOUD_PROJECT || null,
        port,
        host: "127.0.0.1"
      });
      return;
    }

    if (req.method === "GET" && pathname === "/api/broadcasts") {
      const broadcasts = await listBroadcasts();
      sendJson(res, 200, { ok: true, broadcasts });
      return;
    }

    if (req.method === "POST" && pathname === "/api/broadcasts") {
      const rawBody = await readRequestBody(req);
      const input = rawBody ? JSON.parse(rawBody) : {};
      const broadcast = await saveBroadcast(input);
      sendJson(res, 200, { ok: true, broadcast });
      return;
    }

    if (req.method === "POST" && pathname.startsWith("/api/broadcasts/") && pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/broadcasts\//, "").replace(/\/duplicate$/, ""));
      const broadcast = await duplicateBroadcast(id);
      sendJson(res, 200, { ok: true, broadcast });
      return;
    }

    if (req.method === "DELETE" && pathname.startsWith("/api/broadcasts/") && !pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/broadcasts\//, ""));
      await deleteBroadcast(id);
      sendJson(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && pathname === "/api/promotions") {
      const promotions = await listPromotions();
      sendJson(res, 200, { ok: true, promotions });
      return;
    }

    if (req.method === "POST" && pathname === "/api/promotions") {
      const rawBody = await readRequestBody(req);
      const input = rawBody ? JSON.parse(rawBody) : {};
      const promotion = await savePromotion(input);
      sendJson(res, 200, { ok: true, promotion });
      return;
    }

    if (req.method === "POST" && pathname.startsWith("/api/promotions/") && pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/promotions\//, "").replace(/\/duplicate$/, ""));
      const promotion = await duplicatePromotion(id);
      sendJson(res, 200, { ok: true, promotion });
      return;
    }

    if (req.method === "DELETE" && pathname.startsWith("/api/promotions/") && !pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/promotions\//, ""));
      await deletePromotion(id);
      sendJson(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && pathname === "/api/remote-commands") {
      const remoteCommands = await listRemoteCommands();
      sendJson(res, 200, { ok: true, remoteCommands });
      return;
    }

    if (req.method === "POST" && pathname === "/api/remote-commands") {
      const rawBody = await readRequestBody(req);
      const input = rawBody ? JSON.parse(rawBody) : {};
      const remoteCommand = await saveRemoteCommand(input);
      sendJson(res, 200, { ok: true, remoteCommand });
      return;
    }

    if (req.method === "POST" && pathname.startsWith("/api/remote-commands/") && pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/remote-commands\//, "").replace(/\/duplicate$/, ""));
      const remoteCommand = await duplicateRemoteCommand(id);
      sendJson(res, 200, { ok: true, remoteCommand });
      return;
    }

    if (req.method === "DELETE" && pathname.startsWith("/api/remote-commands/") && !pathname.endsWith("/duplicate")) {
      const id = decodeURIComponent(pathname.replace(/^\/api\/remote-commands\//, ""));
      await deleteRemoteCommand(id);
      sendJson(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && pathname === "/api/update-config") {
      const updateConfig = await loadUpdateConfig();
      sendJson(res, 200, { ok: true, updateConfig });
      return;
    }

    if (req.method === "POST" && pathname === "/api/update-config") {
      const rawBody = await readRequestBody(req);
      const input = rawBody ? JSON.parse(rawBody) : {};
      const updateConfig = await saveUpdateConfig(input);
      sendJson(res, 200, { ok: true, updateConfig });
      return;
    }

    if (req.method === "GET" && pathname === "/api/broadcast-results") {
      const results = await listBroadcastResults();
      sendJson(res, 200, { ok: true, results });
      return;
    }

    if (req.method === "GET" && pathname === "/api/firestore/collections") {
      const collections = await listRootCollections();
      sendJson(res, 200, { ok: true, collections });
      return;
    }

    if (req.method === "GET" && pathname === "/api/beta-feedbacks") {
      const feedbacks = await listBetaFeedbacks();
      sendJson(res, 200, { ok: true, feedbacks });
      return;
    }

    if (req.method === "GET" && pathname === "/api/firestore/collection") {
      const collection = url.searchParams.get("name") || "";
      const limitCount = ensureNumber(url.searchParams.get("limit"), 100);
      const data = await loadCollectionDocuments(collection, Math.max(1, Math.min(limitCount, 250)));
      sendJson(res, 200, { ok: true, ...data });
      return;
    }

    sendJson(res, 404, { ok: false, error: "Not found" });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    sendJson(res, 500, { ok: false, error: message });
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`PocketCraft local admin dashboard running at http://127.0.0.1:${port}`);
  console.log("This server is local-only and is not exposed online.");
  if (serviceAccountPath) {
    console.log(`Using service account: ${path.resolve(serviceAccountPath)}`);
    const detectedProjectId = loadedServiceAccount?.project_id || loadedServiceAccount?.projectId;
    if (detectedProjectId) {
      console.log(`Detected Firebase project: ${detectedProjectId}`);
    }
  } else {
    console.log("Using application default credentials.");
    if (!explicitProjectId && !process.env.GCLOUD_PROJECT) {
      console.log("If startup fails, pass --project-id=YOUR_FIREBASE_PROJECT_ID or use a service account JSON.");
    }
  }
});
