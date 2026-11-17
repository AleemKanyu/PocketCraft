import { useCallback, useEffect, useRef, useState } from "react";
import {
  Terminal,
  Puzzle,
  SlidersHorizontal,
  FolderOpen,
  Wrench,
  Folder,
  FileText,
  ArrowLeft,
  RefreshCw,
  Trash2,
  Power,
  Save,
  Send,
  Archive,
  Users,
} from "lucide-react";

/**
 * Premium full control. Commands go to remote_admin_in/{adminKey} and the phone answers on
 * remote_admin_out/{adminKey}. The key is in the admin link only; the phone checks Premium
 * itself before it runs anything.
 */

interface Reply {
  ok: boolean;
  msg: string;
  data?: string;
}

type Tab = "console" | "players" | "plugins" | "settings" | "files" | "tools";

const REPLY_WAIT_MS = 45_000;
// The rules allow one admin command every 2 seconds.
const MIN_GAP_MS = 2_100;

async function sendAdmin(
  getDb: () => Promise<any>,
  key: string,
  cmd: string,
  arg?: string,
  body?: string
): Promise<Reply> {
  const db = await getDb();
  const { doc, onSnapshot, setDoc, serverTimestamp } = await import("firebase/firestore");
  const cmdId = (Math.random().toString(36).slice(2, 12) + Date.now().toString(36)).slice(0, 36);
  return new Promise<Reply>((resolve) => {
    let done = false;
    let unsub: (() => void) | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const finish = (r: Reply) => {
      if (done) return;
      done = true;
      if (timer) clearTimeout(timer);
      unsub?.();
      resolve(r);
    };
    unsub = onSnapshot(
      doc(db, "remote_admin_out", key),
      (snap: any) => {
        const d = snap.exists() ? snap.data() : null;
        if (d && d.cmdId === cmdId) finish({ ok: !!d.ok, msg: d.msg || "", data: d.data });
      },
      () => finish({ ok: false, msg: "Couldn't listen for the answer. Check your connection." })
    );
    timer = setTimeout(
      () => finish({ ok: false, msg: "The phone didn't answer. Make sure PocketHost is running and online." }),
      REPLY_WAIT_MS
    );
    setDoc(doc(db, "remote_admin_in", key), {
      cmd,
      cmdId,
      cmdAt: serverTimestamp(),
      ...(arg ? { arg } : {}),
      ...(body ? { body } : {}),
    }).catch((err: any) => {
      const denied = err?.code === "permission-denied" || String(err?.message).includes("permission-denied");
      finish({
        ok: false,
        msg: denied
          ? "Rejected. Wait 2 seconds between commands, and check that this admin link is current."
          : "Couldn't send the command. Try again.",
      });
    });
  });
}

