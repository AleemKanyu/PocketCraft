import { motion } from "motion/react";
import { Link, useLocation } from "react-router-dom";

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
    <nav className="fixed top-0 left-0 right-0 z-50 bg-[#080808]/95 backdrop-blur-md border-b border-white/5">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2.5 group">
          <div className="w-7 h-7 rounded-lg bg-white flex items-center justify-center transition-transform group-hover:scale-105">
            <div className="w-2.5 h-2.5 bg-black rounded-sm" />
          </div>
          <span className="font-bold text-base text-white tracking-tight">PocketCraft</span>
        </Link>
        
        <div className="flex items-center gap-4 sm:gap-6">
          <Link 
            to="/leaderboard"
            className="text-white/40 hover:text-white font-medium text-xs tracking-wide transition-colors uppercase"
          >
            Leaderboard
          </Link>
          <div className="relative group">
            {/* Smooth blur behind button */}
            <div className="absolute inset-0 bg-white/20 blur-md rounded-full opacity-0 group-hover:opacity-100 transition-opacity" />
            <motion.button
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
              onClick={scrollToWaitlist}
              className="relative bg-white px-5 py-1.5 rounded-full text-black text-xs font-bold transition-transform"
            >
              Join
            </motion.button>
          </div>
        </div>
      </div>
    </nav>
  );
}
