import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useTheme } from "../lib/ThemeContext";
import { createPortal } from "react-dom";
import { useLowEndDevice } from "../hooks/useLowEndDevice";

const DiscordIcon = ({ className = "w-4 h-4" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
    <path d="M20.32 4.37A19.8 19.8 0 0 0 16.18 3a14.3 14.3 0 0 0-.66 1.35 18.07 18.07 0 0 0-7.03 0A14.6 14.6 0 0 0 7.82 3a19.86 19.86 0 0 0-4.14 1.37C1.04 8.31.33 12.16.68 15.95a20.3 20.3 0 0 0 5.08 2.62c.41-.57.78-1.17 1.1-1.8-.61-.23-1.2-.5-1.76-.82.15-.11.3-.23.44-.35a13.97 13.97 0 0 0 12.92 0c.15.12.29.24.44.35-.56.32-1.15.59-1.77.82.33.63.7 1.23 1.11 1.8a20.23 20.23 0 0 0 5.08-2.62c.41-4.39-.7-8.2-2.92-11.58ZM8.87 13.57c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Zm6.26 0c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Z" />
  </svg>
);

const homeFaqs = [
  {
    question: "Do I need a PC to use PocketCraft?",
    answer:
      "No. PocketCraft is built for Android-first hosting, so you can start and manage your server directly from your phone.",
  },
  {
    question: "Can Bedrock players join my server?",
    answer:
      "Yes. PocketCraft supports Bedrock crossplay so players across editions can connect when configured.",
  },
  {
    question: "Do I need to port forward my router?",
    answer:
      "No. PocketCraft uses relay networking so you can host without manual router setup in most cases.",
  },
  {
    question: "Will my world be deleted after an update?",
    answer: "No. Updating the APK does not remove your worlds. Keep regular backups for safety.",
  },
];

const Home = () => {
  const apkUrl = "/api/apk/download";
  const [showDiscordModal, setShowDiscordModal] = useState(false);
  const { theme } = useTheme();
  const isLowEnd = useLowEndDevice();

  useEffect(() => {
    if (!showDiscordModal) return;

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, [showDiscordModal]);

  const handleApkDownload = () => {
    setShowDiscordModal(true);
  };

  return (
    <div
      className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
        theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"
      }`}
    >
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
            Download PocketCraft APK
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
            Tap below to download and install PocketCraft on Android. If your browser blocks the download, open this page in Chrome.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className={`text-sm mb-12 max-w-sm mx-auto ${theme === "dark" ? "text-white/30" : "text-black/50"}`}
          >
            <strong>Version:</strong> Stable release | <strong>Minimum Android:</strong> 8.0+
          </motion.p>

          <motion.a
            href={apkUrl}
            download
            onClick={handleApkDownload}
            className="btn-duo inline-flex items-center justify-center px-12 py-4 text-sm uppercase tracking-wider font-bold"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.5 }}
            whileHover={isLowEnd ? undefined : { scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
          >
            Download APK
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
            After download: open file -&gt; allow unknown apps -&gt; install.
          </motion.p>

        </div>
      </section>

      <Footer />

      {typeof document !== "undefined" &&
        createPortal(
          <AnimatePresence>
            {showDiscordModal && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
                onClick={(e) => {
                  if (e.target === e.currentTarget) {
                    setShowDiscordModal(false);
                  }
                }}
              >
                <motion.div
                  initial={{ opacity: 0, scale: 0.9, y: 20 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.9, y: 20 }}
                  transition={{ type: "spring", stiffness: 300, damping: 25 }}
                  className={`rounded-2xl p-8 max-w-sm mx-auto text-center shadow-2xl ${
                    theme === "dark" ? "bg-[#1a1a1a] border border-white/10" : "bg-white"
                  }`}
                >
                  <h3 className={`text-2xl font-bold mb-4 ${theme === "dark" ? "text-white" : "text-black"}`}>
                    Join Our Community!
                  </h3>
                  <p className={`mb-6 ${theme === "dark" ? "text-white/50" : "text-black/60"}`}>
                    Love what we're building? Join thousands of players on Discord for tips, support, and updates.
                  </p>
                  <div className="flex flex-col gap-3">
                    <motion.a
                      href="https://discord.com/invite/nc7ceYWVfT"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn-duo px-8 py-3 inline-flex items-center justify-center gap-2 font-bold uppercase text-sm"
                      whileHover={isLowEnd ? undefined : { scale: 1.03 }}
                      whileTap={{ scale: 0.98 }}
                    >
                      <DiscordIcon className="w-4 h-4" />
                      Join Discord
                    </motion.a>
                    <motion.button
                      onClick={() => setShowDiscordModal(false)}
                      className={`px-8 py-3 border-2 rounded-lg transition-colors font-bold uppercase text-sm ${
                        theme === "dark" ? "border-white/20 hover:bg-white/5 text-white/60" : "border-black/20 hover:bg-black/5"
                      }`}
                      whileHover={isLowEnd ? undefined : { scale: 1.03 }}
                      whileTap={{ scale: 0.98 }}
                    >
                      Maybe Later
                    </motion.button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body,
        )}
    </div>
  );
};

export default Home;
