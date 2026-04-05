import { Link } from "react-router-dom";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

export default function Footer() {
  return (
    <footer className="relative overflow-hidden px-4 sm:px-6 pt-24 pb-14 border-t-8 border-black bg-[#050505]">
      <div className="absolute -top-28 right-0 w-[26rem] h-[26rem] rounded-full bg-[#7FE620]/15 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-20 -left-8 w-[22rem] h-[22rem] rounded-full bg-[#1CB0F6]/20 blur-3xl pointer-events-none" />

      <div className="relative z-10 max-w-6xl mx-auto rounded-3xl border border-white/10 bg-gradient-to-br from-white/[0.08] via-white/[0.04] to-white/[0.01] backdrop-blur px-6 md:px-10 py-10 md:py-12 shadow-[0_20px_80px_rgba(0,0,0,0.5)]">
        <div className="grid md:grid-cols-[1.4fr_1fr_1fr] gap-10 md:gap-8">
          <div className="space-y-4">
            <Link to="/" className="inline-flex items-center gap-3 group">
              <img
                src={MINECRAFT_ICONS.brand}
                alt="PocketCraft"
                className="w-12 h-12 rounded-full border border-white/25 image-pixelated group-hover:scale-105 transition-transform"
                onError={(e) => {
                  e.currentTarget.onerror = null;
                  e.currentTarget.src = "/grass-block.png";
                }}
              />
              <span className="text-2xl font-minecraft text-white">PocketCraft</span>
            </Link>

            <p className="text-white/90 text-sm md:text-base font-semibold tracking-wide max-w-md">
              Build, host, and share your world from Android with no monthly server bill.
            </p>
            <p className="text-white/60 text-[11px] uppercase font-mono tracking-[0.22em]">
              Fast setup. Real servers. Community first.
            </p>
          </div>

          <div>
            <h3 className="text-xs font-mono tracking-[0.28em] text-white/50 uppercase mb-4">Explore</h3>
            <div className="grid gap-2 text-sm font-semibold">
              <a href="/" className="text-white/80 hover:text-[#7FE620] transition-colors">Home</a>
              <a href="/#faq" className="text-white/80 hover:text-[#7FE620] transition-colors">FAQ</a>
              <a href="/roadmap" className="text-white/80 hover:text-[#7FE620] transition-colors">Roadmap</a>
              <a href="/community" className="text-white/80 hover:text-[#7FE620] transition-colors">Community</a>
              <a href="/blog" className="text-white/80 hover:text-[#7FE620] transition-colors">Blog</a>
            </div>
          </div>

          <div>
            <h3 className="text-xs font-mono tracking-[0.28em] text-white/50 uppercase mb-4">Legal and Contact</h3>
            <div className="grid gap-2 text-sm font-semibold">
              <a href="/legal" className="text-white/80 hover:text-[#7FE620] transition-colors">Legal Center</a>
              <a href="/privacy" className="text-white/80 hover:text-[#7FE620] transition-colors">Privacy Policy</a>
              <a href="/terms" className="text-white/80 hover:text-[#7FE620] transition-colors">Terms of Service</a>
              <a href="https://discord.com/invite/nc7ceYWVfT" target="_blank" rel="noopener noreferrer" className="text-white/80 hover:text-[#7FE620] transition-colors">Discord</a>
              <a href="https://www.instagram.com/pocketcraftmc?igsh=NTRnZGI4MHFuYXd3&utm_source=qr" target="_blank" rel="noopener noreferrer" className="text-white/80 hover:text-[#7FE620] transition-colors">Instagram</a>
              <a href="mailto:privacy@pocketcraft.online" className="text-white/80 hover:text-[#7FE620] transition-colors">privacy@pocketcraft.online</a>
              <a href="mailto:support@pocketcraft.online" className="text-white/80 hover:text-[#7FE620] transition-colors">support@pocketcraft.online</a>
            </div>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-white/10 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-white/45 text-[11px] font-mono uppercase tracking-[0.2em]">
            © {new Date().getFullYear()} PocketCraft. All rights reserved.
          </p>
          <p className="text-white/40 text-[11px] font-mono uppercase tracking-[0.2em]">
            Made for Android Minecraft hosts.
          </p>
        </div>
      </div>
    </footer>
  );
}
