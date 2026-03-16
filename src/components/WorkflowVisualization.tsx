import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Smartphone, User, Globe, Signal, Zap } from "lucide-react";

interface WorkflowNode {
  username: string;
  left: number; // percentage
  top: number; // percentage
}

export default function WorkflowVisualization() {
  const [users, setUsers] = useState<WorkflowNode[]>([]);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768);
    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Fetch users
  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const res = await fetch("/api/stats");
        const data = await res.json();
        if (data.success && data.users) {
          const mappedUsers = data.users.map((u: { username: string }) => {
            // Strategic spread around the center (50, 50)
            const angle = Math.random() * Math.PI * 2;
            const distance = 25 + Math.random() * 20; // 25-45% distance from center
            return {
              username: u.username,
              left: 50 + Math.cos(angle) * distance,
              top: 50 + Math.sin(angle) * distance,
            };
          });
          setUsers(mappedUsers);
        }
      } catch (e) {
        console.error("Failed to fetch users", e);
      }
    };
    fetchUsers();
    const interval = setInterval(fetchUsers, 60000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="relative w-full h-[500px] md:h-[750px] flex items-center justify-center overflow-hidden bg-[#080808] perspective-[1000px]">
      {/* Background Grid Ambience */}
      <div className="absolute inset-0 z-0 opacity-[0.03] pointer-events-none bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:40px_40px]" />
      <div className="absolute inset-0 z-0 bg-[radial-gradient(circle_at_center,rgba(0,0,0,0)_0%,rgba(8,8,8,1)_80%)] pointer-events-none" />

      {/* SVG Connection Layer */}
      <svg className="absolute inset-0 w-full h-full z-10 pointer-events-none overflow-visible">
        <defs>
          <linearGradient id="lineGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="rgba(255,255,255,0)" />
            <stop offset="50%" stopColor="rgba(255,255,255,0.2)" />
            <stop offset="100%" stopColor="rgba(255,255,255,0)" />
          </linearGradient>
        </defs>
        
        {users.map((user, i) => (
          <g key={`connection-${i}`}>
            {/* Connection Path */}
            <motion.path
              d={`M 50% 50% L ${user.left}% ${user.top}%`}
              stroke="rgba(255,255,255,0.05)"
              strokeWidth="1"
              fill="none"
              initial={{ pathLength: 0, opacity: 0 }}
              animate={{ pathLength: 1, opacity: 1 }}
              transition={{ duration: 1.5, delay: i * 0.1 }}
            />
            
            {/* Animated Particle Flow (Reliable cx/cy animation) */}
            <motion.circle
              r="1.5"
              fill="#ffffff"
              animate={{ 
                cx: ["50%", `${user.left}%`],
                cy: ["50%", `${user.top}%`],
                opacity: [0, 1, 0]
              }}
              transition={{
                duration: 2 + Math.random() * 2,
                repeat: Infinity,
                delay: Math.random() * 5,
                ease: "linear",
              }}
              className="hidden lg:block"
            />
          </g>
        ))}
      </svg>

      {/* Central Node (Smartphone) */}
      <motion.div
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        className="absolute z-30 left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
      >
        <div className="relative group">
          <div className="absolute inset-0 bg-[#4CAF50]/20 blur-3xl rounded-full scale-150 animate-pulse" />
          <div className="p-5 md:p-8 bg-[#333] border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] relative z-10">
            <Smartphone className="w-8 h-8 md:w-12 md:h-12 text-white" />
            <motion.div
              animate={{ opacity: [0.2, 0.5, 0.2] }}
              transition={{ repeat: Infinity, duration: 2 }}
              className="absolute -top-1 -right-1"
            >
              <Zap size={14} className="text-mc-green" />
            </motion.div>
          </div>
        </div>
      </motion.div>

      {/* User Nodes */}
      <div className="absolute inset-0 z-20 w-full h-full">
        <AnimatePresence>
          {users.map((user, i) => (
            <motion.div
              key={`user-${user.username}-${i}`}
              initial={{ scale: 0, opacity: 0 }}
              animate={{ 
                left: `${user.left}%`, 
                top: `${user.top}%`,
                scale: [1, 1.05, 1],
                y: [0, -8, 0],
                opacity: 0.8
              }}
              exit={{ scale: 0, opacity: 0 }}
              transition={{ 
                left: { type: "spring", stiffness: 100, damping: 15 },
                top: { type: "spring", stiffness: 100, damping: 15 },
                scale: { repeat: Infinity, duration: 4 + Math.random() * 2, ease: "easeInOut" },
                y: { repeat: Infinity, duration: 5 + Math.random() * 3, ease: "easeInOut" },
                opacity: { duration: 0.5 }
              }}
              className="absolute -translate-x-1/2 -translate-y-1/2 pointer-events-auto"
            >
              <div className="flex flex-col items-center group cursor-pointer hover:opacity-100 transition-opacity">
                {/* Generic User Avatar */}
                <div className="w-8 h-8 md:w-12 md:h-12 bg-black/40 border-2 border-white/10 flex items-center justify-center group-hover:border-white/40 transition-all">
                  <User className="w-4 h-4 md:w-6 md:h-6 text-white/50 group-hover:text-white transition-colors" />
                </div>
                
                {/* Subtle Username Label */}
                <span className="mt-2 text-[5px] md:text-[7px] font-minecraft uppercase text-white/20 group-hover:text-white/60 transition-colors">
                  {user.username}
                </span>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {/* Stylized Information HUD */}
      <div className="absolute bottom-8 left-8 border-4 border-white/5 p-4 bg-black/60 backdrop-blur-2xl z-40 hidden md:block">
         <div className="flex items-center gap-3 mb-2">
            <Globe size={10} className="text-white/20" />
            <span className="text-[6px] font-minecraft text-white/30 uppercase">Global_Sync</span>
         </div>
         <div className="flex items-center gap-3">
            <Signal size={10} className="text-mc-green animate-pulse" />
            <span className="text-[6px] font-minecraft text-mc-green uppercase">Links_Active: {users.length}</span>
         </div>
      </div>
    </div>
  );
}

