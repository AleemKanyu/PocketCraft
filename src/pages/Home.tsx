import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { motion } from "framer-motion";

const DiscordIcon = ({ className = "w-4 h-4" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
    <path d="M20.32 4.37A19.8 19.8 0 0 0 16.18 3a14.3 14.3 0 0 0-.66 1.35 18.07 18.07 0 0 0-7.03 0A14.6 14.6 0 0 0 7.82 3a19.86 19.86 0 0 0-4.14 1.37C1.04 8.31.33 12.16.68 15.95a20.3 20.3 0 0 0 5.08 2.62c.41-.57.78-1.17 1.1-1.8-.61-.23-1.2-.5-1.76-.82.15-.11.3-.23.44-.35a13.97 13.97 0 0 0 12.92 0c.15.12.29.24.44.35-.56.32-1.15.59-1.77.82.33.63.7 1.23 1.11 1.8a20.23 20.23 0 0 0 5.08-2.62c.41-4.39-.7-8.2-2.92-11.58ZM8.87 13.57c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Zm6.26 0c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Z" />
  </svg>
);

const faqs = [
  {
    question: "Do I need a PC to run PocketCraft?",
    answer:
      "No! PocketCraft runs entirely on your Android phone. You don't need a computer, laptop, or separate server. All you need is an Android device and an internet connection.",
  },
  {
    question: "Can Bedrock players join my Java server?",
    answer:
      "Yes! With cross-platform support, players on Bedrock, Pocket Edition, and consoles can join your Java Edition server. It just works.",
  },
  {
    question: "How much does it cost?",
    answer:
      "PocketCraft is free to download and use. You can host one world server with unlimited player slots. Relay hosting has infrastructure cost, so optional premium features may be added later. The core experience remains free.",
  },
  {
    question: "Will my server stay online when I lock my phone?",
    answer:
      "Yes. Once your server starts, it keeps running even if your screen turns off. You can use your phone normally while friends play on your server.",
  },
  {
    question: "Can I install plugins and mods?",
    answer:
      "Yes! PocketCraft supports Bukkit plugins and Spigot community plugins. Drag and drop .jar files directly in the app.",
  },
  {
    question: "Do I need port forwarding?",
    answer:
      "No. PocketCraft uses relay technology so players can connect without router configuration.",
  },
  {
    question: "Can I keep my old world and move it to PocketCraft?",
    answer:
      "Yes. You can import your existing world files into PocketCraft and continue playing without starting over.",
  },
  {
    question: "Does PocketCraft work on low-end phones?",
    answer:
      "It works on many devices, but better CPU and RAM will give smoother gameplay and support more players.",
  },
  {
    question: "How do updates work?",
    answer:
      "When a new app build is released, download and install the latest APK. Your worlds remain on your device unless you remove app data.",
  },
  {
    question: "Where can I get help quickly?",
    answer:
      "Join our Discord community for setup help, troubleshooting, and release updates.",
  },
];

function FAQItem({ faq, index }: { faq: (typeof faqs)[0]; index: number }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.4, delay: index * 0.06 }}
      className="border-2 border-black/10 rounded-xl overflow-hidden hover:border-[#7FE620] transition-colors bg-white shadow-[0_4px_20px_rgba(0,0,0,0.04)]"
    >
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full px-4 sm:px-6 py-4 flex items-center justify-between"
      >
        <h3 className="text-[15px] sm:text-base md:text-lg font-bold text-black text-left pr-4">{faq.question}</h3>
        <ChevronDown
          size={22}
          className={`text-[#7FE620] flex-shrink-0 transition-transform ${isOpen ? "rotate-180" : ""}`}
        />
      </button>
      {isOpen && (
        <div className="px-4 sm:px-6 py-4 bg-black/[0.02] border-t border-black/10">
          <p className="text-black/70 text-sm sm:text-base font-medium leading-relaxed">{faq.answer}</p>
        </div>
      )}
    </motion.div>
  );
}

