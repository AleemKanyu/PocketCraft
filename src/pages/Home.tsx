import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Features from "../components/Features";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { useState } from "react";

const Home = () => {
  const apkUrl = "/api/apk/download";
  const [showDiscordModal, setShowDiscordModal] = useState(false);

  const handleApkDownload = () => {
    setShowDiscordModal(true);
  };

  return (
    <div className="min-h-screen bg-white text-black selection:bg-[#7FE620] selection:text-black font-sans">
      <Navbar />
      <ModernHero />

      <div id="how-it-works">
        <HowItWorks />
      </div>

      <div id="features">
        <Features />
      </div>

      <Screenshots />

      <section id="download" className="py-32 px-6 bg-white relative overflow-hidden border-t-4 border-black/5">
        {/* Gradient blobs */}
        <div className="absolute top-1/3 right-0 w-96 h-96 bg-[#7FE620]/10 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-xl mx-auto text-center relative z-10">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-orange-100 border-2 border-orange-300 mb-8">
            <span className="text-xs font-bold text-orange-600 tracking-wider uppercase">
              🚀 Project in Beta
            </span>
          </div>
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
            <span className="text-xs font-bold text-[#7FE620] tracking-wider uppercase">
              Ready to play?
            </span>
          </div>
          <h2 className="text-4xl md:text-5xl font-extrabold text-black tracking-tight leading-tight mb-6">
            Download PocketCraft APK
          </h2>
          <p className="text-black/60 text-base mb-2 max-w-sm mx-auto leading-relaxed font-medium">
            Tap below to download and install PocketCraft on Android.
            If your browser blocks the download, open this page in Chrome.
          </p>
          <p className="text-black/50 text-sm mb-12 max-w-sm mx-auto">
            <strong>Version:</strong> v0.0.1-Beta | <strong>Minimum Android:</strong> 8.0+
          </p>

          <a
            href={apkUrl}
            download
            onClick={() => {
              handleApkDownload();
            }}
            className="btn-duo inline-flex items-center justify-center px-12 py-4 text-sm uppercase tracking-wider font-bold"
          >
            Download APK
          </a>

          <p className="text-black/40 text-xs mt-8 uppercase tracking-wider font-semibold">
            After download: open file - allow unknown apps - install.
          </p>

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
                    className="btn-duo px-8 py-3 inline-flex items-center justify-center font-bold uppercase text-sm"
                  >
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
