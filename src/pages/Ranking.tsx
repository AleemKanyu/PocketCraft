import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { Trophy, Medal, Loader2, ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";
import { PatternText } from "../components/ui/pattern-text";

interface LeaderboardEntry {
  username: string;
  referral_count: number;
  created_at: string;
}

export default function Ranking() {
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
              className="inline-flex items-center gap-3 px-8 py-3 bg-white/5 border border-white/10 text-[9px] font-black uppercase tracking-widest text-white/30 hover:text-white hover:bg-white/10 transition-all mb-12"
            >
              <ArrowLeft size={12} />
              Back to home
            </Link>

            <div className="bg-[#4CAF50] text-white px-8 py-3 text-[10px] font-minecraft mb-12 uppercase border-4 border-t-[#81C784] border-l-[#81C784] border-r-[#2E7D32] border-b-[#2E7D32] inline-block">
              Global Ranking
            </div>
            
            <h1 className="text-4xl md:text-7xl font-minecraft text-white uppercase mb-12 leading-tight">
              FOUNDING PLAYERS
            </h1>
            <p className="text-white/40 text-[10px] md:text-xs max-w-sm mx-auto font-mono uppercase tracking-[0.2em] leading-relaxed">
              Invite friends to climb the leaderboard. <br />
              The top 100 get access before anyone else. <br />
              No exceptions.
            </p>
          </motion.div>

          <div className="mc-card bg-[#333] border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] overflow-hidden">
            {loading ? (
              <div className="flex flex-col items-center justify-center py-32 gap-6">
                <Loader2 className="animate-spin text-white/10" size={32} />
                <span className="text-[10px] font-minecraft uppercase text-white/10">Loading Data...</span>
              </div>
            ) : leaderboard.length === 0 ? (
              <div className="text-center py-32">
                <p className="text-[10px] font-minecraft uppercase text-white/10">The list is empty. Be the first to join.</p>
              </div>
            ) : (
              <div className="divide-y-4 divide-black/20">
                {leaderboard.map((user, index) => (
                  <motion.div
                    key={index}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: index * 0.05 }}
                    className="flex items-center p-6 md:p-10 hover:bg-black/5 transition-colors group active:translate-y-1"
                  >
                    <div className="w-16 flex-shrink-0 flex justify-center">
                      <div className={`w-12 h-12 border-4 flex items-center justify-center text-[14px] font-minecraft ${
                        index === 0 ? "bg-[#FFD700] text-black border-[#FFF176]" : 
                        index === 1 ? "bg-[#C0C0C0] text-black border-[#E0E0E0]" : 
                        index === 2 ? "bg-[#CD7F32] text-black border-[#D7CCC8]" : 
                        "bg-black/20 text-white/20 border-white/5"
                      }`}>
                        {index + 1}
                      </div>
                    </div>
                    
                    <div className="flex-1 min-w-0 px-6">
                      <h3 className="text-xs md:text-sm font-minecraft text-white uppercase truncate group-hover:text-mc-green transition-colors">
                        {user.username}
                      </h3>
                      <p className="text-[10px] font-mono text-white/20 uppercase tracking-widest mt-2 group-hover:text-white/40 transition-colors">
                        Joined {new Date(user.created_at).toLocaleDateString()}
                      </p>
                    </div>
                    
                    <div className="flex-shrink-0 text-right pr-4">
                      <div className="text-2xl font-minecraft text-[#FFD700] group-hover:scale-110 transition-transform origin-right">
                        {user.referral_count}
                      </div>
                      <div className="text-[8px] font-minecraft text-white/20 uppercase mt-2">
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
              Only top 10 shown
            </p>
          </div>
        </div>
      </main>
      
      <Footer />
    </div>
  );
}
