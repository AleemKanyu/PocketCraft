import { useState, useEffect, useRef } from "react";
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
  Copy,
  Thermometer,
  BatteryCharging,
  Battery,
  MemoryStick,
  Gauge,
  Timer,
  Tag,
  ChevronDown,
  Navigation,
  RefreshCw,
} from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

interface SessionHealth {
  tempTenths?: number;
  battery?: number;
  ramPct?: number;
  charging?: boolean;
}

interface SessionData {
  enabled?: boolean;
  serverName?: string;
  state?: string;
  players?: number;
  maxPlayers?: number;
  lastCmdId?: string;
  updatedAt?: any; // Firestore Timestamp
  version?: string;
  address?: string;
  port?: number;
  playerNames?: string[];
  startedAt?: any; // Firestore Timestamp
  tpsTenths?: number;
  health?: SessionHealth;
  playerDetails?: PlayerDetail[];
  playerDetailsAt?: any; // Firestore Timestamp
}

interface PlayerDetail {
  n: string;
  x?: number;
  y?: number;
  z?: number;
  d?: string;
  hp?: number;
}

type Command = "start" | "stop" | "restart" | "players" | "tp";

const DIMENSION_LABEL: Record<string, string> = {
  overworld: "Overworld",
  the_nether: "Nether",
  the_end: "End",
};

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAmJgiyi9Ri3LiFuCIhFt82pPOB1A3q044",
  authDomain: "pocketcraft-9f9ca.firebaseapp.com",
  projectId: "pocketcraft-9f9ca",
  storageBucket: "pocketcraft-9f9ca.appspot.com",
  messagingSenderId: "52965868712",
  appId: "1:52965868712:web:4ee0f09a562ef6d85e6e9d"
};

// The phone checks in every 12 minutes, so 20 minutes of silence means it really is gone.
const STALE_AFTER_MS = 20 * 60 * 1000;

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

function toMillis(ts: any): number | null {
  if (!ts) return null;
  if (typeof ts.toMillis === "function") return ts.toMillis();
  if (typeof ts.seconds === "number") return ts.seconds * 1000;
  return null;
}

function formatAgo(ms: number, now: number): string {
  const sec = Math.max(0, Math.floor((now - ms) / 1000));
  if (sec < 10) return "just now";
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hrs = Math.floor(min / 60);
  return `${hrs}h ${min % 60}m ago`;
}

function formatDuration(ms: number): string {
  const min = Math.max(0, Math.floor(ms / 60000));
  if (min < 60) return `${min}m`;
  const hrs = Math.floor(min / 60);
  if (hrs < 24) return `${hrs}h ${min % 60}m`;
  return `${Math.floor(hrs / 24)}d ${hrs % 24}h`;
}

