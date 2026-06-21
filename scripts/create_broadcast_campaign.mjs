const args = Object.fromEntries(
  process.argv.slice(2).map((entry) => {
    const [rawKey, ...rest] = entry.split("=");
    return [rawKey.replace(/^--/, ""), rest.join("=")];
  })
);

const interactionType = (args.type || "question").trim();
const title = (args.title || "").trim();
const body = (args.body || "").trim();
const optionsRaw = (args.options || "").trim();
const prompt = (args.prompt || "").trim();
const start = (args.start || "").trim();
const end = (args.end || "").trim();
const minVersion = Number(args.minVersion || 0);
const maxVersion = Number(args.maxVersion || 0);
const dismissible = (args.dismissible || "true").trim().toLowerCase() !== "false";

if (!title) {
  console.error("Missing required --title");
  process.exit(1);
}

if (interactionType !== "question" && interactionType !== "opt_in") {
  console.error("Unsupported --type. Use question or opt_in.");
  process.exit(1);
}

const options = optionsRaw
  .split("|")
  .map((value) => value.trim())
  .filter(Boolean);

if (interactionType === "question" && options.length < 2) {
  console.error("Polls need at least 2 options. Example: --options='Yes|No'");
  process.exit(1);
}

if (interactionType === "opt_in" && options.length < 1) {
  console.error("Opt-ins need at least 1 button label. Example: --options='Join beta'");
  process.exit(1);
}

const payload = {
  active: true,
  title,
  body,
  type: "info",
  dismissible,
  targetMinVersion: Number.isFinite(minVersion) ? minVersion : 0,
  targetMaxVersion: Number.isFinite(maxVersion) ? maxVersion : 0,
  interactionType,
  question: {
    prompt,
    options
  },
  createdAt: "SERVER_TIMESTAMP"
};

if (start) payload.startDate = start;
if (end) payload.expiryDate = end;

console.log("Create a new document in Firestore collection `broadcasts` with:");
console.log(JSON.stringify(payload, null, 2));
console.log("");
console.log("Notes:");
console.log("- Replace SERVER_TIMESTAMP with a Firestore server timestamp in the console.");
console.log("- Use a NEW document ID for each new campaign so users can see the next one.");
console.log("- startDate and expiryDate should be Firestore Timestamp values.");
