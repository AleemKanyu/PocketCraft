// Firebase access shared by the Remote Control pages. Loaded lazily so other pages don't pay for it.

export const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAmJgiyi9Ri3LiFuCIhFt82pPOB1A3q044",
  authDomain: "pocketcraft-9f9ca.firebaseapp.com",
  projectId: "pocketcraft-9f9ca",
  storageBucket: "pocketcraft-9f9ca.appspot.com",
  messagingSenderId: "52965868712",
  appId: "1:52965868712:web:4ee0f09a562ef6d85e6e9d",
};

let cachedApp: any = null;
let cachedDb: any = null;
let cachedAuth: any = null;

async function getApp() {
  if (cachedApp) return cachedApp;
  const { initializeApp, getApps, getApp: existing } = await import("firebase/app");
  cachedApp = getApps().length > 0 ? existing() : initializeApp(FIREBASE_CONFIG);
  return cachedApp;
}

export async function getFirestoreDb() {
  if (cachedDb) return cachedDb;
  const app = await getApp();
  const { getFirestore, connectFirestoreEmulator } = await import("firebase/firestore");
  const db = getFirestore(app);
  if (typeof window !== "undefined") {
    const isEmulatorParam = window.location.search.includes("emulator=true");
    const isLocal = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
    if (isEmulatorParam || (isLocal && window.location.search.includes("use_emulator=1"))) {
      try {
        connectFirestoreEmulator(db, "127.0.0.1", 8080);
      } catch {
        // Already connected.
      }
    }
  }
  cachedDb = db;
  return db;
}

/** Firebase Auth plus the module, so a click handler can call signInWithPopup without awaiting an import. */
export async function loadAuth() {
  const app = await getApp();
  const mod = await import("firebase/auth");
  if (!cachedAuth) cachedAuth = mod.getAuth(app);
  return { auth: cachedAuth, mod };
}

export function toMillis(ts: any): number | null {
  if (!ts) return null;
  if (typeof ts.toMillis === "function") return ts.toMillis();
  if (typeof ts.seconds === "number") return ts.seconds * 1000;
  return null;
}

export function formatAgo(ms: number, now: number): string {
  const sec = Math.max(0, Math.floor((now - ms) / 1000));
  if (sec < 10) return "just now";
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hrs = Math.floor(min / 60);
  if (hrs < 48) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function formatDuration(ms: number): string {
  const min = Math.max(0, Math.floor(ms / 60000));
  if (min < 60) return `${min}m`;
  const hrs = Math.floor(min / 60);
  if (hrs < 24) return `${hrs}h ${min % 60}m`;
  return `${Math.floor(hrs / 24)}d ${hrs % 24}h`;
}
