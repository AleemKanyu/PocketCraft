import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { ArrowRight, Sparkles } from "lucide-react";

/** "NEW · PocketHost PC" pill shown above the home headline. */
export function PcAnnouncementPill({ isDark }: { isDark: boolean }) {
  return (
    <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} className="mb-6">
      <Link
        to="/pc"
        className={`group inline-flex items-center gap-2 rounded-full border pl-1.5 pr-4 py-1.5 text-sm font-bold backdrop-blur transition ${
          isDark
            ? "border-white/10 bg-white/[0.04] text-white/80 hover:border-[#7FE620]/60"
            : "border-black/10 bg-white/70 text-black/75 hover:border-[#4c9a0c]/60 shadow-sm"
        }`}
      >
        <span className="rounded-full bg-[#7FE620] text-black px-2 py-0.5 text-[11px] font-extrabold uppercase tracking-wider">New</span>
        PocketHost is now on PC
        <span className="rounded-full bg-[#FFB020] text-black px-2 py-0.5 text-[11px] font-extrabold uppercase tracking-wider">Beta</span>
        <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
      </Link>
    </motion.div>
  );
}

/**
 * A desktop window frame around an app screenshot: soft border, title bar, deep shadow.
 * Screenshots are cropped to the app content, so the frame supplies a clean chrome.
 */
export function WindowFrame({ src, alt, title = "PocketHost PC", isDark }: { src: string; alt: string; title?: string; isDark: boolean }) {
  return (
    <div
      className={`rounded-2xl overflow-hidden ring-1 ${
        isDark ? "ring-white/10 bg-[#141814] shadow-[0_40px_120px_-20px_rgba(127,230,32,0.25)]" : "ring-black/10 bg-white shadow-[0_40px_100px_-30px_rgba(20,60,20,0.35)]"
      }`}
    >
      <div className={`flex items-center gap-2 px-4 h-9 border-b ${isDark ? "border-white/5 bg-white/[0.03]" : "border-black/5 bg-[#f4f6f2]"}`}>
        <img src="/pc/pockethost-pc-icon.png" alt="" width={16} height={16} className="w-4 h-4" />
        <span className={`text-xs font-semibold ${isDark ? "text-white/50" : "text-black/50"}`}>{title}</span>
        <span className="ml-auto flex gap-3 opacity-40">
          <span className={`w-2.5 h-0.5 mt-1 ${isDark ? "bg-white" : "bg-black"}`} />
          <span className={`w-2.5 h-2.5 border ${isDark ? "border-white" : "border-black"}`} />
          <span className={`text-[11px] leading-none ${isDark ? "text-white" : "text-black"}`}>✕</span>
        </span>
      </div>
      <img src={src} alt={alt} width={1478} height={884} loading="lazy" decoding="async" className="w-full h-auto block" />
    </div>
  );
}

/**
 * The "Now on PC" section on the home page: one big confident headline, one button,
 * one screenshot — the Aternos school of "say less, show more".
 */
export function PcHomeSection() {
  return (
    <section className="relative overflow-hidden bg-[#070907] text-white py-24 sm:py-32 px-5 sm:px-6">
      <div className="absolute -top-40 left-1/2 -translate-x-1/2 w-[900px] h-[500px] rounded-full bg-[#7FE620]/[0.12] blur-[120px] pointer-events-none" />
      <div className="absolute inset-0 opacity-[0.06] pointer-events-none bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:28px_28px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" />

      <div className="relative max-w-6xl mx-auto text-center">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
        >
          <div className="inline-flex items-center gap-2 rounded-full border border-[#7FE620]/30 bg-[#7FE620]/10 px-3 py-1 text-xs font-extrabold uppercase tracking-[0.18em] text-[#9BF04A]">
            <Sparkles className="w-3.5 h-3.5" /> New · PocketHost PC Beta
          </div>
          <h2 className="mt-6 text-4xl sm:text-6xl md:text-7xl font-extrabold tracking-tight leading-[1.05]">
            Your server.
            <br />
            <span className="bg-gradient-to-r from-[#7FE620] via-[#B6F27A] to-[#7FE620] bg-clip-text text-transparent">Your PC.</span> Free.
          </h2>
          <p className="mt-6 text-lg sm:text-xl text-white/60 max-w-2xl mx-auto">
            Host Minecraft Java or Bedrock on Windows, macOS or Linux. A guided setup does the hard part — friends
            are playing in minutes.
          </p>
          <div className="mt-9 flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link
              to="/pc"
              className="inline-flex items-center gap-2 rounded-xl bg-[#7FE620] text-black font-extrabold px-7 py-4 border-b-4 border-[#4c9a0c] hover:brightness-105 active:translate-y-[2px] active:border-b-2 transition"
            >
              Get PocketHost PC <ArrowRight className="w-5 h-5" />
            </Link>
            <span className="text-sm text-white/40 font-semibold">Windows · macOS · Linux · Free · Public beta</span>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.7, ease: "easeOut" }}
          className="mt-16 sm:mt-20 max-w-5xl mx-auto"
        >
          <WindowFrame src="/pc/pockethost-pc-overview.webp" alt="PocketHost PC showing a server ready to start, with its join address" isDark />
        </motion.div>

        <div className="mt-14 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-4xl mx-auto text-left">
          {[
            ["Guided setup", "Answer a few simple questions and your server is ready."],
            ["Java & Bedrock", "10 server types, from Paper and Forge to official Bedrock."],
            ["Friends join anywhere", "Your local address, or a free playit.gg tunnel."],
          ].map(([title, body]) => (
            <div key={title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-5">
              <div className="font-extrabold text-white">{title}</div>
              <div className="mt-1 text-sm text-white/55">{body}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
