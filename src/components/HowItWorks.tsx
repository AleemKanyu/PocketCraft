import React, { useRef } from "react";
import { motion, useInView } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

const steps = [
  {
    number: "01",
    title: "Download the App",
    description: "Install PocketCraft on your Android device. No root required.",
    tag: "2 seconds",
    icon: MINECRAFT_ICONS.steps.download,
  },
  {
    number: "02",
    title: "Start Your Server",
    description: "One tap launches a real Java Edition PaperMC server on your phone.",
    tag: "1 tap",
    icon: MINECRAFT_ICONS.steps.start,
  },
  {
    number: "03",
    title: "Invite Friends",
    description: "Share a join link. Anyone can connect directly to your phone — no IP needed.",
    tag: "instant",
    icon: MINECRAFT_ICONS.steps.invite,
  },
];

interface StepProps {
  step: {
    number: string;
    title: string;
    description: string;
    tag: string;
    icon: string;
  };
  index: number;
}

const Step: React.FC<StepProps> = ({ step, index }) => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });
  const isLast = index === steps.length - 1;

  return (
    <div ref={ref} className="relative flex gap-6 sm:gap-10">
      {/* Left: number + vertical line */}
      <div className="flex flex-col items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.5 }}
          animate={isInView ? { opacity: 1, scale: 1 } : {}}
          transition={{ duration: 0.35, delay: index * 0.12 }}
          className="w-12 h-12 bg-[#333] border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] flex items-center justify-center shrink-0 z-10"
        >
          <span className="text-[10px] font-minecraft text-white tabular-nums">{step.number}</span>
        </motion.div>
        {!isLast && (
          <motion.div
            initial={{ scaleY: 0 }}
            animate={isInView ? { scaleY: 1 } : {}}
            transition={{ duration: 0.5, delay: index * 0.12 + 0.2, ease: "easeInOut" }}
            style={{ originY: 0 }}
            className="w-1 bg-[#222] flex-1 mt-2"
          />
        )}
      </div>

      {/* Right: content */}
      <motion.div
        initial={{ opacity: 0, x: -16 }}
        animate={isInView ? { opacity: 1, x: 0 } : {}}
        transition={{ duration: 0.4, delay: index * 0.12 + 0.1 }}
        className={`pb-12 sm:pb-16 ${isLast ? "pb-0" : ""}`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-4">
          <img
            src={step.icon}
            alt={`${step.title} icon`}
            className="w-10 h-10 image-pixelated border-2 border-white/10 bg-black/20 p-1"
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = "/grass-block.png";
            }}
          />
          <h3 className="text-sm md:text-lg font-minecraft text-white uppercase tracking-wider">
            {step.title}
          </h3>
          <span className="px-3 py-1 bg-[#4CAF50] text-[8px] font-minecraft text-white uppercase border-2 border-t-[#81C784] border-l-[#81C784] border-r-[#2E7D32] border-b-[#2E7D32] w-fit">
            {step.tag}
          </span>
        </div>
        <p className="text-xs md:text-sm text-white/30 font-mono uppercase tracking-widest leading-relaxed max-w-sm">{step.description}</p>
      </motion.div>
    </div>
  );
}

export default function HowItWorks() {
  return (
    <section className="py-32 px-4 sm:px-6 bg-[#080808] border-t-8 border-[#1a1a1a]">
      <div className="max-w-4xl mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between mb-24 gap-8">
          <div>
            <div className="w-12 h-1 bg-[#4CAF50] mb-8" />
            <h2 className="text-2xl sm:text-4xl font-minecraft text-white uppercase">How it works</h2>
          </div>
          <p className="text-white/30 text-[10px] md:text-xs font-mono uppercase tracking-[0.2em] max-w-[220px] leading-relaxed">
            From download to playing in seconds.
          </p>
        </div>

        <div className="pl-2">
          {steps.map((step, i) => (
            <Step key={i} step={step} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