function parseJson<T>(text: string | undefined, fallback: T): T {
  if (!text) return fallback;
  try {
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

const BOOLEAN_SETTINGS: Record<string, string> = {
  "white-list": "Whitelist",
  "enforce-whitelist": "Enforce whitelist",
  pvp: "PvP",
  "allow-flight": "Allow flight",
  hardcore: "Hardcore",
  "force-gamemode": "Force game mode",
  "spawn-monsters": "Spawn monsters",
  "spawn-animals": "Spawn animals",
  "spawn-npcs": "Spawn villagers",
  "allow-nether": "Allow the Nether",
  "generate-structures": "Generate structures",
  "enable-command-block": "Command blocks",
};
const ENUM_SETTINGS: Record<string, { label: string; options: string[] }> = {
  difficulty: { label: "Difficulty", options: ["peaceful", "easy", "normal", "hard"] },
  gamemode: { label: "Default game mode", options: ["survival", "creative", "adventure", "spectator"] },
};

interface Props {
  adminKey: string;
  isDark: boolean;
  getDb: () => Promise<any>;
  serverOnline: boolean;
  players?: string[];
}

export default function RemoteAdminPanel({ adminKey, isDark, getDb, serverOnline, players }: Props) {
  const [tab, setTab] = useState<Tab>("console");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Reply | null>(null);
  // Commands wait their turn instead of failing, and go out at least 2 s apart (the Firestore
  // rules reject a closer one). Opening a tab while another command runs used to show an error.
  const queueRef = useRef<Promise<unknown>>(Promise.resolve());
  const lastSentRef = useRef(0);
  const pendingRef = useRef(0);

  const run = useCallback(
    (cmd: string, arg?: string, body?: string): Promise<Reply> => {
      pendingRef.current += 1;
      setBusy(true);
      const next = queueRef.current.then(async () => {
        const wait = lastSentRef.current + MIN_GAP_MS - Date.now();
        if (wait > 0) await new Promise((r) => setTimeout(r, wait));
        lastSentRef.current = Date.now();
        return sendAdmin(getDb, adminKey, cmd, arg, body);
      });
      queueRef.current = next.catch(() => undefined);
      return next.finally(() => {
        pendingRef.current -= 1;
        if (pendingRef.current === 0) setBusy(false);
      });
    },
    [getDb, adminKey]
  );

  const card = isDark ? "bg-white/[0.03] border-white/10" : "bg-black/[0.02] border-black/10";
  const muted = isDark ? "text-white/50" : "text-black/50";
  const input = `w-full px-3 py-2 rounded-lg border text-sm outline-none ${
    isDark ? "bg-white/5 border-white/10 text-white focus:border-[#7FE620]" : "bg-black/5 border-black/10 text-black focus:border-[#5ab30f]"
  }`;
  const btn = `px-3 py-2 rounded-lg text-sm font-semibold flex items-center gap-1.5 transition-all ${
    busy
      ? isDark ? "bg-white/5 text-white/30 cursor-not-allowed" : "bg-black/5 text-black/30 cursor-not-allowed"
      : "bg-[#7FE620] hover:bg-[#72d01d] text-black active:scale-[0.98]"
  }`;
  const ghost = `px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 border transition-all ${
    busy
      ? isDark ? "border-white/5 text-white/30 cursor-not-allowed" : "border-black/5 text-black/30 cursor-not-allowed"
      : isDark ? "border-white/10 hover:bg-white/10" : "border-black/10 hover:bg-black/10"
  }`;
  const danger = `px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-1.5 border transition-all ${
    busy ? "border-white/5 text-white/30 cursor-not-allowed" : "border-rose-500/40 text-rose-500 hover:bg-rose-500/10"
  }`;

  const tabs: { id: Tab; label: string; icon: any }[] = [
    { id: "console", label: "Console", icon: Terminal },
    { id: "players", label: "Players", icon: Users },
    { id: "plugins", label: "Plugins", icon: Puzzle },
    { id: "settings", label: "Settings", icon: SlidersHorizontal },
    { id: "files", label: "Files", icon: FolderOpen },
    { id: "tools", label: "Tools", icon: Wrench },
  ];

  return (
    <div className={`mb-6 rounded-xl border ${card}`}>
      <div className={`px-3.5 pt-3.5 pb-2 text-[11px] uppercase tracking-wider font-semibold ${muted}`}>
        Premium full control
      </div>
      <div className="px-2.5 flex gap-1 overflow-x-auto">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            onClick={() => {
              setTab(id);
              setNotice(null);
            }}
            className={`px-3 py-2 rounded-lg text-xs sm:text-sm font-semibold flex items-center gap-1.5 whitespace-nowrap ${
              tab === id
                ? "bg-[#7FE620]/15 text-[#7FE620]"
                : isDark ? "text-white/60 hover:bg-white/5" : "text-black/60 hover:bg-black/5"
            }`}
          >
            <Icon className="w-4 h-4" />
            {label}
          </button>
        ))}
      </div>

      <div className="p-3.5">
        {notice && (
          <div
            className={`mb-3 p-2.5 rounded-lg text-xs border ${
              notice.ok
                ? "bg-emerald-500/10 border-emerald-500/20 text-emerald-500"
                : "bg-rose-500/10 border-rose-500/20 text-rose-500"
            }`}
          >
            {notice.msg}
          </div>
        )}
        {busy && <div className={`mb-3 text-xs ${muted}`}>Waiting for the phone...</div>}

        {tab === "console" && <ConsoleTab run={run} input={input} btn={btn} muted={muted} serverOnline={serverOnline} isDark={isDark} setNotice={setNotice} />}
        {tab === "players" && <PlayersTab run={run} ghost={ghost} muted={muted} isDark={isDark} setNotice={setNotice} players={players} />}
        {tab === "plugins" && <PluginsTab run={run} ghost={ghost} danger={danger} muted={muted} isDark={isDark} setNotice={setNotice} />}
        {tab === "settings" && <SettingsTab run={run} input={input} btn={btn} ghost={ghost} muted={muted} setNotice={setNotice} />}
        {tab === "files" && <FilesTab run={run} input={input} btn={btn} ghost={ghost} muted={muted} isDark={isDark} setNotice={setNotice} />}
        {tab === "tools" && <ToolsTab run={run} ghost={ghost} danger={danger} muted={muted} serverOnline={serverOnline} setNotice={setNotice} />}
      </div>
    </div>
  );
}

