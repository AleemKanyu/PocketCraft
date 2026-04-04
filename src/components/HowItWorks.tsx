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
    description: "One tap launches a real Java Edition PaperMC server with built-in Bedrock support on your phone.",
    tag: "1 tap",
    icon: MINECRAFT_ICONS.steps.start,
  },
  {
    number: "03",
    title: "Invite Friends",
    description: "Share a join link. Java & Bedrock players can connect directly to your phone — no IP needed.",
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
          className="w-12 h-12 bg-[#7FE620] border-none rounded-full flex items-center justify-center shrink-0 z-10 font-bold text-black shadow-[0_4px_0_0_rgba(0,0,0,0.1)]"
        >
          <span className="text-sm tabular-nums">{step.number}</span>
        </motion.div>
        {!isLast && (
          <motion.div
            initial={{ scaleY: 0 }}
            animate={isInView ? { scaleY: 1 } : {}}
            transition={{ duration: 0.5, delay: index * 0.12 + 0.2, ease: "easeInOut" }}
            style={{ originY: 0 }}
            className="w-1 bg-black/10 flex-1 mt-2"
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
            className="w-10 h-10 image-pixelated"
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = "/grass-block.png";
            }}
          />
          <h3 className="text-lg md:text-xl font-bold text-black uppercase tracking-tight">
            {step.title}
          </h3>
          <span className="px-4 py-1 bg-[#7FE620] text-xs font-bold text-black uppercase rounded-full w-fit shadow-[0_2px_0_0_rgba(0,0,0,0.1)]">
            {step.tag}
          </span>
        </div>
        <p className="text-sm md:text-base text-black/60 font-medium leading-relaxed max-w-sm">{step.description}</p>
      </motion.div>
    </div>
  );
};

export default function HowItWorks() {
  return (
    <section className="py-32 px-4 sm:px-6 bg-white border-t-4 border-black/5">
      {/* Gradient blob */}
      <div className="absolute inset-0 z-0 opacity-5 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#1f1f1f_1px,transparent_1px)] [background-size:64px_64px]" />
      </div>

      <div className="max-w-4xl mx-auto relative z-10">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between mb-24 gap-8">
          <div>
            <div className="w-12 h-1 bg-[#7FE620] mb-8" />
            <h2 className="text-3xl sm:text-4xl font-extrabold text-black uppercase">How it works</h2>
          </div>
          <p className="text-black/60 text-sm font-medium uppercase tracking-wider max-w-xs leading-relaxed">
            From download to playing in seconds. With Java & Bedrock crossplay support.
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
