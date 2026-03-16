import { useRef, useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Spotlight } from "./ui/spotlight";
import { Link } from "react-router-dom";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

function WaitlistCounter() {
  const [count, setCount] = useState<number | null>(null);

  useEffect(() => {
    const fetchCount = async () => {
      try {
        const res = await fetch("/api/stats");
        const data = await res.json();
        setCount(data.count);
      } catch (e) {
        console.error("Failed to fetch count", e);
      }
    };
    fetchCount();
    const interval = setInterval(fetchCount, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="flex gap-1 md:gap-2">
      {(count?.toString().padStart(5, "0") || "00000").split("").map((digit, i) => (
        <div 
          key={i} 
          className="w-8 h-12 md:w-10 md:h-14 bg-[#333] border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] flex items-center justify-center text-xl md:text-2xl font-minecraft text-[#FFD700]"
          style={{ imageRendering: 'pixelated' }}
        >
          {digit}
        </div>
      ))}
    </div>
  );
}

const FloatingItem = ({
  src,
  delay,
  initialX,
  initialY,
  baseTilt = 0,
  flipX = false,
}: {
  src: string;
  delay: number;
  initialX: string;
  initialY: string;
  baseTilt?: number;
  flipX?: boolean;
}) => (
  <motion.img
    src={src}
    initial={{ opacity: 0, scale: 0.5, scaleX: flipX ? -0.5 : 0.5, x: initialX, y: initialY }}
    animate={{ 
      opacity: 0.6, 
      scale: 1,
      scaleX: flipX ? -1 : 1,
      y: ["-5%", "5%", "-5%"],
      rotate: [baseTilt - 6, baseTilt + 6, baseTilt - 6]
    }}
    transition={{ 
      y: { repeat: Infinity, duration: 4 + delay, ease: "easeInOut" },
      rotate: { repeat: Infinity, duration: 6 + delay, ease: "easeInOut" },
      opacity: { duration: 1 },
      scale: { duration: 1 }
    }}
    className="absolute w-12 h-12 md:w-20 md:h-20 image-pixelated pointer-events-none z-0"
    onError={(e) => {
      e.currentTarget.onerror = null;
      e.currentTarget.src = "/grass-block.png";
    }}
  />
);

export function ModernHero() {
  const scrollToWaitlist = () => {
    document.getElementById("waitlist")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section className="relative min-h-screen w-full flex flex-col items-center justify-center overflow-hidden bg-[#080808] pt-32 pb-20">
      <Spotlight
        className="-top-40 left-0 md:left-60 md:-top-20"
        fill="white"
      />
      
      {/* Background Dots */}
      <div className="absolute inset-0 z-0 opacity-10 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:64px_64px]" />
      </div>

      {/* Floating Minecraft Assets */}
      <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="-35vw" initialY="-30vh" flipX />
      <FloatingItem src={MINECRAFT_ICONS.hero.pickaxe} delay={0.8} initialX="-40vw" initialY="20vh" flipX />
      <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="35vw" initialY="-30vh" />
      <FloatingItem src={MINECRAFT_ICONS.hero.trident} delay={0.8} initialX="40vw" initialY="20vh" />

      <div className="container relative z-10 px-6 mx-auto flex flex-col items-center text-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5 }}
          className="mb-8 flex flex-wrap items-center justify-center gap-4"
        >
          <div className="bg-[#4CAF50] text-white px-4 py-2 text-[8px] md:text-[10px] uppercase font-minecraft border-4 border-t-[#81C784] border-l-[#81C784] border-r-[#2E7D32] border-b-[#2E7D32]">
            Limited Beta
          </div>
          <div className="bg-white/5 border-2 border-white/10 px-4 py-2 text-[8px] md:text-[10px] uppercase font-minecraft text-white/50">
            Invite-Only Early Access
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.1 }}
          className="mb-12 inline-flex flex-col items-center"
        >
          <div className="text-[8px] md:text-[10px] font-minecraft uppercase tracking-wider text-white/20 mb-6 px-4 py-1 border-b border-white/5">
            Live Global Waitlist
          </div>
          <WaitlistCounter />
        </motion.div>

        <motion.h1 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="text-3xl md:text-6xl text-white leading-tight mb-10 uppercase font-minecraft"
        >
          Host your <span className="text-mc-green font-minecraft">Minecraft</span> server <br />
          <span className="text-white/20 font-minecraft font-black">from your phone</span>
        </motion.h1>

        <motion.p 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className="text-white/40 text-[9px] md:text-[11px] max-w-2xl mx-auto mb-12 uppercase font-mono tracking-[0.2em] leading-relaxed"
        >
          Stop paying $10/month to play with 3 friends. <br />
          Your phone is already on.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className="flex flex-col sm:flex-row gap-6"
        >
          <button
            onClick={scrollToWaitlist}
            className="btn-21st px-12 py-6 text-xs md:text-sm uppercase tracking-widest text-shadow-sm"
          >
            Start Hosting
          </button>
          
          <button
            onClick={() => document.getElementById("how-it-works")?.scrollIntoView({ behavior: "smooth" })}
            className="px-10 py-6 bg-white/5 border-4 border-white/10 text-white/50 hover:text-white font-minecraft text-[10px] uppercase tracking-widest transition-all"
          >
            How it works
          </button>
          
          <Link
            to="/ranking"
            className="px-10 py-6 bg-white/5 border-4 border-white/10 text-white/50 hover:text-white font-minecraft text-[10px] uppercase tracking-widest transition-all inline-flex items-center justify-center"
          >
            Global Ranking
          </Link>
        </motion.div>
      </div>
    </section>
  );
}