type Run = (cmd: string, arg?: string, body?: string) => Promise<Reply>;
type SetNotice = (r: Reply | null) => void;

function ConsoleTab({ run, input, btn, muted, serverOnline, isDark, setNotice }: any) {
  const [line, setLine] = useState("");
  const [log, setLog] = useState<{ cmd: string; out: string; ok: boolean }[]>([]);
  async function send() {
    const cmd = line.trim().replace(/^\//, "");
    if (!cmd) return;
    setLine("");
    setNotice(null);
    const r: Reply = await (run as Run)("console", cmd);
    setLog((prev) => [...prev.slice(-29), { cmd, out: r.ok ? r.data || r.msg : r.msg, ok: r.ok }]);
  }
  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
        className="flex gap-2"
      >
        <input
          className={input}
          value={line}
          onChange={(e) => setLine(e.target.value)}
          placeholder={serverOnline ? "say hello, op Steve, whitelist add Alex..." : "Start the server to use the console"}
          maxLength={256}
          autoCapitalize="off"
          autoCorrect="off"
        />
        <button type="submit" className={btn}>
          <Send className="w-4 h-4" /> Send
        </button>
      </form>
      <div className={`mt-3 space-y-2 max-h-72 overflow-y-auto font-mono text-xs ${isDark ? "text-white/80" : "text-black/80"}`}>
        {log.length === 0 && <div className={muted}>Replies from the server show here.</div>}
        {[...log].reverse().map((e, i) => (
          <div key={i}>
            <div className="text-[#7FE620]">&gt; {e.cmd}</div>
            <pre className={`whitespace-pre-wrap break-words ${e.ok ? "" : "text-rose-500"}`}>{e.out}</pre>
          </div>
        ))}
      </div>
    </div>
  );
}

interface PluginItem {
  k: string;
  n: string;
  on: boolean;
  s: number;
}

