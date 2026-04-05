import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Features from "../components/Features";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useTheme } from "../lib/ThemeContext";

const Home = () => {
  const apkUrl = "/api/apk/download";
  const [showDiscordModal, setShowDiscordModal] = useState(false);
  const { theme } = useTheme();

  const handleApkDownload = () => {
    setShowDiscordModal(true);
  };

  return (
    <div className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
      theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"
    }`}>
      <Navbar />
      <ModernHero />

      <div id="how-it-works">
        <HowItWorks />
      </div>

      <div id="features">
        <Features />
      </div>

      <Screenshots />

      <section id="download" className={`py-32 px-6 relative overflow-hidden border-t-4 section-transition ${
        theme === "dark"
          ? "bg-[#0a0a0a] border-white/5"
          : "bg-white border-black/5"
      }`}>
        {/* Gradient blobs */}
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

        <div className="max-w-xl mx-auto text-center relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-orange-100 border-2 border-orange-300 mb-8">
              <span className="text-xs font-bold text-orange-600 tracking-wider uppercase">
                🚀 Project in Beta
              </span>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
              <span className="text-xs font-bold text-[#7FE620] tracking-wider uppercase">
                Ready to play?
              </span>
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
            Tap below to download and install PocketCraft on Android.
            If your browser blocks the download, open this page in Chrome.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className={`text-sm mb-12 max-w-sm mx-auto ${
              theme === "dark" ? "text-white/30" : "text-black/50"
            }`}
          >
            <strong>Version:</strong> v0.0.1-Beta | <strong>Minimum Android:</strong> 8.0+
          </motion.p>

          <motion.a
            href={apkUrl}
            download
            onClick={() => handleApkDownload()}
            className="btn-duo inline-flex items-center justify-center px-12 py-4 text-sm uppercase tracking-wider font-bold"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.5 }}
            whileHover={{ scale: 1.05, y: -3 }}
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
            After download: open file → allow unknown apps → install.
          </motion.p>

          {/* Discord Modal */}
          <AnimatePresence>
            {showDiscordModal && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
                onClick={(e) => {
                  if (e.target === e.currentTarget) setShowDiscordModal(false);
                }}
              >
                <motion.div
                  initial={{ opacity: 0, scale: 0.9, y: 20 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.9, y: 20 }}
                  transition={{ type: "spring", stiffness: 300, damping: 25 }}
                  className={`rounded-2xl p-8 max-w-sm mx-auto text-center shadow-2xl ${
                    theme === "dark"
                      ? "bg-[#1a1a1a] border border-white/10"
                      : "bg-white"
                  }`}
                >
                  <h3 className={`text-2xl font-bold mb-4 ${
                    theme === "dark" ? "text-white" : "text-black"
                  }`}>Join Our Community!</h3>
                  <p className={`mb-6 ${
                    theme === "dark" ? "text-white/50" : "text-black/60"
                  }`}>
                    Love what we're building? Join thousands of players on Discord for tips, support, and updates.
                  </p>
                  <div className="flex flex-col gap-3">
                    <motion.a
                      href="https://discord.com/invite/nc7ceYWVfT"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn-duo px-8 py-3 inline-flex items-center justify-center font-bold uppercase text-sm"
                      whileHover={{ scale: 1.03 }}
                      whileTap={{ scale: 0.98 }}
                    >
                      Join Discord
                    </motion.a>
                    <motion.button
                      onClick={() => setShowDiscordModal(false)}
                      className={`px-8 py-3 border-2 rounded-lg transition-colors font-bold uppercase text-sm ${
                        theme === "dark"
                          ? "border-white/20 hover:bg-white/5 text-white/60"
                          : "border-black/20 hover:bg-black/5"
                      }`}
                      whileHover={{ scale: 1.03 }}
                      whileTap={{ scale: 0.98 }}
                    >
                      Maybe Later
                    </motion.button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Home;
