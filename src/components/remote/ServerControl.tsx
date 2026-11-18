import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Play,
  Square,
  RotateCw,
  Users,
  CheckCircle2,
  AlertTriangle,
  WifiOff,
  Copy,
  Thermometer,
  BatteryCharging,
  Battery,
  MemoryStick,
  Gauge,
  Timer,
  Tag,
  Navigation,
  RefreshCw,
  Smartphone,
  Crown,
} from "lucide-react";
import RemoteAdminPanel from "../RemoteAdminPanel";
import { Avatar } from "./Avatar";
import { getFirestoreDb, toMillis, formatAgo, formatDuration } from "../../lib/remoteFirebase";

interface SessionHealth {
  tempTenths?: number;
  battery?: number;
  ramPct?: number;
  charging?: boolean;
}

export interface SessionData {
  enabled?: boolean;
  serverName?: string;
  state?: string;
  players?: number;
  maxPlayers?: number;
  updatedAt?: any;
  version?: string;
  address?: string;
  port?: number;
  playerNames?: string[];
  startedAt?: any;
  tpsTenths?: number;
  health?: SessionHealth;
  playerDetails?: PlayerDetail[];
  playerDetailsAt?: any;
  // "free" phones obey start/stop only; "premium" phones allow more. Missing = an older app.
  tier?: "free" | "premium";
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

const DIMENSION_LABEL: Record<string, string> = { overworld: "Overworld", the_nether: "Nether", the_end: "End" };

// The phone does not check in on a timer (that would cost a write per phone every few minutes
// across thousands of users). When the page opens with data older than PING_IF_OLDER_MS it sends
// one "ping"; the phone answers with one write. No answer within PING_WAIT_MS = phone offline.
const PING_IF_OLDER_MS = 3 * 60 * 1000;
const PING_WAIT_MS = 40 * 1000;

interface Props {
  code: string;
  adminKey?: string;
  isDark: boolean;
  /** Shown above the name, e.g. the phone model in the account view. */
  subtitle?: string;
}

/**
 * One server: status first, the main action right under it, then details, players and (with
 * Premium and the admin key) full control. Used by share links and by the account view.
 */
export default function ServerControl({ code, adminKey = "", isDark, subtitle }: Props) {
  const [session, setSession] = useState<SessionData | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorStatus, setErrorStatus] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [cooldown, setCooldown] = useState(0);
  const [actionLoading, setActionLoading] = useState<Command | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [visible, setVisible] = useState(true);
  const [pingSentAt, setPingSentAt] = useState<number | null>(null);
  const [tpFrom, setTpFrom] = useState<string | null>(null);
  const [tpTo, setTpTo] = useState("");
  const unsubscribeRef = useRef<(() => void) | null>(null);

