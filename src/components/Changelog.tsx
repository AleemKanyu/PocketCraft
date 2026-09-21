import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useTheme } from "../lib/ThemeContext";

interface ChangelogEntry {
  version: string;
  date: string;
  badge?: "latest" | "major" | "beta";
  changes: { type: "feat" | "fix" | "perf"; text: string }[];
}

const changelog: ChangelogEntry[] = [
  {
    version: "v1.2.3",
    date: "Sep 2026",
    badge: "latest",
    changes: [
      { type: "perf", text: "Dramatically faster server startup — boots in seconds, not minutes" },
      { type: "feat", text: "Optimized APK with smaller download size" },
      { type: "fix", text: "Improved stability for long-running sessions" },
    ],
  },
  {
    version: "v1.2.2",
    date: "Aug 2026",
    badge: "major",
    changes: [
      { type: "feat", text: "Web Dashboard — manage your server from any browser" },
      { type: "feat", text: "Live boot console — watch your server start up in real time" },
      { type: "feat", text: "Player management panel with skin previews, UUIDs, and admin actions" },
      { type: "feat", text: "AFK bot manager — spawn coordinate-based bots to keep chunks loaded" },
      { type: "feat", text: "Real-time player stats scanner with teleport buttons" },
      { type: "feat", text: "Copy-to-clipboard for your server IP (public relay + local network)" },
      { type: "feat", text: "Server Settings panel — adjust config directly from the dashboard" },
      { type: "feat", text: "Restart server button in the dashboard" },
    ],
  },
  {
    version: "v1.2.1",
    date: "Aug 2026",
    changes: [
      { type: "feat", text: "Renamed and rebranded to PocketHost with new app icon" },
      { type: "feat", text: "Server Dashboard and Player Management screenshots added to the website" },
      { type: "fix", text: "Download buttons now link directly to the latest release APK" },
    ],
  },
  {
    version: "v1.6.0 / v1.5.0",
    date: "Jul 2026",
    badge: "major",
    changes: [
      { type: "feat", text: "ViaVersion bundled — players on different Minecraft versions can now join" },
      { type: "feat", text: "Multi-language support added to the app" },
      { type: "feat", text: "AFK helper bots — keep your server alive and chunks loaded automatically" },
      { type: "perf", text: "Chunk loading speed optimized for smoother gameplay" },
      { type: "perf", text: "Cellular data budget tuning — reduced mobile data usage" },
      { type: "perf", text: "Java player ping stability improvements under heavy load" },
      { type: "fix", text: "Bedrock player disconnections significantly reduced" },
      { type: "fix", text: "Elytra flight lag fixed for Java players" },
      { type: "fix", text: "Large world backup and restore no longer crashes the app" },
      { type: "fix", text: "Home screen widget now syncs theme with the app" },
    ],
  },
  {
    version: "v0.9.0",
    date: "Jun 2026",
    changes: [
      { type: "feat", text: "Bedrock crossplay fully functional — iOS, Android, Xbox, Switch, PlayStation players can join" },
      { type: "perf", text: "Relay networking overhauled — lower latency, better connection stability" },
      { type: "fix", text: "Server startup timeout issue fixed — no more false 120s timeout failures" },
      { type: "fix", text: "OnePlus port reset issue resolved" },
      { type: "feat", text: "Relay retry button added when connection drops" },
    ],
  },
  {
    version: "v0.3.0-Beta",
    date: "May 2026",
    changes: [
      { type: "feat", text: "Bundled JRE 25 runtime — no external Java installation needed" },
      { type: "feat", text: "Force external JVM toggle added for power users" },
      { type: "feat", text: "Xiaomi / Android 16 compatibility workaround" },
      { type: "feat", text: "16KB page alignment for newer Android devices" },
    ],
  },
  {
    version: "v0.2.0-Beta",
    date: "Apr 2026",
    changes: [
      { type: "feat", text: "Player management — kick, ban, and change gamemodes in-app" },
      { type: "feat", text: "World seed viewer — see your world seed at any time" },
      { type: "feat", text: "Push notifications when players join or leave" },
      { type: "feat", text: "Render distance adjustable from the app without restarting" },
      { type: "feat", text: "Server properties fully editable — difficulty, gamemode, max players, and more" },
      { type: "feat", text: "Plugin support — drop Bukkit/Spigot plugins in and hot-reload" },
      { type: "feat", text: "Privacy Policy, Terms of Service, and Legal Center pages added" },
    ],
  },
  {
    version: "v0.0.2-Beta",
    date: "Apr 2026",
    badge: "beta",
    changes: [
      { type: "feat", text: "APK download directly from the website" },
      { type: "feat", text: "Blog launched with PocketHost news and guides" },
      { type: "feat", text: "Low-end device detection — animations auto-disabled for smooth performance" },
      { type: "feat", text: "Code and structure refactored for reliability" },
    ],
  },
  {
    version: "v0.0.1-Beta",
    date: "Mar 2026",
    badge: "beta",
    changes: [
      { type: "feat", text: "PocketHost launched — host a full PaperMC Java server on your Android phone" },
      { type: "feat", text: "Zero port forwarding required — proprietary relay networking built-in" },
      { type: "feat", text: "24/7 server hosting without queues or inactivity shutdowns" },
      { type: "feat", text: "Full plugin support from day one" },
      { type: "feat", text: "Server ranking page and community stats API" },
    ],
  },
];

