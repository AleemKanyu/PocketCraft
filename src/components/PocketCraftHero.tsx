'use client'

import PhoneModel from "./PhoneModel";
import { Spotlight } from "./ui/spotlight"

export function PocketCraftHero() {
  const scrollToWaitlist = () => {
    document.getElementById("waitlist")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section className="pt-32 pb-12 px-6 relative overflow-hidden flex items-center justify-center min-h-screen bg-[#111311]">
      {/* Subtle green grid background */}
      <div className="absolute inset-0 grass-pattern opacity-60 pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10">
        <div className="w-full min-h-[520px] md:h-[620px] bg-[#1a1f1a]/90 relative overflow-hidden border-2 border-[#4CAF50]/30 rounded-2xl shadow-[0_8px_32px_rgba(76,175,80,0.12)]">
          <Spotlight
            className="-top-40 left-0 md:left-60 md:-top-20"
            fill="#a5d6a7"
          />

          <div className="flex flex-col md:flex-row h-full">
            {/* Left content */}
            <div className="flex-1 p-8 md:p-12 relative z-10 flex flex-col justify-center">
              {/* Modern badge */}
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#4CAF50]/10 border border-[#4CAF50]/20 mb-6 self-start">
                <div className="w-2 h-2 rounded-full bg-[#4CAF50] animate-pulse" />
                <span className="font-medium text-xs text-[#4CAF50] tracking-wide uppercase">
                  Pocket Edition
                </span>
              </div>

              <h1 className="text-4xl md:text-6xl font-black text-[#e8f5e9] tracking-tight leading-tight mb-6">
                Build Your<br />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#4CAF50] to-[#81C784]">World</span>
              </h1>

              <p className="text-[#a5d6a7] text-base md:text-lg leading-relaxed max-w-md mb-8">
                Host a real Minecraft Java Edition server — right from your Android phone. 
                No PC, no paid hosting, just tap and play.
              </p>

              <div className="flex flex-wrap gap-4">
                <button
                  onClick={scrollToWaitlist}
                  className="bg-[#4CAF50] hover:bg-[#66BB6A] px-8 py-3.5 rounded-full text-[#111] font-semibold transition-all shadow-lg shadow-[#4CAF50]/20"
                >
                  Join Waitlist
                </button>
                <button
                  onClick={() => document.getElementById("how-it-works")?.scrollIntoView({ behavior: "smooth" })}
                  className="bg-[#1a1f1a] hover:bg-[#222922] border border-[#333] px-8 py-3.5 rounded-full text-white font-medium transition-all"
                >
                  Learn More
                </button>
              </div>

              <div className="mt-8 flex items-center gap-2">
                <div className="flex -space-x-1">
                  {["#81C784","#66BB6A","#4CAF50","#43A047"].map((c, i) => (
                    <div key={i} className="w-6 h-6 rounded-full border-2 border-[#1a1f1a]" style={{ backgroundColor: c }} />
                  ))}
                </div>
                <p className="text-[#81C784] text-sm font-medium">
                  1,200+ players waiting
                </p>
              </div>
            </div>

            {/* Right content — CSS 3D Steve */}
            <div className="flex-1 relative min-h-[350px] md:min-h-full bg-gradient-to-br from-[#111811] to-[#1a2e1a] flex items-center justify-center overflow-hidden">
              {/* Smooth ambient glow behind Steve */}
              <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-[#4CAF50]/10 blur-[80px] rounded-full" />
              <div className="absolute inset-0 flex items-center justify-center top-[-10%] md:top-0">
                <PhoneModel />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}


