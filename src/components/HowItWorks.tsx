import React, { useRef } from "react";
import { motion, useInView } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";
import { useTheme } from "../lib/ThemeContext";

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
  theme: string;
}

const Step: React.FC<StepProps> = ({ step, index, theme }) => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });
  const isLast = index === steps.length - 1;

  return (
    <div ref={ref} className="relative flex gap-6 sm:gap-10">
      {/* Left: number + vertical line */}
      <div className="flex flex-col items-center">
        <div className="relative">
          {/* Pulse ring */}
          {isInView && (
            <motion.div
              className="absolute inset-0 rounded-full bg-[#7FE620]/30"
              initial={{ scale: 1, opacity: 0.6 }}
              animate={{ scale: 2, opacity: 0 }}
              transition={{ duration: 1.5, delay: index * 0.15, repeat: 2 }}
            />
          )}
          <motion.div
            initial={{ opacity: 0, scale: 0.5 }}
            animate={isInView ? { opacity: 1, scale: 1 } : {}}
            transition={{ duration: 0.35, delay: index * 0.12, type: "spring", stiffness: 300 }}
            className="w-12 h-12 bg-[#7FE620] border-none rounded-full flex items-center justify-center shrink-0 z-10 font-bold text-black shadow-[0_4px_0_0_rgba(0,0,0,0.1)] relative"
          >
            <span className="text-sm tabular-nums">{step.number}</span>
          </motion.div>
        </div>
        {!isLast && (
          <motion.div
            initial={{ scaleY: 0 }}
            animate={isInView ? { scaleY: 1 } : {}}
            transition={{ duration: 0.7, delay: index * 0.12 + 0.2, ease: "easeInOut" }}
            style={{ originY: 0 }}
            className={`w-1 flex-1 mt-2 ${
              theme === "dark" ? "bg-white/10" : "bg-black/10"
            }`}
          />
        )}
      </div>

      {/* Right: content */}
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={isInView ? { opacity: 1, x: 0 } : {}}
        transition={{ duration: 0.5, delay: index * 0.12 + 0.1 }}
        className={`pb-12 sm:pb-16 ${isLast ? "pb-0" : ""}`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-4">
          <motion.img
            src={step.icon}
            alt={`${step.title} icon`}
            className="w-10 h-10 image-pixelated"
            whileHover={{ rotate: 15, scale: 1.2 }}
            transition={{ type: "spring", stiffness: 300 }}
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = "/grass-block.png";
            }}
          />
          <h3 className={`text-lg md:text-xl font-bold uppercase tracking-tight ${
            theme === "dark" ? "text-white" : "text-black"
          }`}>
            {step.title}
          </h3>
          <motion.span
            className="px-4 py-1 bg-[#7FE620] text-xs font-bold text-black uppercase rounded-full w-fit shadow-[0_2px_0_0_rgba(0,0,0,0.1)]"
            whileHover={{ scale: 1.1 }}
          >
            {step.tag}
          </motion.span>
        </div>
        <p className={`text-sm md:text-base font-medium leading-relaxed max-w-sm ${
          theme === "dark" ? "text-white/50" : "text-black/60"
        }`}>{step.description}</p>
      </motion.div>
    </div>
  );
};

export default function HowItWorks() {
  const { theme } = useTheme();

  return (
    <section className={`py-32 px-4 sm:px-6 border-t-4 section-transition ${
      theme === "dark"
        ? "bg-[#0a0a0a] border-white/5"
        : "bg-white border-black/5"
    }`}>
      {/* Grid bg */}
      <div className={`absolute inset-0 z-0 opacity-5 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none`}>
        <div className={`absolute inset-0 ${
          theme === "dark"
            ? "bg-[radial-gradient(#ffffff_1px,transparent_1px)]"
            : "bg-[radial-gradient(#1f1f1f_1px,transparent_1px)]"
        } [background-size:64px_64px]`} />
      </div>

      <div className="max-w-4xl mx-auto relative z-10">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.8, ease: "easeOut" }}
          className="text-center max-w-2xl mx-auto mb-20 relative z-10"
        >
          {/* Floating animated asset */}
          <motion.img
            src={MINECRAFT_ICONS.hero.diamond}
            alt="Diamond"
            className="absolute -top-10 -left-10 md:-top-16 md:-left-16 w-12 h-12 opacity-30 image-pixelated pointer-events-none"
            initial={{ y: 0, rotate: 0 }}
            animate={{ y: [-15, 10, -15], rotate: [-15, 10, -15] }}
            transition={{ duration: 6, repeat: Infinity, ease: "easeInOut" }}
          />

          <h2 className={`text-4xl md:text-5xl font-extrabold mb-6 leading-tight ${
            theme === "dark" ? "text-white" : "text-black"
          }`}>
            Host in <span className="text-[#1CB0F6]">3 Steps</span>
          </h2>
          <p className={`text-lg font-medium leading-relaxed ${
            theme === "dark" ? "text-white/50" : "text-black/60"
          }`}>
            No technical knowledge required. If you can install an app, you can host a server.
          </p>
        </motion.div>

        <div className="pl-2">
          {steps.map((step, i) => (
            <Step key={i} step={step} index={i} theme={theme} />
          ))}
        </div>
      </div>
    </section>
  );
}