const Home = () => {
  const apkUrl = "/api/apk/download";
  const [showInstallModal, setShowInstallModal] = useState(false);
  const [showDiscordModal, setShowDiscordModal] = useState(false);

  const handleDownloadClick = () => {
    setShowInstallModal(true);
  };

  const handleModalDownloadClick = () => {
    window.location.href = apkUrl;
  };

  const handleInstallModalClose = () => {
    setShowInstallModal(false);
    setShowDiscordModal(true);
  };

  return (
    <div className="min-h-screen bg-white text-black selection:bg-[#7FE620] selection:text-black font-sans">
      <Navbar />
      <ModernHero />

      <div id="how-it-works">
        <HowItWorks />
      </div>

      <Screenshots />

      <section id="faq" className="py-20 md:py-24 px-4 sm:px-6 bg-[#f9f9f9] border-y-4 border-black/5 relative overflow-hidden">
        <div className="absolute -top-24 -right-8 w-72 h-72 bg-[#1CB0F6]/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-24 -left-8 w-72 h-72 bg-[#7FE620]/15 rounded-full blur-3xl pointer-events-none" />
        <div className="max-w-4xl mx-auto relative z-10">
          <div className="text-center mb-10 md:mb-12">
            <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold text-black tracking-tight mb-4">Frequently Asked Questions</h2>
            <p className="text-black/60 text-sm sm:text-base md:text-lg max-w-2xl mx-auto">
              Everything you need to know before you host your first world.
            </p>
          </div>

          <div className="space-y-4">
            {faqs.map((faq, index) => (
              <FAQItem key={faq.question} faq={faq} index={index} />
            ))}
          </div>
        </div>
      </section>

      <section id="download" className="py-24 md:py-32 px-4 sm:px-6 bg-white relative overflow-hidden border-t-4 border-black/5">
        {/* Gradient blobs */}
        <div className="absolute top-1/3 right-0 w-96 h-96 bg-[#7FE620]/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-20 left-0 w-72 h-72 bg-[#1CB0F6]/10 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-xl mx-auto text-center relative z-10">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
            <span className="text-xs font-bold text-[#7FE620] tracking-wider uppercase">
              Ready to play?
            </span>
          </div>
          <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold text-black tracking-tight leading-tight mb-6">
            Download PocketCraft APK
          </h2>
          <p className="text-black/60 text-sm sm:text-base mb-2 max-w-sm mx-auto leading-relaxed font-medium">
            Tap below to download and install PocketCraft on Android.
            If your browser blocks the download, open this page in Chrome.
          </p>
          <p className="text-black/50 text-sm mb-12 max-w-sm mx-auto">
            <strong>Version:</strong> Stable release | <strong>Minimum Android:</strong> 8.0+
          </p>

          <button
            onClick={handleDownloadClick}
            className="btn-duo inline-flex items-center justify-center px-12 py-4 text-sm uppercase tracking-wider font-bold"
          >
            Download APK
          </button>

          {showInstallModal && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4">
              <div className="bg-white rounded-xl p-7 md:p-8 max-w-lg w-full mx-auto text-left shadow-2xl border-2 border-black/10">
                <h3 className="text-2xl font-bold text-black mb-3">How to Download and Install</h3>
                <p className="text-black/65 mb-5">Follow these steps on Android:</p>
                <ol className="list-decimal pl-6 text-black/75 space-y-2 mb-6">
                  <li>Tap Download APK from this popup.</li>
                  <li>Wait for the APK file to finish downloading.</li>
                  <li>Open the downloaded APK from notifications or Files.</li>
                  <li>If prompted, allow install from unknown apps for your browser or file manager.</li>
                  <li>Tap Install and open PocketCraft.</li>
                </ol>
                <div className="flex flex-col sm:flex-row gap-3">
                  <button
                    onClick={handleModalDownloadClick}
                    className="btn-duo px-6 py-3 font-bold uppercase text-sm"
                  >
                    Download APK
                  </button>
                  <button
                    onClick={handleInstallModalClose}
                    className="px-6 py-3 border-2 border-black/20 rounded-lg hover:bg-black/5 transition-colors font-bold uppercase text-sm"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          )}

          {showDiscordModal && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
              <div className="bg-white rounded-lg p-8 max-w-sm mx-auto text-center shadow-2xl">
                <h3 className="text-2xl font-bold text-black mb-4">Join Our Community!</h3>
                <p className="text-black/60 mb-6">
                  Love what we're building? Join thousands of players on Discord for tips, support, and updates.
                </p>
                <div className="flex flex-col gap-3">
                  <a
                    href="https://discord.com/invite/nc7ceYWVfT"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-duo px-8 py-3 inline-flex items-center justify-center gap-2 font-bold uppercase text-sm"
                  >
                    <DiscordIcon className="w-4 h-4" />
                    Join Discord
                  </a>
                  <button
                    onClick={() => setShowDiscordModal(false)}
                    className="px-8 py-3 border-2 border-black/20 rounded-lg hover:bg-black/5 transition-colors font-bold uppercase text-sm"
                  >
                    Maybe Later
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Home;
