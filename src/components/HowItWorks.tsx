import React, { useRef } from "react";
import { motion, useInView } from "motion/react";

const steps = [
  {
    number: "01",
    title: "Download the App",
    description: "Install PocketCraft on your Android device. No root required.",
    tag: "2 seconds",
  },
  {
    number: "02",
    title: "Start Your Server",
    description: "One tap launches a real Java Edition PaperMC server on your phone.",
    tag: "1 tap",
  },
  {
    number: "03",
    title: "Invite Friends",
    description: "Share a join link. Anyone can connect directly to your phone — no IP needed.",
    tag: "instant",
  },
];

interface StepProps {
  step: {
    number: string;
    title: string;
    description: string;
    tag: string;
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
          className="w-9 h-9 sm:w-10 sm:h-10 rounded-full border border-white/10 bg-[#0f0f0f] flex items-center justify-center shrink-0 z-10"
        >
          <span className="text-[10px] font-bold text-white tabular-nums">{step.number}</span>
        </motion.div>
        {!isLast && (
          <motion.div
            initial={{ scaleY: 0 }}
            animate={isInView ? { scaleY: 1 } : {}}
            transition={{ duration: 0.5, delay: index * 0.12 + 0.2, ease: "easeInOut" }}
            style={{ originY: 0 }}
            className="w-px bg-gradient-to-b from-white/20 to-transparent flex-1 mt-2"
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
        <div className="flex items-center gap-3 mb-2">
          <h3 className="text-base sm:text-lg font-semibold text-white tracking-tight">
            {step.title}
          </h3>
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold border border-white/10 text-white/60 bg-white/5 uppercase tracking-wider">
            {step.tag}
          </span>
        </div>
        <p className="text-sm text-white/35 leading-relaxed max-w-xs">{step.description}</p>
      </motion.div>
    </div>
  );
}

export default function HowItWorks() {
  return (
    <section className="py-24 px-4 sm:px-6 bg-[#080808] border-t border-white/5">
      <div className="max-w-4xl mx-auto">
        {/* Header — left-aligned, editorial */}
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between mb-20 gap-4">
          <div>
            <div className="w-6 h-px bg-white/40 mb-5 rounded-full" />
            <h2 className="text-2xl sm:text-3xl font-black text-white tracking-tight leading-tight">HOW IT WORKS</h2>
          </div>
          <p className="text-white/35 text-xs max-w-[200px] sm:text-right leading-relaxed font-medium uppercase tracking-widest">
            From download to playing in seconds.
          </p>
        </div>

        {/* Vertical stepper */}
        <div className="pl-0">
          {steps.map((step, i) => (
            <Step key={i} step={step} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
