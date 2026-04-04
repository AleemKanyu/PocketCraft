import { Link } from "react-router-dom";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

export default function Footer() {
  return (
    <footer className="py-24 px-4 sm:px-6 border-t-8 border-[#1a1a1a] bg-[#080808]">
      <div className="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-12">
        <div className="flex items-center gap-4">
          <Link to="/" className="flex items-center gap-3 group">
            <img 
              src={MINECRAFT_ICONS.brand} 
              alt="PocketCraft" 
              className="w-10 h-10 image-pixelated group-hover:rotate-12 transition-transform"
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = "/grass-block.png";
              }}
            />
            <span className="text-2xl font-minecraft text-white">PocketCraft</span>
          </Link>
        </div>
        
        <p className="text-white/20 text-[10px] uppercase font-mono tracking-[0.2em] text-center sm:text-left">
          Made for players, by players
        </p>
        
        <div className="flex items-center gap-10 text-[10px] font-minecraft uppercase tracking-widest text-white/30">
          <a href="/privacy" className="hover:text-white transition-colors">Privacy</a>
          <a href="/terms" className="hover:text-white transition-colors">Terms</a>
          <a href="mailto:support@pocketcraft.online" className="hover:text-white transition-colors">Contact</a>
        </div>
      </div>
      <div className="max-w-4xl mx-auto mt-16 pt-10 border-t border-white/[0.05] text-center text-white/10 text-[10px] font-mono uppercase tracking-[0.3em]">
        © {new Date().getFullYear()} POCKETCRAFT. <br className="sm:hidden" /> ALL RIGHTS RESERVED.
      </div>
    </footer>
  );
}
