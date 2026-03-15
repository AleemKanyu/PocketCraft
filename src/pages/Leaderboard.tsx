import { useState, useEffect } from "react";
import { motion } from "motion/react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { Trophy, Medal, Loader2, ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

interface LeaderboardEntry {
  username: string;
  referral_count: number;
  created_at: string;
}

export default function Leaderboard() {
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchLeaderboard = async () => {
      try {
        const res = await fetch("/api/leaderboard");
        const data = await res.json();
        setLeaderboard(data.leaderboard || []);
      } catch (error) {
        console.error("Failed to fetch leaderboard", error);
      } finally {
        setLoading(false);
      }
    };

    fetchLeaderboard();
  }, []);

  return (
    <div className="min-h-screen bg-[#080808] text-white font-sans selection:bg-white/10 selection:text-white">
      <Navbar />
      
      <main className="pt-32 pb-24 px-6 min-h-[85vh] relative overflow-hidden">
        {/* Background Gradients */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-[500px] bg-gradient-to-b from-white/5 to-transparent blur-[120px] pointer-events-none opacity-50" />
        
        <div className="max-w-3xl mx-auto relative">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center mb-16"
          >
            <Link 
              to="/" 
              className="inline-flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-white/30 hover:text-white transition-colors mb-12"
            >
              <ArrowLeft size={12} />
              Back to home
            </Link>

            <div className="inline-block bg-white/5 border border-white/10 text-white rounded-full px-4 py-1.5 text-[10px] font-black mb-6 tracking-widest uppercase">
              Global Ranking
            </div>
            
            <h1 className="text-5xl md:text-7xl font-black text-white mb-6 tracking-tighter uppercase italic italic-shorthand">
              Top Players
            </h1>
            <p className="text-white/40 text-xs max-w-sm mx-auto font-bold uppercase tracking-widest leading-relaxed">
              Invite friends to climb the list. Each referral moves you up by 1 spot and brings us closer to launch.
            </p>
          </motion.div>

          <div className="bg-[#0f0f0f]/50 backdrop-blur-xl border border-white/5 rounded-[2rem] overflow-hidden">
            {loading ? (
              <div className="flex flex-col items-center justify-center py-32 gap-4">
                <Loader2 className="animate-spin text-white/20" size={32} />
                <span className="text-[10px] font-black uppercase tracking-widest text-white/20">Loading Data...</span>
              </div>
            ) : leaderboard.length === 0 ? (
              <div className="text-center py-32">
                <p className="text-[10px] font-black uppercase tracking-widest text-white/20">The list is empty. Be the first to join.</p>
              </div>
            ) : (
              <div className="divide-y divide-white/5">
                {leaderboard.map((user, index) => (
                  <motion.div
                    key={index}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.05 }}
                    className="flex items-center p-6 hover:bg-white/[0.02] transition-colors group"
                  >
                    <div className="w-16 flex-shrink-0 flex justify-center">
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-black ${
                        index === 0 ? "bg-white text-black shadow-[0_0_20px_rgba(255,255,255,0.3)]" : 
                        index === 1 ? "bg-white/20 text-white" : 
                        index === 2 ? "bg-white/10 text-white" : 
                        "text-white/20 border border-white/5"
                      }`}>
                        {index + 1}
                      </div>
                    </div>
                    
                    <div className="flex-1 min-w-0 px-4">
                      <h3 className="text-sm font-black text-white uppercase tracking-widest truncate group-hover:translate-x-1 transition-transform">
                        {user.username}
                      </h3>
                      <p className="text-[9px] font-black text-white/20 uppercase tracking-[0.2em] mt-1">
                        Joined {new Date(user.created_at).toLocaleDateString()}
                      </p>
                    </div>
                    
                    <div className="flex-shrink-0 text-right pr-4">
                      <div className="text-xl font-black text-white italic italic-shorthand">
                        {user.referral_count}
                      </div>
                      <div className="text-[8px] font-black text-white/20 uppercase tracking-[0.2em]">
                        Refers
                      </div>
                    </div>
                  </motion.div>
                ))}
              </div>
            )}
          </div>
          
          <div className="mt-12 text-center">
            <p className="text-[10px] font-black uppercase tracking-[0.3em] text-white/10">
              Only top 100 shown
            </p>
          </div>
        </div>
      </main>
      
      <Footer />
    </div>
  );
}
