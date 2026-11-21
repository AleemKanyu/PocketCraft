import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  Apple,
  Archive,
  Download,
  ExternalLink,
  Globe2,
  HardDrive,
  Laptop,
  Monitor,
  Server,
  ShieldAlert,
  Smartphone,
  Terminal,
  Wifi,
} from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";
import { PLAY_STORE_URL } from "../seo/site";
import {
  desktopDownloads,
  desktopRelease,
  downloadUrl,
  releasePageUrl,
  type DesktopDownload,
  type DesktopOs,
} from "../data/desktopRelease";

/**
 * pockethost.online/pc — PocketHost for Windows, macOS and Linux.
 *
 * Not linked from the home page or navigation yet, and marked noindex, so it is only
 * reachable by someone who has the link.
 */

const OS_META: Record<DesktopOs, { name: string; icon: typeof Monitor }> = {
  windows: { name: "Windows", icon: Monitor },
  macos: { name: "macOS", icon: Apple },
  linux: { name: "Linux", icon: Terminal },
};

const features = [
  {
    icon: Server,
    title: "Java and Bedrock servers",
    body: "Paper, Purpur, Fabric, Vanilla and more for Java — or a separate native Bedrock server for phones, consoles and Windows Bedrock.",
  },
  {
    icon: Wifi,
    title: "Local address",
    body: "Friends on the same Wi-Fi join with your computer's address. Nothing to set up.",
  },
  {
    icon: Globe2,
    title: "playit.gg tunnels",
    body: "Let friends anywhere join through a free playit.gg tunnel — no router or port forwarding.",
  },
  {
    icon: Archive,
    title: "Backups, console and players",
    body: "World snapshots, a live console, whitelist, ops and bans — the same tools as the Android app.",
  },
];

function detectOs(): DesktopOs | null {
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } };
  const platform = (nav.userAgentData?.platform || navigator.platform || "").toLowerCase();
  const ua = navigator.userAgent.toLowerCase();
  if (/android|iphone|ipad|ipod/.test(ua)) return null; // phones get the Android app instead
  if (platform.includes("win") || ua.includes("windows")) return "windows";
  if (platform.includes("mac") || ua.includes("mac os")) return "macos";
  if (platform.includes("linux") || ua.includes("linux") || ua.includes("x11")) return "linux";
  return null;
}

function sizeLabel(d: DesktopDownload) {
  return d.sizeMb > 0 ? `${d.sizeMb} MB` : null;
}

