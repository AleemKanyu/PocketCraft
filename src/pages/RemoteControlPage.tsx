import { useState, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
  Play, 
  Square, 
  RotateCw, 
  Users, 
  Server, 
  ShieldAlert, 
  CheckCircle2, 
  AlertTriangle, 
  Clock, 
  Wifi, 
  WifiOff,
  ExternalLink,
  ChevronRight
} from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

interface SessionData {
  enabled?: boolean;
  serverName?: string;
  state?: string;
  players?: number;
  maxPlayers?: number;
  lastCmdId?: string;
  updatedAt?: any; // Firestore Timestamp
}

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAmJgiyi9Ri3LiFuCIhFt82pPOB1A3q044",
  authDomain: "pocketcraft-9f9ca.firebaseapp.com",
  projectId: "pocketcraft-9f9ca",
  storageBucket: "pocketcraft-9f9ca.appspot.com",
  messagingSenderId: "52965868712",
  appId: "1:52965868712:web:4ee0f09a562ef6d85e6e9d"
};

let cachedDb: any = null;

async function getFirestoreDb() {
  if (cachedDb) return cachedDb;
  const { initializeApp, getApps, getApp } = await import("firebase/app");
  const { getFirestore, connectFirestoreEmulator } = await import("firebase/firestore");

  const app = getApps().length > 0 ? getApp() : initializeApp(FIREBASE_CONFIG);
  const db = getFirestore(app);

  if (typeof window !== "undefined") {
    const isEmulatorParam = window.location.search.includes("emulator=true");
    const isLocal = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
    if (isEmulatorParam || (isLocal && window.location.search.includes("use_emulator=1"))) {
      try {
        connectFirestoreEmulator(db, "127.0.0.1", 8080);
        console.log("[RemoteControl] Connected to Firestore emulator at 127.0.0.1:8080");
      } catch {
        // Ignore if already connected
      }
    }
  }

  cachedDb = db;
  return db;
}

