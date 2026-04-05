import Navbar from "../components/Navbar";
import Footer from "../components/Footer";

export default function LegalCenter() {
  return (
    <div className="min-h-screen bg-white text-black selection:bg-[#7FE620] selection:text-black font-sans">
      <Navbar />

      <main className="relative overflow-hidden">
        <div className="absolute -top-20 -left-10 w-80 h-80 bg-[#7FE620]/20 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute top-1/3 -right-20 w-[28rem] h-[28rem] bg-[#1CB0F6]/15 rounded-full blur-3xl pointer-events-none" />

        <section className="max-w-5xl mx-auto px-6 pt-24 pb-12 relative z-10">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#7FE620]/20 border-2 border-[#7FE620] mb-8">
            <span className="text-xs font-bold text-[#1F1F1F] tracking-wider uppercase">PocketCraft Legal Center</span>
          </div>

          <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight mb-5">PocketCraft Legal Center</h1>
          <p className="text-black/60 font-medium">Last updated: April 6, 2026</p>
          <p className="text-black/50 font-medium mb-10">Policy version: 2026-04-06</p>

          <div className="rounded-2xl border-2 border-black/10 bg-white/90 p-6 md:p-8 shadow-[0_10px_40px_rgba(0,0,0,0.06)] mb-8">
            <p className="text-black/70 leading-relaxed text-base md:text-lg">
              Welcome to the PocketCraft Legal Center. This page gives you quick access to our legal documents and explains your choices.
            </p>
          </div>

          <div className="grid md:grid-cols-2 gap-5 mb-10">
            <a href="https://pocketcraft.online/privacy" className="rounded-xl border-2 border-black/10 p-5 bg-white hover:border-[#7FE620] transition-colors">
              <p className="text-xs uppercase tracking-wider text-black/50 font-bold mb-1">Quick Link</p>
              <h2 className="text-2xl font-extrabold">Privacy Policy</h2>
              <p className="text-black/60 mt-2">https://pocketcraft.online/privacy</p>
            </a>

            <a href="https://pocketcraft.online/terms" className="rounded-xl border-2 border-black/10 p-5 bg-white hover:border-[#7FE620] transition-colors">
              <p className="text-xs uppercase tracking-wider text-black/50 font-bold mb-1">Quick Link</p>
              <h2 className="text-2xl font-extrabold">Terms of Service</h2>
              <p className="text-black/60 mt-2">https://pocketcraft.online/terms</p>
            </a>
          </div>

          <div className="grid md:grid-cols-2 gap-5 mb-12">
            <a href="mailto:contact@pocketcraft.online" className="rounded-xl border-2 border-black/10 p-5 bg-[#f9fff0] hover:border-[#7FE620] transition-colors">
              <p className="text-xs uppercase tracking-wider text-black/50 font-bold mb-1">Contact</p>
              <h3 className="text-xl font-extrabold">General and Privacy Requests</h3>
              <p className="text-black/60 mt-2">contact@pocketcraft.online</p>
            </a>

            <a href="mailto:support@pocketcraft.online" className="rounded-xl border-2 border-black/10 p-5 bg-[#f6fbff] hover:border-[#7FE620] transition-colors">
              <p className="text-xs uppercase tracking-wider text-black/50 font-bold mb-1">Contact</p>
              <h3 className="text-xl font-extrabold">Support</h3>
              <p className="text-black/60 mt-2">support@pocketcraft.online</p>
            </a>
          </div>

          <div className="space-y-5">
            <section className="rounded-xl border-2 border-black/10 p-6 bg-white">
              <h2 className="text-2xl font-extrabold mb-2">About PocketCraft</h2>
              <p className="text-black/70">PocketCraft is a mobile app for hosting and managing a Minecraft server. The app is built by a solo developer who loves to game.</p>
            </section>

            <section className="rounded-xl border-2 border-black/10 p-6 bg-white">
              <h2 className="text-2xl font-extrabold mb-2">Your Privacy Choices</h2>
              <p className="text-black/70 mb-3">PocketCraft includes in-app controls for optional data processing, including:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Crash diagnostics</li>
                <li>Usage analytics</li>
                <li>Personalized ads</li>
                <li>Push announcements</li>
              </ul>
              <p className="text-black/70 mt-4">You can change these choices anytime in the app under Settings, Privacy and Legal.</p>
            </section>

            <section className="rounded-xl border-2 border-black/10 p-6 bg-white">
              <h2 className="text-2xl font-extrabold mb-2">How to Read Our Policies</h2>
              <p className="text-black/70 mb-2">Privacy Policy explains what data we collect, how we use it, retention, security, transfers, and your rights.</p>
              <p className="text-black/70">Terms of Service explains the rules for using PocketCraft and related services.</p>
            </section>

            <section className="rounded-xl border-2 border-black/10 p-6 bg-white">
              <h2 className="text-2xl font-extrabold mb-2">Children</h2>
              <p className="text-black/70">PocketCraft is not intended for children under 13 without parental involvement.</p>
            </section>

            <section className="rounded-xl border-2 border-black/10 p-6 bg-white">
              <h2 className="text-2xl font-extrabold mb-2">Policy Updates</h2>
              <p className="text-black/70">We may update our legal documents from time to time. When we do, we update the effective date and version.</p>
            </section>

            <section className="rounded-xl border-2 border-black/10 p-6 bg-white mb-6">
              <h2 className="text-2xl font-extrabold mb-3">Contact</h2>
              <p className="text-black/70">For privacy requests: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:contact@pocketcraft.online">contact@pocketcraft.online</a></p>
              <p className="text-black/70">For support: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:support@pocketcraft.online">support@pocketcraft.online</a></p>
            </section>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}