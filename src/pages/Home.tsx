import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useTheme } from "../lib/ThemeContext";
import { useLowEndDevice } from "../hooks/useLowEndDevice";

const DownloadIcon = ({ className = "w-5 h-5" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" x2="12" y1="15" y2="3" />
  </svg>
);

const DiscordIcon = ({ className = "w-4 h-4" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
    <path d="M20.32 4.37A19.8 19.8 0 0 0 16.18 3a14.3 14.3 0 0 0-.66 1.35 18.07 18.07 0 0 0-7.03 0A14.6 14.6 0 0 0 7.82 3a19.86 19.86 0 0 0-4.14 1.37C1.04 8.31.33 12.16.68 15.95a20.3 20.3 0 0 0 5.08 2.62c.41-.57.78-1.17 1.1-1.8-.61-.23-1.2-.5-1.76-.82.15-.11.3-.23.44-.35a13.97 13.97 0 0 0 12.92 0c.15.12.29.24.44.35-.56.32-1.15.59-1.77.82.33.63.7 1.23 1.11 1.8a20.23 20.23 0 0 0 5.08-2.62c.41-4.39-.7-8.2-2.92-11.58ZM8.87 13.57c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Zm6.26 0c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Z" />
  </svg>
);

const homeFaqs = [
  {
    question: "Do I need a PC to use PocketHost?",
    answer:
      "No. PocketHost is built for Android-first hosting, so you can start and manage your server directly from your phone.",
  },
  {
    question: "Can Bedrock players join my server?",
    answer:
      "Yes. PocketHost supports Bedrock crossplay so players across editions can connect when configured.",
  },
  {
    question: "Do I need to port forward my router?",
    answer:
      "No. PocketHost uses relay networking so you can host without manual router setup in most cases.",
  },
  {
    question: "Will my world be deleted after an update?",
    answer: "No. Updating the app does not remove your worlds. Keep regular backups for safety.",
  },
];

const Home = () => {
  const { theme } = useTheme();
  const isLowEnd = useLowEndDevice();

  return (
    <div
      className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
        theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"
      }`}
    >
      <SEO
        title="PocketHost - Free Minecraft Server Hosting on Android"
        description="Host Minecraft Java & Bedrock Edition servers directly on your Android phone for free. Zero port forwarding, PaperMC plugins, and high-performance relay tunneling."
        path="/"
      />
      <Navbar />
      <ModernHero />

      <div id="how-it-works">
        <HowItWorks />
      </div>

      <Screenshots />

      <section
        id="faq"
        className={`py-24 px-6 border-t-4 section-transition ${
          theme === "dark" ? "bg-[#0a0a0a] border-white/5" : "bg-white border-black/5"
        }`}
      >
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12">
            <h2
              className={`text-3xl md:text-5xl font-extrabold mb-4 ${
                theme === "dark" ? "text-white" : "text-black"
              }`}
            >
              Frequently Asked Questions
            </h2>
            <p className={`${theme === "dark" ? "text-white/50" : "text-black/60"}`}>
              Quick answers before you host your first world.
            </p>
          </div>

          <div className="grid gap-4">
            {homeFaqs.map((faq, idx) => (
              <motion.details
                key={faq.question}
                initial={{ opacity: 0, y: 12 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: idx * 0.06 }}
                className={`group rounded-xl border-2 p-4 md:p-5 ${
                  theme === "dark" ? "border-white/10 bg-white/[0.02]" : "border-black/10 bg-black/[0.01]"
                }`}
              >
                <summary
                  className={`cursor-pointer list-none font-bold text-sm md:text-base ${
                    theme === "dark" ? "text-white" : "text-black"
                  }`}
                >
                  {faq.question}
                </summary>
                <p
                  className={`mt-3 text-sm md:text-base leading-relaxed ${
                    theme === "dark" ? "text-white/55" : "text-black/65"
                  }`}
                >
                  {faq.answer}
                </p>
              </motion.details>
            ))}
          </div>
        </div>
      </section>

      <section
        id="download"
        className={`py-32 px-6 relative overflow-hidden border-t-4 section-transition ${
          theme === "dark" ? "bg-[#0a0a0a] border-white/5" : "bg-white border-black/5"
        }`}
      >
        {!isLowEnd && (
          <>
            <motion.div
              className="absolute top-1/3 right-0 w-96 h-96 bg-[#7FE620]/10 rounded-full blur-3xl pointer-events-none"
              animate={{ scale: [1, 1.2, 1], opacity: [0.3, 0.5, 0.3] }}
              transition={{ duration: 6, repeat: Infinity }}
            />
            <motion.div
              className="absolute bottom-0 left-0 w-72 h-72 bg-[#1CB0F6]/8 rounded-full blur-3xl pointer-events-none"
              animate={{ scale: [1.2, 1, 1.2] }}
              transition={{ duration: 8, repeat: Infinity }}
            />
          </>
        )}

        <div className="max-w-xl mx-auto text-center relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
              <span className="text-xs font-bold text-[#7FE620] tracking-wider uppercase">Ready to play?</span>
            </div>
          </motion.div>

          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className={`text-4xl md:text-5xl font-extrabold tracking-tight leading-tight mb-6 ${
              theme === "dark" ? "text-white" : "text-black"
            }`}
          >
            Download PocketHost APK
          </motion.h2>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className={`text-base mb-2 max-w-sm mx-auto leading-relaxed font-medium ${
              theme === "dark" ? "text-white/50" : "text-black/60"
            }`}
          >
            Tap below to download the latest PocketHost APK and install it directly on your Android device.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className={`text-sm mb-12 max-w-sm mx-auto ${theme === "dark" ? "text-white/30" : "text-black/50"}`}
          >
            <strong>Version:</strong> Latest Release | <strong>Minimum Android:</strong> 8.0+ (ARM64)
          </motion.p>

          <motion.a
            href="https://github.com/AleemKanyu/PocketCraft/releases/latest/download/PocketHost.apk"
            download="PocketHost.apk"
            className="btn-duo inline-flex items-center justify-center gap-3 px-6 sm:px-12 py-4 text-sm uppercase tracking-wider font-bold w-full sm:w-auto shadow-lg"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.5 }}
            whileHover={isLowEnd ? undefined : { scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
          >
            <DownloadIcon className="w-5 h-5 flex-shrink-0" />
            Download APK (Free)
          </motion.a>

          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.6 }}
            className={`text-xs mt-8 uppercase tracking-wider font-semibold ${
              theme === "dark" ? "text-white/20" : "text-black/40"
            }`}
          >
            Free & Open • Instant Download • No Root Required
          </motion.p>

        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Home;
