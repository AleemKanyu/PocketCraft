import React from "react";
import { motion } from "framer-motion";
import PhoneModel from "./PhoneModel";
import { Spotlight } from "./ui/spotlight";

export function ModernHero() {
  const scrollToWaitlist = () => {
    document.getElementById("waitlist")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section className="relative min-h-screen w-full flex flex-col items-center justify-center overflow-hidden bg-[#080808] pt-20">
      <Spotlight
        className="-top-40 left-0 md:left-60 md:-top-20"
        fill="white"
      />
      
      {/* Background Dots */}
      <div className="absolute inset-0 z-0 opacity-20 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:32px_32px]" />
      </div>

      <div className="container relative z-10 px-6 mx-auto flex flex-col items-center text-center">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-8"
        >
          <div className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
          <span className="text-[10px] font-bold text-white/40 tracking-widest uppercase">
            Coming Soon to Android
          </span>
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.1 }}
          className="text-5xl md:text-8xl font-black text-white tracking-tighter leading-[0.9] mb-8"
        >
          Run a real Java server.<br />
          <span className="text-white/20">On your phone.</span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="text-white/40 text-lg md:text-xl max-w-2xl mb-12 leading-relaxed"
        >
          No PC, no paid hosting, no setup. One tap launches a full PaperMC 
          instance directly on your device. Just play.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className="flex flex-col sm:flex-row gap-4 mb-20"
        >
          <div className="relative group">
            {/* Smooth blur behind button */}
            <div className="absolute inset-0 bg-white/20 blur-xl rounded-full opacity-0 group-hover:opacity-100 transition-opacity" />
            <button
              onClick={scrollToWaitlist}
              className="relative px-10 py-4 bg-white text-black rounded-full font-black text-lg transition-transform hover:scale-105 active:scale-95 overflow-hidden uppercase tracking-tight"
            >
              <span className="relative z-10">Be the First to Join</span>
            </button>
          </div>
          <button
            onClick={() => document.getElementById("how-it-works")?.scrollIntoView({ behavior: "smooth" })}
            className="px-10 py-4 bg-white/5 hover:bg-white/10 border border-white/10 text-white rounded-full font-black text-lg transition-all uppercase tracking-tight"
          >
            How it works
          </button>
        </motion.div>

        {/* Interactive Phone Section */}
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 1, delay: 0.4, ease: [0.22, 1, 0.36, 1] }}
          className="relative w-full max-w-lg aspect-square md:aspect-auto md:h-[600px] flex items-center justify-center pointer-events-auto"
        >
          <div className="absolute inset-0 bg-white/5 blur-[120px] rounded-full pointer-events-none" />
          <PhoneModel />
        </motion.div>
      </div>
    </section>
  );
}