export default function DesktopPage() {
  const { theme } = useTheme();
  const isDark = theme === "dark";
  const [os, setOs] = useState<DesktopOs | null>(null);
  const [isPhone, setIsPhone] = useState(false);

  // The visitor's OS is only known in the browser; the prerendered page shows all options.
  useEffect(() => {
    setOs(detectOs());
    setIsPhone(/android|iphone|ipad|ipod/i.test(navigator.userAgent));
  }, []);

  const primary = os ? desktopDownloads.find((d) => d.os === os) : undefined;
  const muted = isDark ? "text-white/60" : "text-black/60";
  const faint = isDark ? "text-white/40" : "text-black/45";
  const card = isDark ? "border-white/10 bg-white/[0.03]" : "border-black/10 bg-white";

  return (
    <div
      className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
        isDark ? "bg-[#0a0a0a] text-white" : "bg-[#f6f7f5] text-black"
      }`}
    >
      <SEO
        title="PocketHost for PC: Windows, macOS and Linux | PocketHost"
        description="Download PocketHost for PC. Host Minecraft Java and Bedrock servers on your own computer for you and your friends."
        path="/pc"
        noindex
      />
      <Navbar />

      <main className="relative pt-24 sm:pt-28 pb-20 px-4 sm:px-6 overflow-hidden">
        <div className="absolute top-10 left-1/2 -translate-x-1/2 w-[760px] h-[340px] bg-[#7FE620]/[0.08] rounded-full blur-3xl pointer-events-none" />

        {/* Hero */}
        <section className="max-w-5xl mx-auto relative z-10 grid md:grid-cols-[1.1fr_1fr] gap-10 items-center">
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45 }}>
            <div
              className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-[#7FE620]/15 ${
                isDark ? "text-[#7FE620]" : "text-[#4c9a0c]"
              }`}
            >
              <Laptop className="w-3.5 h-3.5" /> PocketHost for PC · v{desktopRelease.version}
            </div>
            <div className="mt-5 flex items-center gap-4">
              <img src="/pc/pockethost-pc-icon.png" alt="PocketHost for PC icon" width={64} height={64} className="w-16 h-16" />
              <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight leading-tight">PocketHost for PC</h1>
            </div>
            <p className={`mt-4 text-lg ${muted}`}>
              Host a Minecraft server on your own computer — Java or Bedrock — for you and your friends. Free, no account, and
              everything it needs (including Java) is downloaded for you.
            </p>

            <div className="mt-7 flex flex-wrap items-center gap-3">
              {primary ? (
                <a
                  href={downloadUrl(primary.file)}
                  className="inline-flex items-center gap-2 rounded-lg bg-[#7FE620] text-black font-extrabold px-6 py-3.5 border-b-4 border-[#4c9a0c] hover:brightness-105 active:translate-y-[2px] active:border-b-2 transition"
                >
                  <Download className="w-5 h-5" /> Download for {OS_META[primary.os].name}
                </a>
              ) : (
                <a
                  href="#downloads"
                  className="inline-flex items-center gap-2 rounded-lg bg-[#7FE620] text-black font-extrabold px-6 py-3.5 border-b-4 border-[#4c9a0c] hover:brightness-105 transition"
                >
                  <Download className="w-5 h-5" /> Choose your download
                </a>
              )}
              <a href="#downloads" className={`text-sm font-bold underline-offset-4 hover:underline ${muted}`}>
                Other platforms
              </a>
            </div>
            {primary && (
              <p className={`mt-3 text-sm ${faint}`}>
                {primary.detail}
                {sizeLabel(primary) ? ` · ${sizeLabel(primary)}` : ""}
                {primary.os === "macos" ? " · Intel Mac? Pick the Intel build below." : ""}
              </p>
            )}
            {isPhone && (
              <p className={`mt-4 text-sm ${muted}`}>
                On a phone? PocketHost for Android hosts a server right on your phone —{" "}
                <a href={PLAY_STORE_URL} className="text-[#7FE620] font-bold hover:underline">
                  get it on Google Play
                </a>
                .
              </p>
            )}
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
            className={`rounded-xl border-2 overflow-hidden shadow-[0_24px_80px_rgba(0,0,0,0.18)] ${card}`}
          >
            <img
              src={isDark ? "/pc/pockethost-pc-dark.webp" : "/pc/pockethost-pc-screenshot.webp"}
              alt="PocketHost for PC showing a server overview with its join address"
              width={1600}
              height={1000}
              className="w-full h-auto block"
            />
          </motion.div>
        </section>

        {/* Downloads */}
        <section id="downloads" className="max-w-5xl mx-auto relative z-10 mt-20 scroll-mt-28">
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">Downloads</h2>
          <p className={`mt-2 ${muted}`}>
            Version {desktopRelease.version} · all files are on{" "}
            <a href={releasePageUrl()} className="text-[#7FE620] font-bold hover:underline inline-flex items-center gap-1">
              GitHub Releases <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </p>

          <div className="mt-6 grid gap-5 md:grid-cols-3">
            {(Object.keys(OS_META) as DesktopOs[]).map((key) => {
              const meta = OS_META[key];
              const Icon = meta.icon;
              const files = desktopDownloads.filter((d) => d.os === key);
              const highlighted = os === key;
              return (
                <div
                  key={key}
                  className={`rounded-xl border-2 p-5 flex flex-col ${card} ${highlighted ? "!border-[#7FE620]" : ""}`}
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2.5 rounded-lg bg-[#7FE620]/15">
                      <Icon className={`w-6 h-6 ${isDark ? "text-[#7FE620]" : "text-[#4c9a0c]"}`} />
                    </div>
                    <h3 className="text-xl font-extrabold">{meta.name}</h3>
                    {highlighted && (
                      <span className="ml-auto text-[11px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-[#7FE620] text-black">
                        Your system
                      </span>
                    )}
                  </div>
                  <div className="mt-4 flex flex-col gap-3">
                    {files.map((d) => (
                      <a
                        key={d.file}
                        href={downloadUrl(d.file)}
                        className={`group rounded-lg border px-4 py-3 flex items-center gap-3 transition hover:border-[#7FE620] ${
                          isDark ? "border-white/10 hover:bg-white/[0.04]" : "border-black/10 hover:bg-black/[0.02]"
                        }`}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="font-bold text-[0.95rem]">{d.label}</div>
                          <div className={`text-xs ${faint}`}>
                            {d.detail}
                            {sizeLabel(d) ? ` · ${sizeLabel(d)}` : ""}
                          </div>
                        </div>
                        <Download className="w-4 h-4 shrink-0 opacity-60 group-hover:opacity-100 group-hover:text-[#7FE620]" />
                      </a>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* Features */}
        <section className="max-w-5xl mx-auto relative z-10 mt-20">
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">What you get</h2>
          <div className="mt-6 grid gap-5 sm:grid-cols-2">
            {features.map((f) => {
              const Icon = f.icon;
              return (
                <div key={f.title} className={`rounded-xl border-2 p-5 flex gap-4 ${card}`}>
                  <div className="p-2.5 rounded-lg bg-[#7FE620]/15 h-fit">
                    <Icon className={`w-5 h-5 ${isDark ? "text-[#7FE620]" : "text-[#4c9a0c]"}`} />
                  </div>
                  <div>
                    <h3 className="font-extrabold text-lg">{f.title}</h3>
                    <p className={`mt-1 text-[0.95rem] ${muted}`}>{f.body}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* Install notes */}
        <section className="max-w-5xl mx-auto relative z-10 mt-20">
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">Installing</h2>
          <div className={`mt-4 rounded-xl border-2 p-5 flex gap-3 ${card}`}>
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5 text-[#FFB020]" />
            <p className={`text-[0.95rem] ${muted}`}>
              PocketHost for PC isn't code-signed yet, so Windows and macOS show a warning the first time you open it. That's
              expected for a new, independent app — here's how to get past it.
            </p>
          </div>
          <div className="mt-5 grid gap-5 md:grid-cols-3">
            <InstallStep
              card={card}
              muted={muted}
              title="Windows"
              steps={[
                "Open the downloaded .msi.",
                "If SmartScreen says “Windows protected your PC”, click More info → Run anyway.",
                "Launch PocketHost from the Start Menu or desktop shortcut.",
              ]}
            />
            <InstallStep
              card={card}
              muted={muted}
              title="macOS"
              steps={[
                "Open the .dmg and drag PocketHost into Applications.",
                "Open it once; macOS will block it.",
                "Go to System Settings → Privacy & Security and click Open Anyway.",
              ]}
            />
            <InstallStep
              card={card}
              muted={muted}
              title="Linux"
              steps={[
                "Debian/Ubuntu: sudo apt install ./PocketHost-*.deb",
                "Fedora/RHEL: sudo dnf install ./PocketHost-*.rpm",
                "Find PocketHost in your app menu.",
              ]}
            />
          </div>
          <div className={`mt-5 flex flex-wrap gap-x-8 gap-y-2 text-sm ${faint}`}>
            <span className="inline-flex items-center gap-2">
              <HardDrive className="w-4 h-4" /> 64-bit OS · 4 GB RAM or more recommended
            </span>
            <span className="inline-flex items-center gap-2">
              <Smartphone className="w-4 h-4" /> Want to host from your phone instead?{" "}
              <a href={PLAY_STORE_URL} className="text-[#7FE620] font-bold hover:underline">
                PocketHost for Android
              </a>
            </span>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}

function InstallStep({ title, steps, card, muted }: { title: string; steps: string[]; card: string; muted: string }) {
  return (
    <div className={`rounded-xl border-2 p-5 ${card}`}>
      <h3 className="font-extrabold text-lg">{title}</h3>
      <ol className={`mt-3 space-y-2 text-[0.92rem] list-decimal pl-5 ${muted}`}>
        {steps.map((s) => (
          <li key={s} className="break-words">
            {s}
          </li>
        ))}
      </ol>
    </div>
  );
}
