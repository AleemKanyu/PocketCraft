import { ModernHero } from "../components/ModernHero";
import HowItWorks from "../components/HowItWorks";
import Features from "../components/Features";
import WaitlistForm from "../components/WaitlistForm";
import Footer from "../components/Footer";

const Home = () => {
  return (
    <div className="min-h-screen bg-[#080808] text-white selection:bg-white selection:text-black font-sans">
      <ModernHero />
      
      <div id="how-it-works">
        <HowItWorks />
      </div>

      <div id="features">
        <Features />
      </div>

      <section id="waitlist" className="py-32 px-6 bg-[#080808] relative overflow-hidden border-t border-white/5">
        <div className="max-w-xl mx-auto text-center relative z-10">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-8">
            <span className="text-[10px] font-bold text-white/40 tracking-widest uppercase">
              Early Access
            </span>
          </div>
          <h2 className="text-4xl md:text-5xl font-black text-white tracking-tight leading-tight mb-6">
            Join the waitlist
          </h2>
          <p className="text-white/40 text-sm mb-12 max-w-sm mx-auto leading-relaxed">
            We're building something special. Be among the first to turn your phone 
            into a real Minecraft server.
          </p>
          <WaitlistForm />
        </div>
      </section>

      <Footer />
    </div>
  );
};

export default Home;
