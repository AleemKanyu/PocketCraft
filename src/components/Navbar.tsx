import { motion } from "framer-motion";
import { Link, useLocation } from "react-router-dom";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

export default function Navbar() {
  const location = useLocation();
  const isHome = location.pathname === "/";

  const scrollToWaitlist = () => {
    if (isHome) {
      document.getElementById("waitlist")?.scrollIntoView({ behavior: "smooth" });
    } else {
      window.location.href = "/#waitlist";
    }
  };

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-[#c6c6c6] border-b-8 border-t-[#ffffff] border-l-[#ffffff] border-r-[#555555] border-b-[#555555] h-20 flex items-center">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 w-full flex items-center justify-between">
        <Link to="/" className="flex items-center gap-3 group">
          <div className="w-10 h-10 border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] bg-[#333] flex items-center justify-center transition-transform group-hover:scale-105 active:translate-y-1">
            <img
              src={MINECRAFT_ICONS.brand}
              className="w-6 h-6 image-pixelated"
              alt="logo"
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = "/grass-block.png";
              }}
            />
          </div>
          <span className="font-minecraft text-xs md:text-sm text-[#333] uppercase">PocketCraft</span>
        </Link>
        
        <div className="flex items-center gap-6">
          <Link 
            to="/ranking"
            className="text-[#444] hover:text-black font-minecraft text-[10px] tracking-widest transition-colors uppercase"
          >
            Ranking
          </Link>
          <motion.button
            whileTap={{ scale: 0.95 }}
            onClick={scrollToWaitlist}
            className="btn-21st px-6 py-2 text-[10px] uppercase"
          >
            Join
          </motion.button>
        </div>
      </div>
    </nav>
  );
}
