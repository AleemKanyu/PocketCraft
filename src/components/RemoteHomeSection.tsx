import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import { Crown, Play, Square, Users, Smartphone, Terminal, Check } from "lucide-react";
import { useTheme } from "../lib/ThemeContext";

/** Home page: Remote Control from the browser, signed in with the app's Google account (Premium). */
export default function RemoteHomeSection() {
  const { theme } = useTheme();
  const isDark = theme === "dark";
  const muted = isDark ? "text-white/55" : "text-black/60";
  const card = isDark ? "bg-[#141414] border-white/10" : "bg-white border-black/[0.08]";

  return (
    <section id="remote-control" className={`py-24 px-6 border-t-4 section-transition ${isDark ? "border-white/5" : "border-black/5"}`}>
      <div className="max-w-6xl mx-auto grid gap-12 lg:grid-cols-2 items-center">
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.5 }}>
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-amber-400/15 text-amber-600">
            <Crown className="w-3.5 h-3.5" /> Premium
          </span>
          <h2 className={`mt-4 text-4xl md:text-5xl font-extrabold tracking-tight leading-tight ${isDark ? "text-white" : "text-black"}`}>
            Run your server from any browser
          </h2>
          <p className={`mt-4 text-base md:text-lg leading-relaxed ${muted}`}>
            Sign in with the Google account you use in PocketHost and control every server on your
            phones from a laptop, a school computer or a friend's phone.
          </p>
          <ul className={`mt-6 space-y-3 text-sm md:text-base ${muted}`}>
            {[
              "Start, stop and restart without touching the phone",
              "Console, plugins, settings and file editing",
              "Every player who ever joined, where they are and what they carry",
            ].map((t) => (
              <li key={t} className="flex items-start gap-3">
                <span className="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-[#7FE620]/20 flex items-center justify-center">
                  <Check className="w-3.5 h-3.5 text-[#4c9a0c]" />
                </span>
                {t}
              </li>
            ))}
          </ul>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link
              to="/remote"
              className="inline-flex items-center justify-center h-12 px-6 rounded-2xl font-bold bg-[#7FE620] text-black hover:bg-[#72d01d] shadow-lg shadow-[#7FE620]/25"
            >
              Open Remote Control
            </Link>
            <span className={`text-xs ${muted}`}>Needs PocketHost Premium.</span>
          </div>
        </motion.div>

        {/* A small picture of the page, drawn with the same pieces as the real thing */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.1 }}
          className="relative"
          aria-hidden="true"
        >
          <div className="absolute -inset-6 bg-[#7FE620]/10 blur-3xl rounded-full pointer-events-none" />
          <div className={`relative rounded-3xl border p-6 shadow-xl ${card}`}>
            <div className={`flex items-center gap-1.5 text-xs font-medium ${isDark ? "text-white/40" : "text-black/40"}`}>
              <Smartphone className="w-3.5 h-3.5" /> Pixel 8
            </div>
            <div className={`mt-1 text-2xl font-extrabold ${isDark ? "text-white" : "text-black"}`}>Survival SMP</div>
            <div className="mt-2 flex items-center gap-3 text-sm">
              <span className="inline-flex items-center gap-2 font-bold text-emerald-500">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 ring-4 ring-emerald-400/30" /> Online
              </span>
              <span className={`inline-flex items-center gap-1 ${muted}`}>
                <Users className="w-4 h-4" /> 4 / 20 playing
              </span>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-2.5">
              <div className="h-12 rounded-2xl bg-rose-600 text-white font-bold flex items-center justify-center gap-2 text-sm">
                <Square className="w-4 h-4 fill-current" /> Stop server
              </div>
              <div className={`h-12 rounded-2xl border font-bold flex items-center justify-center gap-2 text-sm ${isDark ? "border-white/15 text-white" : "border-black/15 text-black"}`}>
                <Play className="w-4 h-4" /> Restart
              </div>
            </div>
            <div className={`mt-4 rounded-2xl p-3 font-mono text-xs ${isDark ? "bg-black/40 text-white/75" : "bg-black/[0.04] text-black/70"}`}>
              <div className="flex items-center gap-1.5 mb-1 font-sans font-semibold text-[11px] uppercase tracking-wider opacity-60">
                <Terminal className="w-3 h-3" /> Console
              </div>
              <div className="text-[#4c9a0c]">&gt; whitelist add Alex</div>
              <div>Added Alex to the whitelist</div>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