const typeStyles = {
  feat: { label: "New", color: "#7FE620", bg: "#7FE62015" },
  fix:  { label: "Fix", color: "#1CB0F6", bg: "#1CB0F615" },
  perf: { label: "Perf", color: "#FFD900", bg: "#FFD90015" },
};

const badgeStyles = {
  latest: { label: "Latest", color: "#7FE620", border: "#7FE62040" },
  major:  { label: "Major", color: "#FF85B3", border: "#FF85B340" },
  beta:   { label: "Beta", color: "#AB47BC", border: "#AB47BC40" },
};

export default function Changelog() {
  const { theme } = useTheme();
  const [expanded, setExpanded] = useState<string | null>("v1.2.3");

  const isDark = theme === "dark";

  return (
    <section
      id="changelog"
      className={`py-24 px-6 border-t-4 section-transition ${
        isDark ? "border-white/5" : "border-black/5"
      }`}
    >
      <div className="max-w-4xl mx-auto relative z-10">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="mb-14"
        >
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-1 bg-[#7FE620] rounded-full" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">
              What&apos;s New
            </span>
          </div>
          <h2
            className={`text-3xl md:text-5xl font-extrabold mb-4 leading-tight ${
              isDark ? "text-white" : "text-black"
            }`}
          >
            Changelog
          </h2>
          <p className={`text-base max-w-xl ${isDark ? "text-white/50" : "text-black/55"}`}>
            Every update we ship, written for you — not developers.
          </p>
        </motion.div>

        {/* Timeline */}
        <div className="relative">
          {/* Vertical line */}
          <div
            className={`absolute left-[11px] top-2 bottom-2 w-px ${
              isDark ? "bg-white/10" : "bg-black/10"
            }`}
          />

          <div className="flex flex-col gap-4">
            {changelog.map((entry, idx) => {
              const isOpen = expanded === entry.version;
              return (
                <motion.div
                  key={entry.version}
                  initial={{ opacity: 0, x: -16 }}
                  whileInView={{ opacity: 1, x: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4, delay: idx * 0.04 }}
                  className="pl-8 relative"
                >
                  {/* Dot */}
                  <div
                    className={`absolute left-0 top-[18px] w-[23px] h-[23px] rounded-full border-2 flex items-center justify-center ${
                      isOpen
                        ? "border-[#7FE620] bg-[#7FE620]"
                        : isDark
                        ? "border-white/20 bg-[#111]"
                        : "border-black/15 bg-white"
                    }`}
                  >
                    {isOpen && (
                      <div className="w-2 h-2 rounded-full bg-black" />
                    )}
                  </div>

                  {/* Card */}
                  <div
                    className={`rounded-2xl border-2 overflow-hidden transition-all duration-300 ${
                      isOpen
                        ? isDark
                          ? "border-white/15 bg-white/[0.03]"
                          : "border-black/15 bg-black/[0.02]"
                        : isDark
                        ? "border-white/8 bg-white/[0.01] hover:border-white/15"
                        : "border-black/8 bg-black/[0.01] hover:border-black/15"
                    }`}
                  >
                    {/* Accordion header */}
                    <button
                      className="w-full text-left px-5 py-4 flex items-center justify-between gap-3 cursor-pointer"
                      onClick={() =>
                        setExpanded(isOpen ? null : entry.version)
                      }
                    >
                      <div className="flex items-center gap-3 flex-wrap">
                        <span
                          className={`font-extrabold text-base font-mono ${
                            isDark ? "text-white" : "text-black"
                          }`}
                        >
                          {entry.version}
                        </span>
                        <span
                          className={`text-xs font-semibold ${
                            isDark ? "text-white/40" : "text-black/45"
                          }`}
                        >
                          {entry.date}
                        </span>
                        {entry.badge && (
                          <span
                            className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border"
                            style={{
                              color: badgeStyles[entry.badge].color,
                              borderColor: badgeStyles[entry.badge].border,
                              backgroundColor:
                                badgeStyles[entry.badge].color + "12",
                            }}
                          >
                            {badgeStyles[entry.badge].label}
                          </span>
                        )}
                        <span
                          className={`text-xs ${
                            isDark ? "text-white/25" : "text-black/35"
                          }`}
                        >
                          {entry.changes.length} change
                          {entry.changes.length !== 1 ? "s" : ""}
                        </span>
                      </div>

                      {/* Chevron */}
                      <motion.svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        className={`w-4 h-4 flex-shrink-0 ${
                          isDark ? "text-white/40" : "text-black/40"
                        }`}
                        animate={{ rotate: isOpen ? 180 : 0 }}
                        transition={{ duration: 0.25 }}
                      >
                        <polyline points="6 9 12 15 18 9" />
                      </motion.svg>
                    </button>

                    {/* Change list */}
                    <AnimatePresence initial={false}>
                      {isOpen && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: "auto", opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.3, ease: "easeInOut" }}
                          className="overflow-hidden"
                        >
                          <div
                            className={`px-5 pb-5 border-t ${
                              isDark ? "border-white/8" : "border-black/8"
                            }`}
                          >
                            <ul className="mt-4 flex flex-col gap-2.5">
                              {entry.changes.map((c, i) => (
                                <motion.li
                                  key={i}
                                  initial={{ opacity: 0, x: -8 }}
                                  animate={{ opacity: 1, x: 0 }}
                                  transition={{
                                    duration: 0.25,
                                    delay: i * 0.04,
                                  }}
                                  className="flex items-start gap-3"
                                >
                                  <span
                                    className="mt-0.5 text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded flex-shrink-0"
                                    style={{
                                      color: typeStyles[c.type].color,
                                      backgroundColor: typeStyles[c.type].bg,
                                    }}
                                  >
                                    {typeStyles[c.type].label}
                                  </span>
                                  <span
                                    className={`text-sm leading-relaxed ${
                                      isDark ? "text-white/70" : "text-black/70"
                                    }`}
                                  >
                                    {c.text}
                                  </span>
                                </motion.li>
                              ))}
                            </ul>
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                </motion.div>
              );
            })}
          </div>
        </div>

        {/* Footer note */}
        <motion.p
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className={`mt-10 text-xs text-center uppercase tracking-wider font-semibold ${
            isDark ? "text-white/20" : "text-black/30"
          }`}
        >
          All releases are free forever · No subscriptions required
        </motion.p>
      </div>
    </section>
  );
}