  // Pause the live listener while the tab is hidden: a listener costs one read when it starts
  // and one per change, so a forgotten background tab should not keep paying.
  useEffect(() => {
    const onVisibility = () => setVisible(!document.hidden);
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, []);

  useEffect(() => {
    setLoading(true);
    setSession(null);
    setErrorStatus(null);
    setPingSentAt(null);
  }, [code]);

  // Live session: Firestore pushes each change the phone writes.
  useEffect(() => {
    if (!code || !visible) return;
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
              setErrorStatus("This link isn't valid any more, or Remote Control was turned off on the phone.");
              return;
            }
            const data = snapshot.data() as SessionData;
            setSession(data);
            setErrorStatus(data.enabled === false ? "Remote Control was turned off on the phone." : null);
          },
          () => {
            setLoading(false);
            setErrorStatus("Couldn't reach PocketHost. Check your internet connection.");
          }
        );
      } catch {
        setLoading(false);
        setErrorStatus("Couldn't reach PocketHost. Check your internet connection.");
      }
    })();
    return () => {
      cancelled = true;
      unsubscribeRef.current?.();
      unsubscribeRef.current = null;
    };
  }, [code, visible]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 5000);
    return () => clearInterval(t);
  }, []);

  // Ask the phone for fresh status once when what we have is old.
  const sessionUpdatedMs = toMillis(session?.updatedAt);
  const sessionEnabled = session?.enabled !== false && !!session;
  useEffect(() => {
    if (!code || !visible || !sessionEnabled) return;
    const nowMs = Date.now();
    if (sessionUpdatedMs !== null && nowMs - sessionUpdatedMs < PING_IF_OLDER_MS) return;
    if (pingSentAt !== null && nowMs - pingSentAt < 60_000) return;
    setPingSentAt(nowMs);
    (async () => {
      try {
        const db = await getFirestoreDb();
        const { doc, setDoc, serverTimestamp } = await import("firebase/firestore");
        await setDoc(doc(db, "remote_commands", code), {
          cmd: "ping",
          cmdId: ("p" + Math.random().toString(36).slice(2, 10) + Date.now().toString(36)).slice(0, 36),
          cmdAt: serverTimestamp(),
        });
      } catch {
        // Someone pinged in the last minute (one a minute is allowed); their answer updates this page too.
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, visible, sessionEnabled, sessionUpdatedMs]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setInterval(() => setCooldown((p) => Math.max(0, p - 1)), 1000);
    return () => clearInterval(t);
  }, [cooldown]);

  useEffect(() => {
    if (!actionSuccess) return;
    const t = setTimeout(() => setActionSuccess(null), 6000);
    return () => clearTimeout(t);
  }, [actionSuccess]);

  async function send(cmd: Command, arg?: string) {
    if (!code || cooldown > 0 || actionLoading) return;
    setActionLoading(cmd);
    setActionSuccess(null);
    try {
      const db = await getFirestoreDb();
      const { doc, setDoc, serverTimestamp } = await import("firebase/firestore");
      await setDoc(doc(db, "remote_commands", code), {
        cmd,
        cmdId: (Math.random().toString(36).substring(2, 12) + Date.now().toString(36)).slice(0, 36),
        cmdAt: serverTimestamp(),
        ...(arg ? { arg } : {}),
      });
      setCooldown(30);
      setActionSuccess(
        cmd === "start"
          ? "Starting your server. It usually takes about a minute."
          : cmd === "stop"
            ? "Stopping your server."
            : cmd === "restart"
              ? "Restarting your server."
              : cmd === "players"
                ? "Getting player positions..."
                : `Teleport sent: ${arg?.replace(" ", " → ")}`
      );
    } catch (err: any) {
      const denied = err?.code === "permission-denied" || String(err?.message).includes("permission-denied");
      setErrorStatus(denied ? "Please wait 30 seconds between commands." : "Couldn't send that. Try again.");
      setTimeout(() => setErrorStatus(null), 5000);
    } finally {
      setActionLoading(null);
    }
  }

  async function copyAddress(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard blocked; the address is on screen.
    }
  }

  const lastSeenMs = toMillis(session?.updatedAt);
  const answeredPing = pingSentAt !== null && lastSeenMs !== null && lastSeenMs >= pingSentAt - 5000;
  const checkingPhone = pingSentAt !== null && !answeredPing && now - pingSentAt <= PING_WAIT_MS;
  const phoneAway = pingSentAt !== null && !answeredPing && now - pingSentAt > PING_WAIT_MS;

  const state = (session?.state || "OFFLINE").toUpperCase();
  const isOnline = state === "ONLINE";
  const isStarting = state === "STARTING";
  const isSleeping = state === "SLEEPING";
  const startedAtMs = toMillis(session?.startedAt);
  const javaAddress = session?.address ? (session.port ? `${session.address}:${session.port}` : session.address) : null;
  const health = session?.health;
  const tps = typeof session?.tpsTenths === "number" ? session.tpsTenths / 10 : null;
  const names = (session?.playerNames || []).filter(Boolean);
  const isFreeTier = session?.tier === "free";
  const showAdminPanel = !!adminKey && session?.tier === "premium";
  const busy = cooldown > 0 || actionLoading !== null;

  const card = isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/[0.08]";
  const soft = isDark ? "bg-white/[0.04]" : "bg-black/[0.03]";
  const muted = isDark ? "text-white/55" : "text-black/55";
  const faint = isDark ? "text-white/35" : "text-black/40";

  const status = isOnline
    ? { label: "Online", dot: "bg-emerald-400", ring: "ring-emerald-400/30", text: "text-emerald-500", pulse: true }
    : isStarting
      ? { label: "Starting", dot: "bg-amber-400", ring: "ring-amber-400/30", text: "text-amber-500", pulse: true }
      : isSleeping
        ? { label: "Sleeping", dot: "bg-indigo-400", ring: "ring-indigo-400/30", text: "text-indigo-400", pulse: false }
        : { label: "Offline", dot: "bg-zinc-400", ring: "ring-zinc-400/25", text: isDark ? "text-white/60" : "text-black/55", pulse: false };

  if (loading) {
    return (
      <div className={`rounded-3xl border p-10 text-center ${card}`}>
        <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin mx-auto mb-4" />
        <p className={`text-sm ${muted}`}>Connecting to your phone...</p>
      </div>
    );
  }

  if (!session) {
    return (
      <div className={`rounded-3xl border p-6 ${isDark ? "bg-red-950/20 border-red-500/30 text-red-200" : "bg-red-50 border-red-200 text-red-800"}`}>
        <div className="flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
          <p className="text-sm">{errorStatus || "This server isn't available."}</p>
        </div>
      </div>
    );
  }

  const tile = `rounded-2xl p-3.5 ${soft}`;
  const label = `flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider ${faint}`;

  return (
    <div className="space-y-4">
      {/* Status card: name, state, and the one button most people came for */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        className={`rounded-3xl border p-5 sm:p-7 shadow-sm ${card}`}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            {subtitle && (
              <div className={`flex items-center gap-1.5 text-xs font-medium mb-1 ${faint}`}>
                <Smartphone className="w-3.5 h-3.5" /> {subtitle}
              </div>
            )}
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight break-words">
              {session.serverName || "Minecraft Server"}
            </h2>
            <div className="mt-2 flex items-center gap-2.5 flex-wrap">
              <span className={`inline-flex items-center gap-2 text-sm font-bold ${status.text}`}>
                <span className={`relative w-2.5 h-2.5 rounded-full ${status.dot} ring-4 ${status.ring}`}>
                  {status.pulse && <span className={`absolute inset-0 rounded-full ${status.dot} animate-ping opacity-60`} />}
                </span>
                {status.label}
              </span>
              {isOnline && (
                <span className={`inline-flex items-center gap-1 text-sm ${muted}`}>
                  <Users className="w-4 h-4" />
                  {session.players ?? names.length}
                  {typeof session.maxPlayers === "number" ? ` / ${session.maxPlayers}` : ""} playing
                </span>
              )}
            </div>
          </div>
          <div className={`text-right text-xs shrink-0 ${faint}`}>
            <div className="font-semibold uppercase tracking-wider">Phone</div>
            <div className="mt-1 flex items-center gap-1.5 justify-end">
              <span className={`w-1.5 h-1.5 rounded-full ${phoneAway ? "bg-amber-500" : checkingPhone ? "bg-zinc-400 animate-pulse" : "bg-emerald-400"}`} />
              {checkingPhone ? "checking..." : phoneAway ? "not answering" : lastSeenMs ? formatAgo(lastSeenMs, now) : "unknown"}
            </div>
          </div>
        </div>

        {phoneAway && (
          <div className={`mt-4 flex items-start gap-2.5 rounded-2xl p-3.5 text-sm ${isDark ? "bg-amber-500/10 text-amber-200" : "bg-amber-50 text-amber-800"}`}>
            <WifiOff className="w-4 h-4 mt-0.5 shrink-0 text-amber-500" />
            <span>
              The phone isn't answering. It may be off, offline, or PocketHost was closed by the phone.
              {lastSeenMs ? ` Last heard ${formatAgo(lastSeenMs, now)}.` : ""}
            </span>
          </div>
        )}

        {/* Main actions */}
        <div className="mt-5">
          {isOnline || isStarting ? (
            <div className={`grid ${isFreeTier ? "grid-cols-1" : "grid-cols-2"} gap-2.5`}>
              <button
                type="button"
                onClick={() => send("stop")}
                disabled={busy}
                className={`h-14 rounded-2xl font-bold text-base flex items-center justify-center gap-2 transition-all ${
                  busy ? `${soft} ${faint} cursor-not-allowed` : "bg-rose-600 hover:bg-rose-500 text-white active:scale-[0.98]"
                }`}
              >
                {actionLoading === "stop" ? <Spinner /> : <Square className="w-4 h-4 fill-current" />}
                Stop server
              </button>
              {!isFreeTier && (
                <button
                  type="button"
                  onClick={() => send("restart")}
                  disabled={busy || !isOnline}
                  className={`h-14 rounded-2xl font-bold text-base flex items-center justify-center gap-2 border transition-all ${
                    busy || !isOnline
                      ? `${soft} ${faint} border-transparent cursor-not-allowed`
                      : isDark ? "border-white/15 hover:bg-white/5" : "border-black/15 hover:bg-black/5"
                  }`}
                >
                  {actionLoading === "restart" ? <Spinner /> : <RotateCw className="w-4 h-4" />}
                  Restart
                </button>
              )}
            </div>
          ) : (
            <button
              type="button"
              onClick={() => send("start")}
              disabled={busy}
              className={`w-full h-14 rounded-2xl font-extrabold text-base flex items-center justify-center gap-2 transition-all ${
                busy ? `${soft} ${faint} cursor-not-allowed` : "bg-[#7FE620] hover:bg-[#72d01d] text-black shadow-lg shadow-[#7FE620]/25 active:scale-[0.98]"
              }`}
            >
              {actionLoading === "start" ? <Spinner dark /> : <Play className="w-5 h-5 fill-current" />}
              Start server
            </button>
          )}
          <AnimatePresence>
            {(actionSuccess || cooldown > 0 || errorStatus) && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                className={`mt-3 text-sm flex items-center justify-between gap-3 ${errorStatus ? "text-rose-500" : muted}`}
              >
                <span className="flex items-center gap-1.5 min-w-0">
                  {errorStatus ? <AlertTriangle className="w-4 h-4 shrink-0" /> : actionSuccess ? <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-500" /> : null}
                  <span>{errorStatus || actionSuccess || ""}</span>
                </span>
                {cooldown > 0 && <span className={`shrink-0 text-xs font-mono ${faint}`}>next in {cooldown}s</span>}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>

      {/* Join address and details */}
      <div className={`rounded-3xl border p-4 sm:p-5 ${card}`}>
        {javaAddress ? (
          <button
            type="button"
            onClick={() => copyAddress(javaAddress)}
            className={`w-full text-left rounded-2xl p-4 flex items-center justify-between gap-3 transition-colors ${soft} hover:ring-2 hover:ring-[#7FE620]/40`}
          >
            <span className="min-w-0">
              <span className={label}>Join address</span>
              <span className="block font-mono font-bold text-[15px] sm:text-lg mt-1 break-words">
                {session.port ? (
                  <>
                    {session.address}
                    <wbr />:{session.port}
                  </>
                ) : (
                  javaAddress
                )}
              </span>
              {session.port ? (
                <span className={`block text-xs mt-1 ${muted}`}>Bedrock: {session.address} · port {session.port}</span>
              ) : null}
            </span>
            <span className={`shrink-0 inline-flex items-center gap-1.5 text-sm font-bold ${isDark ? "text-[#7FE620]" : "text-[#4c9a0c]"}`}>
              {copied ? <CheckCircle2 className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              {copied ? "Copied" : "Copy"}
            </span>
          </button>
        ) : (
          <div className={`rounded-2xl p-4 ${soft}`}>
            <span className={label}>Join address</span>
            <span className={`block text-sm mt-1 ${muted}`}>Shows here once the server has been online.</span>
          </div>
        )}

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mt-2.5">
          <div className={tile}>
            <span className={label}><Tag className="w-3 h-3" /> Version</span>
            <span className="block font-bold text-sm mt-1">{session.version || "—"}</span>
          </div>
          <div className={tile}>
            <span className={label}><Timer className="w-3 h-3" /> Uptime</span>
            <span className="block font-bold text-sm mt-1">{isOnline && startedAtMs ? formatDuration(now - startedAtMs) : "—"}</span>
          </div>
          <div className={tile}>
            <span className={label}><Gauge className="w-3 h-3" /> TPS</span>
            <span className={`block font-bold text-sm mt-1 ${tps !== null && tps < 15 ? "text-amber-500" : ""}`}>
              {isOnline && tps !== null ? tps.toFixed(1) : "—"}
            </span>
          </div>
          <div className={tile}>
            <span className={label}><Users className="w-3 h-3" /> Players</span>
            <span className="block font-bold text-sm mt-1">
              {isOnline ? `${session.players ?? names.length}${typeof session.maxPlayers === "number" ? ` / ${session.maxPlayers}` : ""}` : "—"}
            </span>
          </div>
        </div>

        {health && !phoneAway && (
          <div className={`mt-2.5 rounded-2xl px-4 py-3 flex items-center justify-between gap-3 text-sm ${soft}`}>
            <span className={`text-xs font-semibold uppercase tracking-wider ${faint}`}>Phone</span>
            <span className="flex items-center gap-4 flex-wrap justify-end">
              <span className="inline-flex items-center gap-1">
                <Thermometer className={`w-4 h-4 ${(health.tempTenths ?? 0) >= 450 ? "text-rose-500" : (health.tempTenths ?? 0) >= 380 ? "text-amber-500" : "text-[#7FE620]"}`} />
                {typeof health.tempTenths === "number" ? `${(health.tempTenths / 10).toFixed(0)}°C` : "—"}
              </span>
              <span className="inline-flex items-center gap-1">
                {health.charging ? (
                  <BatteryCharging className="w-4 h-4 text-[#7FE620]" />
                ) : (
                  <Battery className={`w-4 h-4 ${(health.battery ?? 100) <= 20 ? "text-rose-500" : "text-[#7FE620]"}`} />
                )}
                {typeof health.battery === "number" ? `${health.battery}%` : "—"}
              </span>
              <span className="inline-flex items-center gap-1">
                <MemoryStick className={`w-4 h-4 ${(health.ramPct ?? 0) >= 90 ? "text-rose-500" : "text-[#7FE620]"}`} />
                {typeof health.ramPct === "number" ? `${health.ramPct}% RAM` : "—"}
              </span>
            </span>
          </div>
        )}

        {/* Who is playing. With the admin link, the Players tab below lists everyone who ever joined. */}
        {isOnline && !showAdminPanel && (
          <div className="mt-4">
            <div className="flex items-center justify-between mb-2">
              <span className={label}><Users className="w-3 h-3" /> Online now</span>
              {!isFreeTier && names.length > 0 && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => send("players")}
                  className={`text-xs font-semibold inline-flex items-center gap-1 ${busy ? "opacity-40 cursor-not-allowed" : "text-[#5ab30f] hover:underline"}`}
                >
                  <RefreshCw className="w-3 h-3" /> Positions
                </button>
              )}
            </div>
            {names.length === 0 ? (
              <p className={`text-sm ${muted}`}>Nobody is playing right now.</p>
            ) : (
              <div className="space-y-1.5">
                {names.map((name) => {
                  const det = (session.playerDetails || []).find((p) => p.n === name);
                  const others = names.filter((n) => n !== name);
                  return (
                    <div key={name} className={`rounded-xl px-3 py-2.5 ${soft}`}>
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <Avatar name={name} />
                          <div className="min-w-0">
                            <div className="font-semibold text-sm truncate">{name}</div>
                            {det && typeof det.x === "number" && (
                              <div className={`text-xs ${muted}`}>
                                {DIMENSION_LABEL[det.d || ""] || det.d || "World"} · {det.x}, {det.y}, {det.z}
                                {typeof det.hp === "number" ? ` · ❤ ${det.hp}` : ""}
                              </div>
                            )}
                          </div>
                        </div>
                        {!isFreeTier && others.length > 0 && (
                          <button
                            type="button"
                            onClick={() => {
                              setTpFrom(tpFrom === name ? null : name);
                              setTpTo(others[0]);
                            }}
                            className="shrink-0 inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-[#7FE620]/15 text-[#4c9a0c] hover:bg-[#7FE620]/25"
                          >
                            <Navigation className="w-3.5 h-3.5" /> Teleport
                          </button>
                        )}
                      </div>
                      {tpFrom === name && (
                        <div className="mt-2.5 flex items-center gap-2">
                          <span className={`text-xs ${muted}`}>to</span>
                          <select
                            value={tpTo}
                            onChange={(e) => setTpTo(e.target.value)}
                            className={`flex-1 min-w-0 text-sm px-2.5 py-1.5 rounded-lg border outline-none ${isDark ? "bg-[#141414] border-white/15" : "bg-white border-black/15"}`}
                          >
                            {others.map((o) => <option key={o} value={o}>{o}</option>)}
                          </select>
                          <button
                            type="button"
                            disabled={busy || !tpTo}
                            onClick={() => {
                              send("tp", `${name} ${tpTo}`);
                              setTpFrom(null);
                            }}
                            className={`text-xs font-bold px-3 py-1.5 rounded-lg ${busy ? "opacity-40" : "bg-[#7FE620] text-black hover:bg-[#72d01d]"}`}
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
          </div>
        )}
      </div>

      {adminKey && !showAdminPanel && (
        <div className={`rounded-3xl border p-4 flex items-start gap-3 text-sm ${card}`}>
          <Crown className="w-5 h-5 text-amber-500 shrink-0" />
          <span className={muted}>
            Full control (console, players, plugins, settings, files) needs PocketHost Premium on the phone.
            Until then you can start and stop the server here.
          </span>
        </div>
      )}
      {showAdminPanel && (
        <RemoteAdminPanel adminKey={adminKey} isDark={isDark} getDb={getFirestoreDb} serverOnline={isOnline} players={names} />
      )}

      {isFreeTier && !adminKey && (
        <p className={`text-xs text-center px-4 ${faint}`}>
          This link can start and stop the server. Restart, player tools and full control come with PocketHost Premium.
        </p>
      )}
    </div>
  );
}

function Spinner({ dark }: { dark?: boolean }) {
  return <span className={`w-4 h-4 rounded-full border-2 ${dark ? "border-black" : "border-current"} border-t-transparent animate-spin`} />;
}
