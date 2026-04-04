import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Features from "../components/Features";
import Screenshots from "../components/Screenshots";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";

const Home = () => {
  const apkUrl = "/api/apk/download";

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
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
            <span className="text-xs font-bold text-[#7FE620] tracking-wider uppercase">
              Ready to play?
            </span>
          </div>
          <h2 className="text-4xl md:text-5xl font-extrabold text-black tracking-tight leading-tight mb-6">
            Download PocketCraft APK
          </h2>
          <p className="text-black/60 text-base mb-12 max-w-sm mx-auto leading-relaxed font-medium">
            Tap below to download and install PocketCraft on Android.
            If your browser blocks the download, open this page in Chrome.
          </p>

          <a
            href={apkUrl}
            download
            className="btn-duo inline-flex items-center justify-center px-12 py-4 text-sm uppercase tracking-wider font-bold"
          >
            Download APK
          </a>

          <p className="text-black/40 text-xs mt-8 uppercase tracking-wider font-semibold">
            After download: open file - allow unknown apps - install.
          </p>
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Home;
