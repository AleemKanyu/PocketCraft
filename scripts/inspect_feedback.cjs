#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { createRequire } = require("module");

function parseArgs(argv) {
  return Object.fromEntries(
    argv.map((entry) => {
      const [rawKey, ...rest] = entry.split("=");
      return [rawKey.replace(/^--/, ""), rest.join("=")];
    })
  );
}

const args = parseArgs(process.argv.slice(2));

if (args.help || !args.id) {
  console.log(`PocketCraft Beta Feedback Inspector CLI

Usage:
  node scripts/inspect_feedback.cjs --id=FEEDBACK_ID [options]

Options:
  --id=ID                  The ID of the feedback document in Firestore
  --service-account=PATH   Firebase service account JSON path
  --project-id=ID          Override Firebase project id
`);
  process.exit(0);
}

const functionsRequire = createRequire(path.join(__dirname, "../functions/package.json"));
const admin = functionsRequire("firebase-admin");

const serviceAccountPath = args["service-account"] || process.env.GOOGLE_APPLICATION_CREDENTIALS || "";
const explicitProjectId = args["project-id"] || process.env.FIREBASE_PROJECT_ID || "";
let loadedServiceAccount = null;

function buildCredential() {
  if (serviceAccountPath) {
    const absolutePath = path.resolve(serviceAccountPath);
    const raw = fs.readFileSync(absolutePath, "utf8");
    loadedServiceAccount = JSON.parse(raw);
    return admin.credential.cert(loadedServiceAccount);
  }
  return admin.credential.applicationDefault();
}

try {
  const options = {
    credential: buildCredential()
  };
  const resolvedProjectId =
    explicitProjectId ||
    loadedServiceAccount?.project_id ||
    loadedServiceAccount?.projectId ||
    process.env.GCLOUD_PROJECT ||
    "";
  if (resolvedProjectId) {
    options.projectId = resolvedProjectId;
  }
  admin.initializeApp(options);
} catch (error) {
  console.error("Failed to initialize Firebase Admin:", error.message);
  process.exit(1);
}

const db = admin.firestore();

async function inspect() {
  const feedbackId = args.id.trim();
  console.log(`Fetching feedback document "${feedbackId}" from Firestore...`);
  
  const ref = db.collection("beta_feedback").doc(feedbackId);
  const doc = await ref.get();
  
  if (!doc.exists) {
    console.error(`Error: Feedback document "${feedbackId}" does not exist.`);
    process.exit(1);
  }
  
  const data = doc.data() || {};
  const message = data.message || "(No message provided)";
  
  console.log("\n==================================================");
  console.log("             BETA FEEDBACK DETAILS                ");
  console.log("==================================================");
  console.log(`ID:              ${doc.id}`);
  console.log(`User ID:         ${data.userId || "unknown"}`);
  console.log(`App Version:     ${data.appVersion || "unknown"} (${data.appVersionCode ?? "unknown"})`);
  console.log(`Server Version:  ${data.serverVersion || "unknown"}`);
  console.log(`Device:          ${data.deviceManufacturer || "unknown"} ${data.deviceModel || "unknown"}`);
  console.log(`Android SDK:     ${data.androidSdk ?? "unknown"}`);
  console.log(`Submitted:       ${data.createdAt?.toDate?.().toISOString() || "unknown"}`);
  console.log(`Source:          ${data.source || "unknown"}`);
  console.log(`Email Status:    ${data.emailStatus || "pending"}`);
  if (data.emailError) {
    console.log(`Email Error:     ${data.emailError}`);
  }
  console.log("--------------------------------------------------");
  console.log("User Message:");
  console.log(`"${message}"`);
  console.log("==================================================");
  
  // appLogExcerpt is what FeedbackService actually attaches. The other three are read for
  // documents written by older tooling; on their own they made every report look crash-free.
  const appLog = data.appLogExcerpt || "";
  const consoleLog = data.currentConsoleLog || "";
  const serverLog = data.serverLatestLog || "";
  const crashArt = data.crashArtifacts || "";
  
  console.log("             CRASH LOG DIAGNOSIS                 ");
  console.log("==================================================");
  
  const analyzeLogs = (...logSources) => {
    const errors = [];
    const logs = logSources.filter(Boolean).join("\n");
    const lines = logs.split("\n");
    
    if (logs.includes("java.lang.OutOfMemoryError")) {
      errors.push("🚨 OutOfMemoryError detected: The server ran out of allocated RAM.");
    }
    if (logs.includes("BindException") || logs.includes("Address already in use")) {
      errors.push("🚨 BindException (Address already in use) detected: Server or RCON ports are occupied.");
    }
    if (logs.includes("NullPointerException")) {
      errors.push("🚨 NullPointerException detected in logs.");
    }
    if (logs.includes("PluginLoadException") || logs.includes("PluginLoader")) {
      errors.push("⚠️ Plugin Loading Error detected.");
    }
    if (logs.includes("UnsupportedClassVersionError") || logs.includes("has been compiled by a more recent version")) {
      errors.push("🚨 Java Class Version Mismatch detected (compiled under newer JRE).");
    }
    
    // Find stack trace snippets
    const exceptionLines = [];
    lines.forEach((line, idx) => {
      if (line.includes("Exception") || line.includes("Error") || line.includes("Caused by:")) {
        // Grab context
        const start = Math.max(0, idx - 3);
        const end = Math.min(lines.length - 1, idx + 10);
        const snippet = lines.slice(start, end).join("\n");
        exceptionLines.push({ lineNum: idx + 1, matched: line.trim(), snippet });
      }
    });
    
    return { errors, exceptionLines };
  };
  
  const diagnosis = analyzeLogs(appLog, consoleLog, serverLog, crashArt);
  
  if (diagnosis.errors.length === 0 && diagnosis.exceptionLines.length === 0) {
    console.log("🟢 No critical system crashes or exceptions detected in the logs.");
  } else {
    diagnosis.errors.forEach((err) => console.log(err));
    console.log("\nFound Stack Traces / Exception Context:");
    // Print first 3 unique snippets
    const seen = new Set();
    let count = 0;
    for (const entry of diagnosis.exceptionLines) {
      if (count >= 3) break;
      if (!seen.has(entry.matched)) {
        seen.add(entry.matched);
        console.log(`\n--- Exception on line ${entry.lineNum}: ${entry.matched} ---`);
        console.log(entry.snippet);
        count++;
      }
    }
  }
  
  console.log("==================================================\n");
}

inspect().catch((err) => {
  console.error("Failed to run inspection CLI:", err);
});
