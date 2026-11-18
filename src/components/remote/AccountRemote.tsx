import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Crown, LogOut, Smartphone, Link2, ChevronRight, Check, Server } from "lucide-react";
import ServerControl from "./ServerControl";
import { getFirestoreDb, loadAuth, toMillis, formatAgo } from "../../lib/remoteFirebase";

const PLAY_URL = "https://play.google.com/store/apps/details?id=com.pockethost.app";

interface AccountServer {
  id: string;
  code: string;
  serverName: string;
  device: string;
  tier: "free" | "premium";
  adminKey?: string;
  updatedAt?: any;
}

const GoogleMark = () => (
  <svg viewBox="0 0 48 48" className="w-5 h-5" aria-hidden="true">
    <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
    <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
    <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
    <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
  </svg>
);

/**
 * pockethost.online/remote without a link: sign in with the same Google account as the app and
 * control every phone that account has turned Remote Control on. Premium only; the phone lists
 * itself under users/{uid}/remote_servers when it is signed in.
 */
export default function AccountRemote({ isDark }: { isDark: boolean }) {
  const [authApi, setAuthApi] = useState<{ auth: any; mod: any } | null>(null);
  const [user, setUser] = useState<any>(undefined); // undefined = still checking
  const [servers, setServers] = useState<AccountServer[] | null>(null);
  const [accountPremium, setAccountPremium] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [signInError, setSignInError] = useState<string | null>(null);
  const [pasted, setPasted] = useState("");
  const [now] = useState(Date.now());

  // Load Firebase Auth up front so the sign-in click opens the popup straight away (a popup opened
  // after an await can be blocked by the browser).
  useEffect(() => {
    let unsub: (() => void) | undefined;
    let cancelled = false;
    loadAuth()
      .then((api) => {
        if (cancelled) return;
        setAuthApi(api);
        unsub = api.mod.onAuthStateChanged(api.auth, (u: any) => setUser(u && !u.isAnonymous ? u : null));
      })
      .catch(() => setUser(null));
    return () => {
      cancelled = true;
      unsub?.();
    };
  }, []);

  useEffect(() => {
    if (!user) {
      setServers(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const db = await getFirestoreDb();
        const { collection, getDocs, doc, getDoc } = await import("firebase/firestore");
        const [list, profile] = await Promise.all([
          getDocs(collection(db, "users", user.uid, "remote_servers")),
          getDoc(doc(db, "users", user.uid)).catch(() => null),
        ]);
        if (cancelled) return;
        const rows: AccountServer[] = list.docs.map((d: any) => ({ id: d.id, ...(d.data() as any) }));
        rows.sort((a, b) => (toMillis(b.updatedAt) ?? 0) - (toMillis(a.updatedAt) ?? 0));
        const p = profile && profile.exists() ? profile.data() : {};
        const until = toMillis(p.premiumUntil) ?? toMillis(p.premiumExpiry) ?? toMillis(p.expiresAt);
        const tierOk = p.premiumTier === "premium" || p.premiumTier === "supportive";
        setAccountPremium(tierOk && (until === null || until > Date.now()));
        setServers(rows);
        setSelected((cur) => cur ?? rows[0]?.id ?? null);
      } catch {
        if (!cancelled) setServers([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [user]);

  function signIn() {
    if (!authApi) return;
    setSignInError(null);
    const provider = new authApi.mod.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: "select_account" });
    authApi.mod.signInWithPopup(authApi.auth, provider).catch((err: any) => {
      const code = String(err?.code || "");
      if (code.includes("popup-closed") || code.includes("cancelled-popup")) return;
      setSignInError(
        code.includes("popup-blocked")
          ? "Your browser blocked the sign-in window. Allow pop-ups for this site and try again."
          : code.includes("unauthorized-domain")
            ? "Sign-in isn't set up for this address yet."
            : "Couldn't sign in. Try again."
      );
    });
  }

  const card = isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/[0.08]";
  const muted = isDark ? "text-white/55" : "text-black/55";
  const faint = isDark ? "text-white/35" : "text-black/40";

  // Premium shows on the phone's row as soon as the phone has it, and on the account profile.
  const isPremium = accountPremium || (servers || []).some((s) => s.tier === "premium");
  const current = (servers || []).find((s) => s.id === selected) || null;

  if (user === undefined) {
    return (
      <div className={`rounded-3xl border p-10 text-center ${card}`}>
        <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin mx-auto" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="space-y-4">
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className={`rounded-3xl border p-6 sm:p-8 ${card}`}>
          <div className="w-12 h-12 rounded-2xl bg-[#7FE620]/15 flex items-center justify-center mb-4">
            <Server className="w-6 h-6 text-[#5ab30f]" />
          </div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight">All your servers in one place</h2>
          <p className={`mt-2 text-sm leading-relaxed ${muted}`}>
            Sign in with the same Google account you use in the PocketHost app to start, stop and manage
            every server you host, from any browser.
          </p>
          <ul className={`mt-4 space-y-2 text-sm ${muted}`}>
            {["Start, stop and restart", "Console, players, plugins, settings and files", "Every phone on your account"].map((t) => (
              <li key={t} className="flex items-center gap-2">
                <Check className="w-4 h-4 text-[#5ab30f]" /> {t}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={signIn}
            disabled={!authApi}
            className={`mt-6 w-full h-14 rounded-2xl font-bold text-base flex items-center justify-center gap-3 border transition-all ${
              isDark ? "bg-white text-black border-white hover:bg-white/90" : "bg-white text-black border-black/15 hover:bg-black/[0.03] shadow-sm"
            } ${!authApi ? "opacity-50" : "active:scale-[0.99]"}`}
          >
            <GoogleMark /> Sign in with Google
          </button>
          {signInError && <p className="mt-3 text-sm text-rose-500">{signInError}</p>}
          <p className={`mt-4 text-xs flex items-center gap-1.5 ${faint}`}>
            <Crown className="w-3.5 h-3.5 text-amber-500" /> Controlling servers from your account is part of PocketHost Premium.
          </p>
        </motion.div>

        <div className={`rounded-3xl border p-5 ${card}`}>
          <div className="flex items-center gap-2 font-bold text-sm">
            <Link2 className="w-4 h-4 text-[#5ab30f]" /> Have a link from the app?
          </div>
          <p className={`mt-1 text-sm ${muted}`}>
            In the app open Settings, then Remote Control, and tap Copy Link. Paste it here.
          </p>
          <form
            className="mt-3 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const val = pasted.trim();
              const match = val.includes("#") ? val.split("#")[1] : val;
              if (match) window.location.hash = match;
            }}
          >
            <input
              value={pasted}
              onChange={(e) => setPasted(e.target.value)}
              placeholder="https://pockethost.online/remote#..."
              className={`flex-1 min-w-0 px-4 h-12 rounded-2xl border text-sm outline-none ${
                isDark ? "bg-white/5 border-white/10 placeholder-white/30 focus:border-[#7FE620]" : "bg-black/[0.03] border-black/10 placeholder-black/30 focus:border-[#5ab30f]"
              }`}
            />
            <button type="submit" className="px-5 h-12 rounded-2xl font-bold text-sm bg-[#7FE620] text-black hover:bg-[#72d01d]">
              Open
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Account bar */}
      <div className={`rounded-3xl border px-4 py-3 flex items-center justify-between gap-3 ${card}`}>
        <div className="flex items-center gap-3 min-w-0">
          {user.photoURL ? (
            <img src={user.photoURL} alt="" referrerPolicy="no-referrer" className="w-9 h-9 rounded-full" />
          ) : (
            <span className="w-9 h-9 rounded-full bg-[#7FE620] text-black font-extrabold flex items-center justify-center">
              {(user.displayName || user.email || "?")[0].toUpperCase()}
            </span>
          )}
          <div className="min-w-0">
            <div className="text-sm font-bold truncate flex items-center gap-1.5">
              {user.displayName || "Your account"}
              {isPremium && (
                <span className="inline-flex items-center gap-1 text-[10px] font-extrabold uppercase tracking-wider px-1.5 py-0.5 rounded-md bg-amber-400/20 text-amber-600">
                  <Crown className="w-3 h-3" /> Premium
                </span>
              )}
            </div>
            <div className={`text-xs truncate ${faint}`}>{user.email}</div>
          </div>
        </div>
        <button
          type="button"
          onClick={() => authApi?.mod.signOut(authApi.auth)}
          className={`shrink-0 inline-flex items-center gap-1.5 text-sm font-semibold px-3 py-2 rounded-xl ${isDark ? "hover:bg-white/5" : "hover:bg-black/5"} ${muted}`}
        >
          <LogOut className="w-4 h-4" /> Sign out
        </button>
      </div>

      {servers === null && (
        <div className={`rounded-3xl border p-10 text-center ${card}`}>
          <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin mx-auto" />
        </div>
      )}

      {servers !== null && !isPremium && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className={`rounded-3xl border p-6 sm:p-8 text-center ${card}`}>
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-amber-300 to-amber-500 flex items-center justify-center mx-auto mb-4 shadow-lg shadow-amber-500/25">
            <Crown className="w-7 h-7 text-white" />
          </div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight">You need PocketHost Premium</h2>
          <p className={`mt-2 text-sm leading-relaxed max-w-md mx-auto ${muted}`}>
            Controlling your servers from your account is a Premium feature. Buy Premium in the PocketHost app
            (tap the menu, then Support PocketHost), then come back to this page.
          </p>
          <a
            href={PLAY_URL}
            target="_blank"
            rel="noreferrer"
            className="mt-6 inline-flex items-center justify-center gap-2 h-12 px-6 rounded-2xl font-bold text-sm bg-[#7FE620] text-black hover:bg-[#72d01d]"
          >
            Open PocketHost on Google Play
          </a>
          <p className={`mt-4 text-xs ${faint}`}>
            Already have Premium? Make sure you're signed in with the same Google account in the app.
          </p>
        </motion.div>
      )}

      {servers !== null && isPremium && servers.length === 0 && (
        <div className={`rounded-3xl border p-6 sm:p-8 ${card}`}>
          <h2 className="text-lg font-extrabold">No servers on this account yet</h2>
          <ol className={`mt-3 space-y-2.5 text-sm ${muted}`}>
            {[
              "Update PocketHost to the latest version.",
              "In the app, sign in with this Google account (Settings, then Account).",
              "Turn on Remote Control (Settings, then App). Your server shows up here.",
            ].map((t, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-6 h-6 shrink-0 rounded-full bg-[#7FE620]/20 text-[#4c9a0c] text-xs font-extrabold flex items-center justify-center">{i + 1}</span>
                <span className="pt-0.5">{t}</span>
              </li>
            ))}
          </ol>
        </div>
      )}

      {servers !== null && isPremium && servers.length > 0 && (
        <>
          {servers.length > 1 && (
            <div className="grid gap-2 sm:grid-cols-2">
              {servers.map((s) => {
                const active = s.id === selected;
                const seen = toMillis(s.updatedAt);
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => setSelected(s.id)}
                    className={`text-left rounded-2xl border px-4 py-3 flex items-center justify-between gap-3 transition-all ${
                      active ? "border-[#7FE620] ring-2 ring-[#7FE620]/30 " + (isDark ? "bg-[#7FE620]/10" : "bg-[#7FE620]/10") : `${card} hover:border-[#7FE620]/50`
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="block font-bold text-sm truncate">{s.serverName}</span>
                      <span className={`flex items-center gap-1 text-xs truncate ${faint}`}>
                        <Smartphone className="w-3 h-3" /> {s.device}
                        {seen ? ` · ${formatAgo(seen, now)}` : ""}
                      </span>
                    </span>
                    <ChevronRight className={`w-4 h-4 shrink-0 ${active ? "text-[#5ab30f]" : faint}`} />
                  </button>
                );
              })}
            </div>
          )}
          {current && (
            <ServerControl key={current.id} code={current.code} adminKey={current.adminKey} isDark={isDark} subtitle={current.device} />
          )}
        </>
      )}

    </div>
  );
}