function PluginsTab({ run, ghost, danger, muted, isDark, setNotice }: any) {
  const [items, setItems] = useState<PluginItem[] | null>(null);
  const load = useCallback(async () => {
    const r: Reply = await (run as Run)("plugins");
    if (r.ok) setItems(parseJson<PluginItem[]>(r.data, []));
    else (setNotice as SetNotice)(r);
  }, [run, setNotice]);
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  async function act(cmd: string, p: PluginItem) {
    if (cmd === "plugin_delete" && !window.confirm(`Delete ${p.n}? This can't be undone.`)) return;
    const r: Reply = await (run as Run)(cmd, `${p.k}/${p.n}`);
    (setNotice as SetNotice)(r);
    if (r.ok) await load();
  }
  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <span className={`text-xs ${muted}`}>Changes apply after a restart.</span>
        <button type="button" className={ghost} onClick={() => void load()}>
          <RefreshCw className="w-4 h-4" /> Refresh
        </button>
      </div>
      {items === null && <div className={`text-sm ${muted}`}>Loading...</div>}
      {items && items.length === 0 && <div className={`text-sm ${muted}`}>No plugins or mods found.</div>}
      <div className="space-y-2">
        {items?.map((p) => (
          <div key={`${p.k}/${p.n}`} className={`flex items-center justify-between gap-2 p-2.5 rounded-lg border ${isDark ? "border-white/10" : "border-black/10"}`}>
            <div className="min-w-0">
              <div className={`text-sm font-medium truncate ${p.on ? "" : "line-through opacity-60"}`}>{p.n.replace(/\.disabled$/, "")}</div>
              <div className={`text-[11px] ${muted}`}>
                {p.k === "mods" ? "Mod" : "Plugin"} · {formatSize(p.s)} · {p.on ? "On" : "Off"}
              </div>
            </div>
            <div className="flex gap-1.5 shrink-0">
              <button type="button" className={ghost} onClick={() => void act("plugin_toggle", p)}>
                <Power className="w-4 h-4" /> {p.on ? "Turn off" : "Turn on"}
              </button>
              <button type="button" className={danger} onClick={() => void act("plugin_delete", p)} aria-label={`Delete ${p.n}`}>
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>
      <div className={`mt-3 text-[11px] ${muted}`}>
        Adding new plugins or mods isn't done from the website. Add them in the PocketHost app.
      </div>
    </div>
  );
}

function SettingsTab({ run, input, btn, ghost, muted, setNotice }: any) {
  const [values, setValues] = useState<Record<string, string> | null>(null);
  const load = useCallback(async () => {
    const r: Reply = await (run as Run)("props_get");
    if (r.ok) setValues(parseJson<Record<string, string>>(r.data, {}));
    else (setNotice as SetNotice)(r);
  }, [run, setNotice]);
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  async function save(key: string, value: string) {
    const r: Reply = await (run as Run)("props_set", key, value);
    (setNotice as SetNotice)(r);
    if (r.ok) setValues((prev) => ({ ...(prev || {}), [key]: value }));
  }
  if (values === null) return <div className={`text-sm ${muted}`}>Loading...</div>;
  const row = "flex items-center justify-between gap-3 py-2";
  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className={`text-xs ${muted}`}>Saved settings apply after a restart.</span>
        <button type="button" className={ghost} onClick={() => void load()}>
          <RefreshCw className="w-4 h-4" /> Refresh
        </button>
      </div>
      <div className="divide-y divide-white/5">
        <TextSetting label="Max players" k="max-players" values={values} save={save} input={input} btn={btn} row={row} type="number" />
        <TextSetting label="Message of the day" k="motd" values={values} save={save} input={input} btn={btn} row={row} />
        <TextSetting label="Spawn protection" k="spawn-protection" values={values} save={save} input={input} btn={btn} row={row} type="number" />
        {Object.entries(ENUM_SETTINGS).map(([k, { label, options }]) => (
          <div key={k} className={row}>
            <span className="text-sm">{label}</span>
            <select className={`${input} !w-40`} value={values[k] ?? ""} onChange={(e) => void save(k, e.target.value)}>
              {values[k] === undefined && <option value="">-</option>}
              {options.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
        ))}
        {Object.entries(BOOLEAN_SETTINGS).map(([k, label]) => (
          <div key={k} className={row}>
            <span className="text-sm">{label}</span>
            <select className={`${input} !w-28`} value={values[k] ?? ""} onChange={(e) => void save(k, e.target.value)}>
              {values[k] === undefined && <option value="">-</option>}
              <option value="true">On</option>
              <option value="false">Off</option>
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}

function TextSetting({ label, k, values, save, input, btn, row, type }: any) {
  const [text, setText] = useState<string>(values[k] ?? "");
  useEffect(() => setText(values[k] ?? ""), [values, k]);
  return (
    <div className={row}>
      <span className="text-sm shrink-0">{label}</span>
      <div className="flex gap-2 min-w-0">
        <input className={`${input} !w-40 sm:!w-56`} type={type || "text"} value={text} onChange={(e: any) => setText(e.target.value)} maxLength={120} />
        <button type="button" className={btn} onClick={() => void save(k, text)} aria-label={`Save ${label}`}>
          <Save className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

interface FileItem {
  n: string;
  d: boolean;
  s: number;
  e: boolean;
}

function FilesTab({ run, input, btn, ghost, muted, isDark, setNotice }: any) {
  const [path, setPath] = useState<string>("");
  const [items, setItems] = useState<FileItem[] | null>(null);
  const [openFile, setOpenFile] = useState<string | null>(null);
  const [text, setText] = useState<string>("");
  const [dirty, setDirty] = useState(false);

  const load = useCallback(
    async (p: string) => {
      const r: Reply = await (run as Run)("file_list", p);
      (setNotice as SetNotice)(r.ok && !r.msg ? null : r);
      if (r.ok) {
        setItems(parseJson<FileItem[]>(r.data, []));
        setPath(p);
      }
    },
    [run, setNotice]
  );
  useEffect(() => {
    void load("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function open(name: string) {
    const full = path ? `${path}/${name}` : name;
    const r: Reply = await (run as Run)("file_read", full);
    if (!r.ok) return (setNotice as SetNotice)(r);
    (setNotice as SetNotice)(null);
    setOpenFile(full);
    setText(r.data ?? "");
    setDirty(false);
  }
  async function save() {
    if (!openFile) return;
    const r: Reply = await (run as Run)("file_write", openFile, text);
    (setNotice as SetNotice)(r);
    if (r.ok) setDirty(false);
  }
  const up = () => load(path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "");

  if (openFile) {
    return (
      <div>
        <div className="flex items-center justify-between gap-2 mb-2">
          <button type="button" className={ghost} onClick={() => { if (!dirty || window.confirm("Discard your changes?")) setOpenFile(null); }}>
            <ArrowLeft className="w-4 h-4" /> Back
          </button>
          <span className={`text-xs truncate ${muted}`}>{openFile}</span>
          <button type="button" className={btn} disabled={!dirty} onClick={() => void save()}>
            <Save className="w-4 h-4" /> Save
          </button>
        </div>
        <textarea
          className={`${input} font-mono text-xs h-72`}
          value={text}
          spellCheck={false}
          onChange={(e) => { setText(e.target.value); setDirty(true); }}
        />
        <div className={`mt-2 text-[11px] ${muted}`}>
          The old version is kept next to the file as .bak-remote. Restart the server after editing.
        </div>
      </div>
    );
  }
  return (
    <div>
      <div className="flex items-center justify-between gap-2 mb-2">
        <button type="button" className={ghost} disabled={path === ""} onClick={() => void up()}>
          <ArrowLeft className="w-4 h-4" /> Up
        </button>
        <span className={`text-xs truncate ${muted}`}>/{path}</span>
        <button type="button" className={ghost} onClick={() => void load(path)}>
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>
      {items === null && <div className={`text-sm ${muted}`}>Loading...</div>}
      <div className="space-y-1">
        {items?.map((f) => (
          <button
            key={f.n}
            type="button"
            disabled={!f.d && !f.e}
            onClick={() => (f.d ? void load(path ? `${path}/${f.n}` : f.n) : void open(f.n))}
            className={`w-full flex items-center justify-between gap-2 p-2 rounded-lg text-left text-sm border ${
              isDark ? "border-white/5" : "border-black/5"
            } ${!f.d && !f.e ? "opacity-40 cursor-default" : isDark ? "hover:bg-white/5" : "hover:bg-black/5"}`}
          >
            <span className="flex items-center gap-2 min-w-0">
              {f.d ? <Folder className="w-4 h-4 text-[#7FE620] shrink-0" /> : <FileText className="w-4 h-4 shrink-0" />}
              <span className="truncate">{f.n}</span>
            </span>
            {!f.d && <span className={`text-[11px] shrink-0 ${muted}`}>{formatSize(f.s)}</span>}
          </button>
        ))}
      </div>
      <div className={`mt-3 text-[11px] ${muted}`}>
        Only text files (yml, json, txt, properties...) under 100 KB can be opened. server.properties is changed in Settings.
      </div>
    </div>
  );
}

function ToolsTab({ run, ghost, danger, muted, serverOnline, setNotice }: any) {
  async function act(cmd: string, confirmText?: string) {
    if (confirmText && !window.confirm(confirmText)) return;
    (setNotice as SetNotice)(await (run as Run)(cmd));
  }
  return (
    <div className="flex flex-wrap gap-2">
      <button type="button" className={ghost} onClick={() => void act("status")}>
        <RefreshCw className="w-4 h-4" /> Refresh status
      </button>
      <button type="button" className={danger} disabled={!serverOnline} onClick={() => void act("restart", "Restart the server? Players will be disconnected.")}>
        <Power className="w-4 h-4" /> Restart
      </button>
      <button type="button" className={ghost} onClick={() => void act("backup", "Make a world backup now? The server must be stopped.")}>
        <Archive className="w-4 h-4" /> Backup world
      </button>
      <div className={`w-full text-[11px] ${muted}`}>
        Backups are saved on the phone in Downloads/PocketHost. The server must be stopped first.
      </div>
    </div>
  );
}

interface Loc {
  x: number;
  y: number;
  z: number;
  d: string;
}
interface PlayerInfo {
  name: string;
  pos: Loc | null;
  respawn: Loc | null;
  respawnIsWorldSpawn?: boolean;
  death: Loc | null;
  hp?: number;
  inv: { s: number; i: string; c: number }[];
}

const DIMENSIONS: Record<string, string> = { overworld: "Overworld", the_nether: "Nether", the_end: "End" };

function prettyItem(id: string): string {
  return id
    .split("_")
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(" ");
}

function LocLine({ label, loc, muted, extra }: { label: string; loc: Loc | null; muted: string; extra?: string }) {
  return (
    <div className="flex items-center justify-between gap-2 text-sm">
      <span className={muted}>{label}</span>
      <span className="font-mono text-xs text-right break-words min-w-0">
        {loc ? `${loc.x}, ${loc.y}, ${loc.z} · ${DIMENSIONS[loc.d] || loc.d}${extra ? ` (${extra})` : ""}` : "Not set"}
      </span>
    </div>
  );
}

function PlayersTab({ run, ghost, muted, isDark, setNotice, players }: any) {
  const [selected, setSelected] = useState<string | null>(null);
  const [info, setInfo] = useState<PlayerInfo | null>(null);
  const names: string[] = (players || []).filter(Boolean);

  async function open(name: string) {
    setSelected(name);
    setInfo(null);
    const r: Reply = await (run as Run)("player_info", name);
    if (r.ok) {
      setInfo(parseJson<PlayerInfo | null>(r.data, null));
      (setNotice as SetNotice)(null);
    } else (setNotice as SetNotice)(r);
  }
  async function tp(cmd: "tp_respawn" | "tp_death") {
    if (!selected) return;
    const r: Reply = await (run as Run)(cmd, selected);
    (setNotice as SetNotice)(r);
    if (r.ok) await open(selected);
  }

  if (names.length === 0) return <div className={`text-sm ${muted}`}>Nobody is online right now.</div>;

  return (
    <div>
      <div className="flex flex-wrap gap-1.5 mb-3">
        {names.map((n) => (
          <button
            key={n}
            type="button"
            onClick={() => void open(n)}
            className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${
              selected === n ? "bg-[#7FE620]/15 text-[#7FE620] border-[#7FE620]/40" : isDark ? "border-white/10 hover:bg-white/5" : "border-black/10 hover:bg-black/5"
            }`}
          >
            {n}
          </button>
        ))}
      </div>
      {selected && !info && <div className={`text-sm ${muted}`}>Loading {selected}...</div>}
      {info && (
        <div className="space-y-3">
          <div className={`p-3 rounded-lg border space-y-1.5 ${isDark ? "border-white/10" : "border-black/10"}`}>
            <LocLine label="Location now" loc={info.pos} muted={muted} />
            <LocLine label="Respawn point" loc={info.respawn} muted={muted} extra={info.respawnIsWorldSpawn ? "world spawn" : undefined} />
            <LocLine label="Last death" loc={info.death} muted={muted} />
            {typeof info.hp === "number" && (
              <div className="flex items-center justify-between text-sm">
                <span className={muted}>Health</span>
                <span className="text-xs font-semibold">{info.hp} / 20</span>
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className={ghost} disabled={!info.respawn} onClick={() => void tp("tp_respawn")}>
              Teleport to respawn point
            </button>
            <button type="button" className={ghost} disabled={!info.death} onClick={() => void tp("tp_death")}>
              Teleport to last death
            </button>
            <button type="button" className={ghost} onClick={() => void open(info.name)}>
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
          <div className="min-w-0">
            <div className={`text-[11px] uppercase tracking-wider font-semibold mb-1.5 ${muted}`}>Inventory</div>
            <McInventory items={info.inv} />
            <div className={`mt-2 text-[10.5px] leading-snug ${muted}`}>
              Item pictures belong to Mojang. Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Item pictures are Mojang's textures, loaded from the PrismarineJS minecraft-assets mirror (the
// same source the app's own inventory screen uses). Set to false to show item initials instead.
const SHOW_ITEM_TEXTURES = true;
const ITEM_TEXTURE_BASE = "https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/1.21.11/";

type InvItem = { s: number; i: string; c: number };

function initials(id: string): string {
  return id
    .split("_")
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");
}

function ItemIcon({ id }: { id: string }) {
  // items/<id>.png first, then blocks/<id>.png, then the item's initials.
  const [stage, setStage] = useState(SHOW_ITEM_TEXTURES ? 0 : 2);
  if (stage >= 2) {
    return (
      <span style={{ fontSize: "2.6cqw", fontWeight: 800, color: "#3b3b3b", lineHeight: 1 }}>{initials(id)}</span>
    );
  }
  return (
    <img
      src={`${ITEM_TEXTURE_BASE}${stage === 0 ? "items" : "blocks"}/${id}.png`}
      alt={prettyItem(id)}
      onError={() => setStage((s) => s + 1)}
      style={{ width: "78%", height: "78%", imageRendering: "pixelated", objectFit: "contain" }}
    />
  );
}

const SLOT_STYLE: Record<string, string | number> = {
  position: "relative",
  aspectRatio: "1 / 1",
  background: "#8b8b8b",
  borderTop: "max(1px, 0.45cqw) solid #373737",
  borderLeft: "max(1px, 0.45cqw) solid #373737",
  borderRight: "max(1px, 0.45cqw) solid #ffffff",
  borderBottom: "max(1px, 0.45cqw) solid #ffffff",
  borderRadius: 0,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  minWidth: 0,
};

// Faint outlines for empty armor and off-hand slots, like the game shows.
const EMPTY_HINT: Record<number, string> = {
  103: "M6 10a6 6 0 0 1 12 0v4H6z",
  102: "M5 6l4-2h6l4 2-2 4v8H7v-8z",
  101: "M7 4h10v16h-4l-1-9-1 9H7z",
  100: "M5 13h5v6H5zM14 13h5v6h-5z",
  [-106]: "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z",
};

function Slot({ item, hint }: { item?: InvItem; hint?: number }) {
  return (
    <div style={SLOT_STYLE} title={item ? `${prettyItem(item.i)}${item.c > 1 ? ` x${item.c}` : ""}` : ""}>
      {item ? (
        <>
          <ItemIcon id={item.i} />
          {item.c > 1 && (
            <span
              style={{
                position: "absolute",
                right: "4%",
                bottom: "0%",
                fontSize: "max(8px, 2.9cqw)",
                fontWeight: 800,
                color: "#fff",
                textShadow: "0.3cqw 0.3cqw 0 #3f3f3f",
                lineHeight: 1,
              }}
            >
              {item.c}
            </span>
          )}
        </>
      ) : hint !== undefined && EMPTY_HINT[hint] ? (
        <svg viewBox="0 0 24 24" style={{ width: "60%", height: "60%", opacity: 0.35 }} fill="none" stroke="#373737" strokeWidth={1.6}>
          <path d={EMPTY_HINT[hint]} />
        </svg>
      ) : null}
    </div>
  );
}

/**
 * The player's inventory laid out like the game: armor on the left, off hand under the body
 * area, three rows of storage and the hotbar. Sizes come from the panel width (container query
 * units), so it fits a phone or a wide screen without scrolling sideways.
 */
function McInventory({ items }: { items: InvItem[] }) {
  const at = (n: number) => items.find((it) => it.s === n) || (n === -106 ? items.find((it) => it.s === 40) : undefined);
  const gap = "0.6cqw";
  return (
    <div style={{ containerType: "inline-size", width: "100%" }}>
      <div
        style={{
          background: "#c6c6c6",
          borderTop: "max(2px, 0.8cqw) solid #ffffff",
          borderLeft: "max(2px, 0.8cqw) solid #ffffff",
          borderRight: "max(2px, 0.8cqw) solid #555555",
          borderBottom: "max(2px, 0.8cqw) solid #555555",
          borderRadius: 0,
          padding: "1.6cqw",
          display: "grid",
          gridTemplateColumns: "repeat(2, minmax(0, 1fr)) 1.4cqw repeat(9, minmax(0, 1fr))",
          gridTemplateRows: "repeat(3, auto) 1.4cqw auto",
          gap,
        }}
      >
        {/* Armor column: helmet, chestplate, leggings, boots */}
        {[103, 102, 101, 100].map((n, row) => (
          <div key={n} style={{ gridColumn: 1, gridRow: row < 3 ? row + 1 : 5 }}>
            <Slot item={at(n)} hint={n} />
          </div>
        ))}
        {/* Off hand, beside the boots */}
        <div style={{ gridColumn: 2, gridRow: 5 }}>
          <Slot item={at(-106)} hint={-106} />
        </div>
        {/* Storage rows: slots 9-35 */}
        {Array.from({ length: 27 }, (_, k) => (
          <div key={`m${k}`} style={{ gridColumn: 4 + (k % 9), gridRow: 1 + Math.floor(k / 9) }}>
            <Slot item={at(9 + k)} />
          </div>
        ))}
        {/* Hotbar: slots 0-8 */}
        {Array.from({ length: 9 }, (_, k) => (
          <div key={`h${k}`} style={{ gridColumn: 4 + k, gridRow: 5 }}>
            <Slot item={at(k)} />
          </div>
        ))}
      </div>
    </div>
  );
}