export default function RemoteControlPage() {
  const { theme } = useTheme();
  const isDark = theme === "dark";

  const [code, setCode] = useState<string>("");
  const [session, setSession] = useState<SessionData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [errorStatus, setErrorStatus] = useState<string | null>(null);
  const [lastFetchTime, setLastFetchTime] = useState<number>(Date.now());
  const [cooldownSeconds, setCooldownSeconds] = useState<number>(0);
  const [actionLoading, setActionLoading] = useState<"start" | "stop" | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [autoRefreshExpired, setAutoRefreshExpired] = useState<boolean>(false);
  const [readsCount, setReadsCount] = useState<number>(0);

  const readsRef = useRef<number>(0);
  const mountedTimeRef = useRef<number>(Date.now());
  const timerRef = useRef<any>(null);

  // Extract code from URL hash: e.g. #abc123...
  useEffect(() => {
    function readCodeFromHash() {
      if (typeof window === "undefined") return;
      const raw = window.location.hash.replace(/^#/, "").trim();
      setCode(raw);
    }

    readCodeFromHash();
    window.addEventListener("hashchange", readCodeFromHash);
    return () => window.removeEventListener("hashchange", readCodeFromHash);
  }, []);

  // Fetch session doc
  const fetchSession = useCallback(async (isManualRefresh = false) => {
    if (!code) {
      setLoading(false);
      return;
    }

    // Spark budget guard: max 40 reads per visit
    if (readsRef.current >= 40) {
      setAutoRefreshExpired(true);
      return;
    }

    if (isManualRefresh) {
      setRefreshing(true);
    }

    try {
      const db = await getFirestoreDb();
      const { doc, getDoc } = await import("firebase/firestore");
      const docRef = doc(db, "remote_sessions", code);
      const snapshot = await getDoc(docRef);

      readsRef.current += 1;
      setReadsCount(readsRef.current);
      setLastFetchTime(Date.now());

      if (!snapshot.exists()) {
        setSession(null);
        setErrorStatus("This link is not valid or was turned off");
      } else {
        const data = snapshot.data() as SessionData;
        if (data.enabled === false) {
          setSession(data);
          setErrorStatus("This link is not valid or was turned off");
        } else {
          setSession(data);
          setErrorStatus(null);
        }
      }
    } catch (err: any) {
      console.error("[RemoteControl] Error fetching session:", err);
      setErrorStatus("Failed to connect to PocketHost services. Please check your network.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [code]);

  // Initial fetch on code change
  useEffect(() => {
    if (code) {
      setLoading(true);
      readsRef.current = 0;
      mountedTimeRef.current = Date.now();
      setAutoRefreshExpired(false);
      fetchSession(false);
    } else {
      setLoading(false);
      setSession(null);
    }
  }, [code, fetchSession]);

  // Auto-refresh every 20s while tab is visible, max 2 minutes (120s) or 40 reads
  useEffect(() => {
    if (!code) return;

    const interval = setInterval(() => {
      if (typeof document !== "undefined" && document.hidden) {
        return; // Don't refresh while backgrounded
      }

      const elapsed = Date.now() - mountedTimeRef.current;
      if (elapsed > 120000 || readsRef.current >= 40) {
        setAutoRefreshExpired(true);
        clearInterval(interval);
        return;
      }

      fetchSession(false);
    }, 20000);

    return () => clearInterval(interval);
  }, [code, fetchSession]);

  // Cooldown countdown timer
  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const t = setInterval(() => {
      setCooldownSeconds((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(t);
  }, [cooldownSeconds]);

  // Send Command: 'start' or 'stop'
  async function handleSendCommand(cmd: "start" | "stop") {
    if (!code || cooldownSeconds > 0 || actionLoading) return;

    setActionLoading(cmd);
    setActionSuccess(null);
    setErrorStatus(null);

    try {
      const db = await getFirestoreDb();
      const { doc, setDoc, serverTimestamp } = await import("firebase/firestore");
      const cmdRef = doc(db, "remote_commands", code);

      const randomId = Math.random().toString(36).substring(2, 12) + Date.now().toString(36);
      await setDoc(cmdRef, {
        cmd: cmd,
        cmdId: randomId.slice(0, 36),
        cmdAt: serverTimestamp()
      });

      setCooldownSeconds(30);
      setActionSuccess(`Command '${cmd.toUpperCase()}' sent! PocketHost is processing...`);
      
      // Give the phone a moment, then refresh
      setTimeout(() => {
        fetchSession(false);
      }, 3000);
    } catch (err: any) {
      console.error("[RemoteControl] Send command failed:", err);
      if (err?.message?.includes("permission-denied") || err?.code === "permission-denied") {
        setErrorStatus("Command rejected by security rules. Ensure 30s have passed since the last command.");
      } else {
        setErrorStatus("Failed to send command. Please try again.");
      }
    } finally {
      setActionLoading(null);
    }
  }

  // Format relative updated text
  function formatRelativeTime(ms: number): string {
    const sec = Math.floor((Date.now() - ms) / 1000);
    if (sec < 10) return "just now";
    if (sec < 60) return `${sec}s ago`;
    const min = Math.floor(sec / 60);
    if (min < 60) return `${min}m ago`;
    const hrs = Math.floor(min / 60);
    return `${hrs}h ago`;
  }

  // Check staleness (if last update was > 15 minutes ago and state indicates online or starting)
  const isStale = session?.updatedAt && (Date.now() - (session.updatedAt.toMillis ? session.updatedAt.toMillis() : (session.updatedAt.seconds ? session.updatedAt.seconds * 1000 : Date.now())) > 15 * 60 * 1000);

  const serverState = (session?.state || "OFFLINE").toUpperCase();
  const isOnline = serverState === "ONLINE";
  const isStarting = serverState === "STARTING";
  const isSleeping = serverState === "SLEEPING";

  return (
    <div
      className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
        isDark ? "bg-[#0a0a0a] text-white" : "bg-[#f8f9fa] text-black"
      }`}
    >
      <SEO
        title="Remote Control | PocketHost"
        description="Securely start and stop your PocketHost Minecraft server from your browser."
        path="/remote"
        noindex
      />
      <Navbar />

      <main className="relative py-20 sm:py-28 px-4 sm:px-6 overflow-hidden">
        {/* Ambient glow */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-[#7FE620]/5 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-xl mx-auto relative z-10">
          {/* Header */}
          <div className="text-center mb-8">
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider mb-3 bg-[#7FE620]/10 text-[#7FE620] border border-[#7FE620]/20"
            >
              <Server className="w-3.5 h-3.5" />
              PocketHost Remote Control
            </motion.div>
            <motion.h1
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.05 }}
              className="text-2xl sm:text-3xl font-extrabold tracking-tight"
            >
              {session?.serverName || "Minecraft Server"}
            </motion.h1>
            <motion.p
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
              className={`text-sm mt-1 ${isDark ? "text-white/60" : "text-black/60"}`}
            >
              Authorized remote control dashboard
            </motion.p>
          </div>

          {/* Missing Code Prompt */}
          {!code && (
            <motion.div
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              className={`p-6 sm:p-8 rounded-2xl border shadow-xl ${
                isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/10"
              }`}
            >
              <div className="flex flex-col items-center text-center">
                <div className="w-12 h-12 rounded-2xl bg-[#7FE620]/10 border border-[#7FE620]/30 flex items-center justify-center text-[#7FE620] mb-4">
                  <Wifi className="w-6 h-6" />
                </div>
                <h2 className="text-lg font-bold mb-2">No Remote Code Provided</h2>
                <p className={`text-sm mb-6 max-w-sm ${isDark ? "text-white/60" : "text-black/60"}`}>
                  To control a server, open the PocketHost app on Android, go to{" "}
                  <strong className="text-[#7FE620]">Settings &gt; Remote Control</strong>, and tap{" "}
                  <strong>Copy Link</strong> or scan the QR code.
                </p>

                <div className="w-full max-w-md">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="Paste link or code here..."
                      className={`flex-1 px-4 py-2.5 rounded-xl border text-sm outline-none transition-colors ${
                        isDark
                          ? "bg-white/5 border-white/10 text-white placeholder-white/30 focus:border-[#7FE620]"
                          : "bg-black/5 border-black/10 text-black placeholder-black/30 focus:border-[#5ab30f]"
                      }`}
                      onChange={(e) => {
                        const val = e.target.value.trim();
                        const match = val.includes("#") ? val.split("#")[1] : val;
                        if (match) {
                          window.location.hash = match;
                          setCode(match);
                        }
                      }}
                    />
                  </div>
                </div>
              </div>
            </motion.div>
          )}

          {/* Loading State */}
          {code && loading && (
            <div className={`p-8 rounded-2xl border text-center ${
              isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/10"
            }`}>
              <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin mx-auto mb-4" />
              <p className={`text-sm ${isDark ? "text-white/70" : "text-black/70"}`}>Connecting to PocketHost...</p>
            </div>
          )}

          {/* Error / Invalid Link State */}
          {code && !loading && errorStatus && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className={`p-6 rounded-2xl border mb-6 ${
                isDark ? "bg-red-950/20 border-red-500/30 text-red-200" : "bg-red-50 border-red-200 text-red-800"
              }`}
            >
              <div className="flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
                <div className="flex-1">
                  <h3 className="font-semibold text-sm">Remote Access Notice</h3>
                  <p className="text-xs sm:text-sm mt-1 opacity-90">{errorStatus}</p>
                </div>
              </div>
            </motion.div>
          )}

          {/* Stale Warning */}
          {code && !loading && !errorStatus && isStale && (
            <div className={`p-4 rounded-xl border mb-6 ${
              isDark ? "bg-amber-950/20 border-amber-500/30 text-amber-200" : "bg-amber-50 border-amber-200 text-amber-800"
            }`}>
              <div className="flex items-center gap-3">
                <WifiOff className="w-4 h-4 text-amber-500 shrink-0" />
                <p className="text-xs sm:text-sm">
                  The owner's phone may be offline or PocketHost is not running there.
                </p>
              </div>
            </div>
          )}

          {/* Active Control Panel */}
          {code && !loading && session && (
            <motion.div
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              className={`p-6 sm:p-8 rounded-2xl border shadow-xl relative ${
                isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/10"
              }`}
            >
              {/* Status Header */}
              <div className="flex items-center justify-between pb-6 border-b border-white/10 mb-6">
                <div>
                  <span className={`text-xs block font-medium uppercase tracking-wider ${
                    isDark ? "text-white/40" : "text-black/40"
                  }`}>
                    Live Status
                  </span>
                  <div className="flex items-center gap-2.5 mt-1">
                    {/* Status Chip */}
                    {isOnline ? (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                        ONLINE
                      </span>
                    ) : isStarting ? (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30">
                        <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
                        STARTING...
                      </span>
                    ) : isSleeping ? (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-indigo-500/15 text-indigo-400 border border-indigo-500/30">
                        <span className="w-2 h-2 rounded-full bg-indigo-400" />
                        SLEEPING
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-zinc-500/15 text-zinc-400 border border-zinc-500/30">
                        <span className="w-2 h-2 rounded-full bg-zinc-500" />
                        OFFLINE
                      </span>
                    )}

                    {/* Players Count Badge */}
                    {isOnline && (
                      <span className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full border ${
                        isDark ? "bg-white/5 border-white/10 text-white/80" : "bg-black/5 border-black/10 text-black/80"
                      }`}>
                        <Users className="w-3.5 h-3.5 text-[#7FE620]" />
                        <span>{session.players ?? 0} {session.players === 1 ? "player" : "players"}</span>
                      </span>
                    )}
                  </div>
                </div>

                {/* Manual Refresh Button */}
                <button
                  type="button"
                  onClick={() => fetchSession(true)}
                  disabled={refreshing}
                  className={`p-2.5 rounded-xl border transition-all flex items-center justify-center ${
                    isDark
                      ? "bg-white/5 hover:bg-white/10 border-white/10 text-white/80 hover:text-white"
                      : "bg-black/5 hover:bg-black/10 border-black/10 text-black/80 hover:text-black"
                  } disabled:opacity-50`}
                  title="Refresh Status"
                >
                  <RotateCw className={`w-4 h-4 ${refreshing ? "animate-spin text-[#7FE620]" : ""}`} />
                </button>
              </div>

              {/* Action feedback */}
              <AnimatePresence>
                {actionSuccess && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    className="mb-6 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs sm:text-sm flex items-center gap-2"
                  >
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{actionSuccess}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Start / Stop Controls */}
              <div className="grid grid-cols-2 gap-3 sm:gap-4 mb-6">
                {/* START BUTTON */}
                <button
                  type="button"
                  onClick={() => handleSendCommand("start")}
                  disabled={isOnline || isStarting || cooldownSeconds > 0 || actionLoading !== null}
                  className={`py-3.5 px-4 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-lg ${
                    isOnline || isStarting || cooldownSeconds > 0 || actionLoading !== null
                      ? isDark
                        ? "bg-white/5 text-white/30 border border-white/5 cursor-not-allowed shadow-none"
                        : "bg-black/5 text-black/30 border border-black/5 cursor-not-allowed shadow-none"
                      : "bg-[#7FE620] hover:bg-[#72d01d] text-black active:scale-[0.98] shadow-[#7FE620]/20"
                  }`}
                >
                  {actionLoading === "start" ? (
                    <div className="w-4 h-4 rounded-full border-2 border-black border-t-transparent animate-spin" />
                  ) : (
                    <Play className="w-4 h-4 fill-current" />
                  )}
                  <span>Start Server</span>
                </button>

                {/* STOP BUTTON */}
                <button
                  type="button"
                  onClick={() => handleSendCommand("stop")}
                  disabled={(!isOnline && !isStarting && !isSleeping) || cooldownSeconds > 0 || actionLoading !== null}
                  className={`py-3.5 px-4 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-lg ${
                    (!isOnline && !isStarting && !isSleeping) || cooldownSeconds > 0 || actionLoading !== null
                      ? isDark
                        ? "bg-white/5 text-white/30 border border-white/5 cursor-not-allowed shadow-none"
                        : "bg-black/5 text-black/30 border border-black/5 cursor-not-allowed shadow-none"
                      : "bg-rose-600 hover:bg-rose-500 text-white active:scale-[0.98] shadow-rose-600/20"
                  }`}
                >
                  {actionLoading === "stop" ? (
                    <div className="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
                  ) : (
                    <Square className="w-4 h-4 fill-current" />
                  )}
                  <span>Stop Server</span>
                </button>
              </div>

              {/* Cooldown Counter Banner */}
              {cooldownSeconds > 0 && (
                <div className={`p-3 rounded-xl mb-4 text-xs flex items-center justify-between border ${
                  isDark ? "bg-white/5 border-white/10 text-white/70" : "bg-black/5 border-black/10 text-black/70"
                }`}>
                  <span className="flex items-center gap-1.5 font-medium">
                    <Clock className="w-3.5 h-3.5 text-[#7FE620]" />
                    Command Cooldown:
                  </span>
                  <span className="font-mono font-bold text-[#7FE620]">{cooldownSeconds}s</span>
                </div>
              )}

              {/* Footer Meta */}
              <div className={`pt-4 border-t flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs ${
                isDark ? "border-white/10 text-white/40" : "border-black/10 text-black/40"
              }`}>
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5" />
                  <span>Updated {formatRelativeTime(lastFetchTime)}</span>
                </div>

                {autoRefreshExpired ? (
                  <span className="text-amber-500 font-medium">
                    Auto-refresh paused. Tap refresh icon above to update.
                  </span>
                ) : (
                  <span>Auto-refresh active (20s)</span>
                )}
              </div>
            </motion.div>
          )}

          {/* Security & Spark Plan Explainer Card */}
          <div className={`mt-8 p-5 rounded-xl border text-xs leading-relaxed ${
            isDark ? "bg-white/[0.02] border-white/5 text-white/50" : "bg-black/[0.02] border-black/5 text-black/50"
          }`}>
            <div className="flex items-center gap-2 mb-2 font-semibold text-[#7FE620]">
              <ShieldAlert className="w-4 h-4" />
              <span>Secure Remote Execution</span>
            </div>
            <p>
              This link gives start/stop capability only. It cannot edit server files, view terminal console, access player inventories, or change app settings. PocketHost never exposes internal phone files to the web.
            </p>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