export default function RemoteControlPage() {
  const { theme } = useTheme();
  const isDark = theme === "dark";

  const [code, setCode] = useState<string>("");
  const [session, setSession] = useState<SessionData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorStatus, setErrorStatus] = useState<string | null>(null);
  const [now, setNow] = useState<number>(Date.now());
  const [cooldownSeconds, setCooldownSeconds] = useState<number>(0);
  const [actionLoading, setActionLoading] = useState<Command | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  const [visible, setVisible] = useState<boolean>(true);
  const [playersOpen, setPlayersOpen] = useState<boolean>(false);
  const [tpFrom, setTpFrom] = useState<string | null>(null);
  const [tpTo, setTpTo] = useState<string>("");

  const unsubscribeRef = useRef<(() => void) | null>(null);

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

  // Pause the live listener while the tab is hidden: a listener costs one read when it starts
  // and one per change, so a forgotten background tab should not keep paying.
  useEffect(() => {
    if (typeof document === "undefined") return;
    const onVisibility = () => setVisible(!document.hidden);
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, []);

  // Live session: Firestore pushes each change the phone writes. This replaces polling every
  // 20 seconds (about 7 reads a visit and a 2-minute cutoff) with 1 read plus 1 per change.
  useEffect(() => {
    if (!code) {
      setLoading(false);
      setSession(null);
      return;
    }
    if (!visible) return;
    let cancelled = false;
    (async () => {
      try {
        const db = await getFirestoreDb();
        const { doc, onSnapshot } = await import("firebase/firestore");
        if (cancelled) return;
        unsubscribeRef.current = onSnapshot(
          doc(db, "remote_sessions", code),
          (snapshot: any) => {
            setLoading(false);
            if (!snapshot.exists()) {
              setSession(null);
              setErrorStatus("This link is not valid or was turned off");
              return;
            }
            const data = snapshot.data() as SessionData;
            setSession(data);
            setErrorStatus(data.enabled === false ? "This link is not valid or was turned off" : null);
          },
          (err: any) => {
            console.error("[RemoteControl] Live session error:", err);
            setLoading(false);
            setErrorStatus("Failed to connect to PocketHost services. Please check your network.");
          }
        );
      } catch (err) {
        console.error("[RemoteControl] Could not open the live session:", err);
        setLoading(false);
        setErrorStatus("Failed to connect to PocketHost services. Please check your network.");
      }
    })();
    return () => {
      cancelled = true;
      unsubscribeRef.current?.();
      unsubscribeRef.current = null;
    };
  }, [code, visible]);

  // Re-render relative times ("3m ago", uptime) without touching Firestore.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 15000);
    return () => clearInterval(t);
  }, []);

  // Cooldown countdown timer
  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const t = setInterval(() => {
      setCooldownSeconds((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(t);
  }, [cooldownSeconds]);

  async function handleSendCommand(cmd: Command, arg?: string) {
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
        cmdAt: serverTimestamp(),
        ...(arg ? { arg } : {})
      });

      setCooldownSeconds(30);
      setActionSuccess(
        cmd === "players"
          ? "Asking the phone for player details..."
          : cmd === "tp"
            ? `Teleport sent: ${arg?.replace(" ", " → ")}`
            : `Command '${cmd.toUpperCase()}' sent! PocketHost is processing...`
      );
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

  async function copyText(label: string, text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(label);
      setTimeout(() => setCopied(null), 1500);
    } catch {
      // Clipboard can be blocked; the text is still visible to copy by hand.
    }
  }

  const lastSeenMs = toMillis(session?.updatedAt);
  const isStale = lastSeenMs !== null && now - lastSeenMs > STALE_AFTER_MS;

  const serverState = (session?.state || "OFFLINE").toUpperCase();
  const isOnline = serverState === "ONLINE";
  const isStarting = serverState === "STARTING";
  const isSleeping = serverState === "SLEEPING";

  const startedAtMs = toMillis(session?.startedAt);
  const javaAddress = session?.address ? (session.port ? `${session.address}:${session.port}` : session.address) : null;
  const health = session?.health;
  const tps = typeof session?.tpsTenths === "number" ? session.tpsTenths / 10 : null;
  const names = (session?.playerNames || []).filter(Boolean);

  const muted = isDark ? "text-white/50" : "text-black/50";
  const tile = `p-3.5 rounded-xl border ${isDark ? "bg-white/[0.03] border-white/10" : "bg-black/[0.02] border-black/10"}`;

  const disabledBtn = isDark
    ? "bg-white/5 text-white/30 border border-white/5 cursor-not-allowed shadow-none"
    : "bg-black/5 text-black/30 border border-black/5 cursor-not-allowed shadow-none";
  const busy = cooldownSeconds > 0 || actionLoading !== null;

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
                  The owner's phone has not checked in for {lastSeenMs ? formatAgo(lastSeenMs, now).replace(" ago", "") : "a while"}. It may be offline or PocketHost is not running there.
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
              <div className={`flex items-center justify-between pb-6 border-b mb-6 ${isDark ? "border-white/10" : "border-black/10"}`}>
                <div>
                  <span className={`text-xs block font-medium uppercase tracking-wider ${
                    isDark ? "text-white/40" : "text-black/40"
                  }`}>
                    Live Status
                  </span>
                  <div className="flex items-center gap-2.5 mt-1 flex-wrap">
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

                    {isOnline && (
                      <span className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full border ${
                        isDark ? "bg-white/5 border-white/10 text-white/80" : "bg-black/5 border-black/10 text-black/80"
                      }`}>
                        <Users className="w-3.5 h-3.5 text-[#7FE620]" />
                        <span>
                          {session.players ?? 0}
                          {typeof session.maxPlayers === "number" ? ` / ${session.maxPlayers}` : ""}
                          {" "}{(session.players ?? 0) === 1 ? "player" : "players"}
                        </span>
                      </span>
                    )}
                  </div>
                </div>

                <div className={`text-right text-xs ${muted}`}>
                  <span className="block uppercase tracking-wider font-medium">Phone</span>
                  <span className="flex items-center gap-1 justify-end mt-1">
                    <span className={`w-1.5 h-1.5 rounded-full ${isStale ? "bg-amber-500" : "bg-emerald-400 animate-pulse"}`} />
                    {lastSeenMs ? formatAgo(lastSeenMs, now) : "unknown"}
                  </span>
                </div>
              </div>

              {/* Server info tiles */}
              <div className="grid grid-cols-2 gap-2.5 mb-6">
                  {!javaAddress && (
                    <div className={`${tile} col-span-2`}>
                      <span className={`block text-[11px] uppercase tracking-wider font-medium ${muted}`}>Join address</span>
                      <span className={`block text-sm mt-0.5 ${muted}`}>Shows here once the server has been online.</span>
                    </div>
                  )}
                  {javaAddress && (
                    <button
                      type="button"
                      onClick={() => copyText("java", javaAddress)}
                      className={`${tile} col-span-2 text-left flex items-center justify-between gap-2 hover:border-[#7FE620]/40 transition-colors`}
                    >
                      <span className="min-w-0">
                        <span className={`block text-[11px] uppercase tracking-wider font-medium ${muted}`}>Join address</span>
                        <span className="block font-mono font-semibold text-sm truncate">{javaAddress}</span>
                        {session.port ? (
                          <span className={`block text-[11px] mt-0.5 ${muted}`}>
                            Bedrock: {session.address} · port {session.port}
                          </span>
                        ) : null}
                      </span>
                      <span className="shrink-0 flex items-center gap-1 text-xs text-[#7FE620] font-semibold">
                        {copied === "java" ? <CheckCircle2 className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                        {copied === "java" ? "Copied" : "Copy"}
                      </span>
                    </button>
                  )}
                  {session.version && (
                    <div className={tile}>
                      <span className={`flex items-center gap-1 text-[11px] uppercase tracking-wider font-medium ${muted}`}>
                        <Tag className="w-3 h-3" /> Version
                      </span>
                      <span className="block font-semibold text-sm mt-0.5">{session.version}</span>
                    </div>
                  )}
                  {isOnline && startedAtMs && (
                    <div className={tile}>
                      <span className={`flex items-center gap-1 text-[11px] uppercase tracking-wider font-medium ${muted}`}>
                        <Timer className="w-3 h-3" /> Uptime
                      </span>
                      <span className="block font-semibold text-sm mt-0.5">{formatDuration(now - startedAtMs)}</span>
                    </div>
                  )}
              </div>

              {/* Online players: names are live; positions are fetched only when opened or refreshed */}
              {isOnline && (
                <div className={`${tile} mb-6`}>
                  <button
                    type="button"
                    onClick={() => {
                      const opening = !playersOpen;
                      setPlayersOpen(opening);
                      setTpFrom(null);
                      if (opening && names.length > 0 && !busy) handleSendCommand("players");
                    }}
                    className="w-full flex items-center justify-between gap-2 text-left"
                  >
                    <span className="flex items-center gap-2 font-semibold text-sm">
                      <Users className="w-4 h-4 text-[#7FE620]" />
                      Online players
                      <span className={`text-xs font-medium ${muted}`}>
                        {names.length}{typeof session.maxPlayers === "number" ? ` / ${session.maxPlayers}` : ""}
                      </span>
                    </span>
                    <ChevronDown className={`w-4 h-4 transition-transform ${playersOpen ? "rotate-180" : ""} ${muted}`} />
                  </button>

                  {!playersOpen && names.length > 0 && (
                    <div className="mt-2.5 flex flex-wrap gap-1.5">
                      {names.map((name) => (
                        <span
                          key={name}
                          className={`text-xs px-2.5 py-1 rounded-lg border font-medium ${
                            isDark ? "bg-[#7FE620]/10 border-[#7FE620]/20 text-[#a6f05a]" : "bg-[#7FE620]/10 border-[#7FE620]/30 text-[#3d7a0a]"
                          }`}
                        >
                          {name}
                        </span>
                      ))}
                    </div>
                  )}

                  <AnimatePresence initial={false}>
                    {playersOpen && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="overflow-hidden"
                      >
                        {names.length === 0 ? (
                          <p className={`text-xs mt-3 ${muted}`}>Nobody is online right now.</p>
                        ) : (
                          <div className="mt-3 space-y-2">
                            {names.map((name) => {
                              const det = (session.playerDetails || []).find((p) => p.n === name);
                              const hasPos = det && typeof det.x === "number";
                              const others = names.filter((n) => n !== name);
                              return (
                                <div
                                  key={name}
                                  className={`rounded-lg border px-3 py-2.5 ${isDark ? "border-white/10 bg-white/[0.02]" : "border-black/10 bg-white"}`}
                                >
                                  <div className="flex items-center justify-between gap-2">
                                    <div className="min-w-0">
                                      <span className="block font-semibold text-sm truncate">{name}</span>
                                      <span className={`block text-[11px] mt-0.5 ${muted}`}>
                                        {det
                                          ? hasPos
                                            ? `${DIMENSION_LABEL[det.d || ""] || det.d || "World"} · ${det.x}, ${det.y}, ${det.z}${typeof det.hp === "number" ? ` · ❤ ${det.hp}` : ""}`
                                            : name.startsWith(".") ? "Bedrock player · position not available" : "Position not available"
                                          : toMillis(session.playerDetailsAt)
                                            ? "Joined after the last refresh"
                                            : busy ? "Loading details..." : "Press refresh to load"}
                                      </span>
                                    </div>
                                    {others.length > 0 && (
                                      <button
                                        type="button"
                                        onClick={() => {
                                          setTpFrom(tpFrom === name ? null : name);
                                          setTpTo(others[0]);
                                        }}
                                        className="shrink-0 flex items-center gap-1 text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-[#7FE620]/15 text-[#5ab30f] hover:bg-[#7FE620]/25 transition-colors"
                                      >
                                        <Navigation className="w-3.5 h-3.5" />
                                        Teleport
                                      </button>
                                    )}
                                  </div>
                                  {tpFrom === name && (
                                    <div className="mt-2.5 flex items-center gap-2">
                                      <span className={`text-xs shrink-0 ${muted}`}>to</span>
                                      <select
                                        value={tpTo}
                                        onChange={(e) => setTpTo(e.target.value)}
                                        className={`flex-1 min-w-0 text-sm px-2.5 py-1.5 rounded-lg border outline-none ${
                                          isDark ? "bg-[#141414] border-white/15 text-white" : "bg-white border-black/15 text-black"
                                        }`}
                                      >
                                        {others.map((o) => (
                                          <option key={o} value={o}>{o}</option>
                                        ))}
                                      </select>
                                      <button
                                        type="button"
                                        disabled={busy || !tpTo}
                                        onClick={() => {
                                          handleSendCommand("tp", `${name} ${tpTo}`);
                                          setTpFrom(null);
                                        }}
                                        className={`shrink-0 text-xs font-bold px-3 py-1.5 rounded-lg ${
                                          busy ? disabledBtn : "bg-[#7FE620] text-black hover:bg-[#72d01d]"
                                        }`}
                                      >
                                        Go
                                      </button>
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                        <div className={`mt-3 flex items-center justify-between text-[11px] ${muted}`}>
                          <span>
                            {toMillis(session.playerDetailsAt)
                              ? `Positions from ${formatAgo(toMillis(session.playerDetailsAt)!, now)}`
                              : "Positions load when you open this"}
                          </span>
                          <button
                            type="button"
                            disabled={busy || names.length === 0}
                            onClick={() => handleSendCommand("players")}
                            className={`flex items-center gap-1 font-semibold ${busy ? "opacity-40 cursor-not-allowed" : "text-[#5ab30f] hover:underline"}`}
                          >
                            <RefreshCw className="w-3 h-3" />
                            Refresh
                          </button>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              )}

              {/* Phone health */}
              {health && !isStale && (
                <div className="grid grid-cols-4 gap-2 mb-6">
                  <div className={`${tile} text-center`}>
                    <Thermometer className={`w-4 h-4 mx-auto ${
                      (health.tempTenths ?? 0) >= 450 ? "text-rose-500" : (health.tempTenths ?? 0) >= 380 ? "text-amber-500" : "text-[#7FE620]"
                    }`} />
                    <span className="block text-sm font-bold mt-1">
                      {typeof health.tempTenths === "number" ? `${(health.tempTenths / 10).toFixed(0)}°C` : "—"}
                    </span>
                    <span className={`block text-[10px] ${muted}`}>Temp</span>
                  </div>
                  <div className={`${tile} text-center`}>
                    {health.charging ? (
                      <BatteryCharging className="w-4 h-4 mx-auto text-[#7FE620]" />
                    ) : (
                      <Battery className={`w-4 h-4 mx-auto ${(health.battery ?? 100) <= 20 ? "text-rose-500" : "text-[#7FE620]"}`} />
                    )}
                    <span className="block text-sm font-bold mt-1">
                      {typeof health.battery === "number" ? `${health.battery}%` : "—"}
                    </span>
                    <span className={`block text-[10px] ${muted}`}>Battery</span>
                  </div>
                  <div className={`${tile} text-center`}>
                    <MemoryStick className={`w-4 h-4 mx-auto ${(health.ramPct ?? 0) >= 90 ? "text-rose-500" : "text-[#7FE620]"}`} />
                    <span className="block text-sm font-bold mt-1">
                      {typeof health.ramPct === "number" ? `${health.ramPct}%` : "—"}
                    </span>
                    <span className={`block text-[10px] ${muted}`}>RAM</span>
                  </div>
                  <div className={`${tile} text-center`}>
                    <Gauge className={`w-4 h-4 mx-auto ${tps !== null && tps < 15 ? "text-amber-500" : "text-[#7FE620]"}`} />
                    <span className="block text-sm font-bold mt-1">{isOnline && tps !== null ? tps.toFixed(1) : "—"}</span>
                    <span className={`block text-[10px] ${muted}`}>TPS</span>
                  </div>
                </div>
              )}

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

              {/* Start / Restart / Stop Controls */}
              <div className="grid grid-cols-3 gap-2.5 sm:gap-3 mb-6">
                <button
                  type="button"
                  onClick={() => handleSendCommand("start")}
                  disabled={busy}
                  className={`py-3.5 px-2 rounded-xl font-bold text-sm flex items-center justify-center gap-1.5 transition-all shadow-lg ${
                    busy ? disabledBtn : "bg-[#7FE620] hover:bg-[#72d01d] text-black active:scale-[0.98] shadow-[#7FE620]/20"
                  }`}
                >
                  {actionLoading === "start" ? (
                    <div className="w-4 h-4 rounded-full border-2 border-black border-t-transparent animate-spin" />
                  ) : (
                    <Play className="w-4 h-4 fill-current" />
                  )}
                  <span>Start</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleSendCommand("restart")}
                  disabled={busy || !isOnline}
                  title={isOnline ? "Restart the server" : "Only while the server is online"}
                  className={`py-3.5 px-2 rounded-xl font-bold text-sm flex items-center justify-center gap-1.5 transition-all shadow-lg ${
                    busy || !isOnline
                      ? disabledBtn
                      : isDark
                        ? "bg-white/10 hover:bg-white/15 text-white border border-white/10 active:scale-[0.98]"
                        : "bg-black/10 hover:bg-black/15 text-black border border-black/10 active:scale-[0.98]"
                  }`}
                >
                  {actionLoading === "restart" ? (
                    <div className="w-4 h-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
                  ) : (
                    <RotateCw className="w-4 h-4" />
                  )}
                  <span>Restart</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleSendCommand("stop")}
                  disabled={busy}
                  className={`py-3.5 px-2 rounded-xl font-bold text-sm flex items-center justify-center gap-1.5 transition-all shadow-lg ${
                    busy ? disabledBtn : "bg-rose-600 hover:bg-rose-500 text-white active:scale-[0.98] shadow-rose-600/20"
                  }`}
                >
                  {actionLoading === "stop" ? (
                    <div className="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
                  ) : (
                    <Square className="w-4 h-4 fill-current" />
                  )}
                  <span>Stop</span>
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
              <div className={`pt-4 border-t flex items-center justify-between gap-2 text-xs ${
                isDark ? "border-white/10 text-white/40" : "border-black/10 text-black/40"
              }`}>
                <span className="flex items-center gap-1.5">
                  <span className={`w-1.5 h-1.5 rounded-full ${visible ? "bg-emerald-400 animate-pulse" : "bg-zinc-500"}`} />
                  {visible ? "Live updates on" : "Paused while this tab is hidden"}
                </span>
                {lastSeenMs && <span>Last update {formatAgo(lastSeenMs, now)}</span>}
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
              This link can start, stop and restart the server, and shows its status. It cannot edit server files, view the console, access player inventories, or change app settings. PocketHost never exposes internal phone files to the web.
            </p>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
