import { useState } from "react";
import { motion } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";
import { useTheme } from "../lib/ThemeContext";
import { useLowEndDevice } from "../hooks/useLowEndDevice";

const DownloadIcon = ({ className = "w-5 h-5" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" x2="12" y1="15" y2="3" />
  </svg>
);

const DiscordIcon = ({ className = "w-4 h-4" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
    <path d="M20.32 4.37A19.8 19.8 0 0 0 16.18 3a14.3 14.3 0 0 0-.66 1.35 18.07 18.07 0 0 0-7.03 0A14.6 14.6 0 0 0 7.82 3a19.86 19.86 0 0 0-4.14 1.37C1.04 8.31.33 12.16.68 15.95a20.3 20.3 0 0 0 5.08 2.62c.41-.57.78-1.17 1.1-1.8-.61-.23-1.2-.5-1.76-.82.15-.11.3-.23.44-.35a13.97 13.97 0 0 0 12.92 0c.15.12.29.24.44.35-.56.32-1.15.59-1.77.82.33.63.7 1.23 1.11 1.8a20.23 20.23 0 0 0 5.08-2.62c.41-4.39-.7-8.2-2.92-11.58ZM8.87 13.57c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Zm6.26 0c-1.01 0-1.84-.93-1.84-2.07 0-1.14.81-2.07 1.84-2.07 1.02 0 1.85.93 1.84 2.07 0 1.14-.82 2.07-1.84 2.07Z" />
  </svg>
);

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
    loading="lazy"
    decoding="async"
    initial={{ opacity: 0, scale: 0.5, scaleX: flipX ? -0.5 : 0.5, x: initialX, y: initialY }}
    animate={{
      opacity: 0.8,
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

// Sparkle particle component
const Sparkle = ({ delay, x, y }: { delay: number; x: string; y: string; key?: any }) => (
  <motion.div
    className="absolute w-1.5 h-1.5 rounded-full bg-[#7FE620]"
    style={{ left: x, top: y }}
    initial={{ opacity: 0, scale: 0 }}
    animate={{
      opacity: [0, 1, 0],
      scale: [0, 1.2, 0],
    }}
    transition={{
      duration: 2.5,
      delay,
      repeat: Infinity,
      ease: "easeInOut",
    }}
  />
);

export function ModernHero() {
  const { theme } = useTheme();
  const isLowEnd = useLowEndDevice();
  const [mousePos, setMousePos] = useState({ x: -1000, y: -1000 });

  const sparkles = [
    { delay: 0, x: "15%", y: "20%" },
    { delay: 0.5, x: "80%", y: "15%" },
    { delay: 1, x: "25%", y: "70%" },
    { delay: 1.5, x: "70%", y: "60%" },
    { delay: 2, x: "50%", y: "30%" },
    { delay: 0.3, x: "90%", y: "40%" },
    { delay: 1.2, x: "10%", y: "50%" },
    { delay: 0.8, x: "60%", y: "80%" },
  ];

  const headlineWords = ["Host", "your"];
  const headlineWords2 = ["from", "your", "phone"];

  return (
    <section
      onMouseMove={(e) => {
        if (isLowEnd) return;
        const rect = e.currentTarget.getBoundingClientRect();
        setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
      }}
      className="relative min-h-[100dvh] sm:min-h-screen w-full flex flex-col items-center justify-start md:justify-center overflow-x-clip pt-20 sm:pt-28 md:pt-32 pb-28 sm:pb-36 section-transition"
    >
      {/* Interactive Cursor Ambient Spotlight */}
      {!isLowEnd && (
        <div
          className="absolute inset-0 pointer-events-none transition-opacity duration-300 z-0"
          style={{
            background: `radial-gradient(600px circle at ${mousePos.x}px ${mousePos.y}px, ${
              theme === "dark" ? "rgba(127, 230, 32, 0.08)" : "rgba(127, 230, 32, 0.12)"
            }, transparent 80%)`,
          }}
        />
      )}

      {/* Animated gradient background */}
      <div className={`absolute inset-0 ${isLowEnd ? "" : "animate-gradient-shift"} pointer-events-none ${
        theme === "dark"
          ? "bg-gradient-to-br from-[#7FE620]/5 via-transparent to-[#1CB0F6]/5"
          : "bg-gradient-to-br from-[#7FE620]/10 via-transparent to-[#1CB0F6]/5"
      }`} style={{ backgroundSize: "400% 400%" }} />

      {/* Colorful gradient blobs */}
      {!isLowEnd && (
        <>
          <motion.div
            className={`absolute top-20 left-10 w-72 h-72 rounded-full blur-3xl pointer-events-none ${
              theme === "dark" ? "bg-[#7FE620]/10" : "bg-[#7FE620]/20"
            }`}
            animate={{ scale: [1, 1.2, 1], opacity: [0.3, 0.6, 0.3] }}
            transition={{ duration: 8, repeat: Infinity, ease: "easeInOut" }}
          />
          <motion.div
            className={`absolute top-1/3 right-20 w-96 h-96 rounded-full blur-3xl pointer-events-none ${
              theme === "dark" ? "bg-[#1CB0F6]/8" : "bg-[#1CB0F6]/15"
            }`}
            animate={{ scale: [1.2, 1, 1.2], opacity: [0.2, 0.5, 0.2] }}
            transition={{ duration: 10, repeat: Infinity, ease: "easeInOut" }}
          />
          <motion.div
            className={`absolute bottom-1/4 left-1/3 w-80 h-80 rounded-full blur-3xl pointer-events-none ${
              theme === "dark" ? "bg-[#FFD900]/5" : "bg-[#FFD900]/10"
            }`}
            animate={{ scale: [1, 1.3, 1] }}
            transition={{ duration: 12, repeat: Infinity, ease: "easeInOut" }}
          />
        </>
      )}

      {/* Sparkle particles */}
      {!isLowEnd && (
        <div className="absolute inset-0 pointer-events-none z-0">
          {sparkles.map((s, i) => (
            <Sparkle key={i} delay={s.delay} x={s.x} y={s.y} />
          ))}
        </div>
      )}

      {/* Subtle grid background */}
      <div className={`absolute inset-0 z-0 opacity-5 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none`}>
        <div className={`absolute inset-0 ${
          theme === "dark"
            ? "bg-[radial-gradient(#ffffff_1px,transparent_1px)]"
            : "bg-[radial-gradient(#1f1f1f_1px,transparent_1px)]"
        } [background-size:64px_64px]`} />
      </div>

      {/* Floating Minecraft assets are desktop-only so they don't overlap mobile hero text */}
      {!isLowEnd && (
        <div className="hidden md:block">
          <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="-35vw" initialY="-30vh" flipX />
          <FloatingItem src={MINECRAFT_ICONS.hero.pickaxe} delay={0.8} initialX="-40vw" initialY="20vh" flipX />
          <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="35vw" initialY="-30vh" />
          <FloatingItem src={MINECRAFT_ICONS.hero.trident} delay={0.8} initialX="40vw" initialY="20vh" />
        </div>
      )}

      {/* Mobile-only decorative items placed safely above bottom gradient */}
      <div className="md:hidden absolute bottom-24 sm:bottom-28 inset-x-0 pointer-events-none z-0">
        <motion.img
          src={MINECRAFT_ICONS.hero.pickaxe}
          alt=""
          aria-hidden="true"
          loading="lazy"
          decoding="async"
          className="absolute left-6 w-9 h-9 image-pixelated opacity-45"
          animate={isLowEnd ? undefined : { y: [0, 6, 0], rotate: [-8, 8, -8] }}
          transition={isLowEnd ? undefined : { duration: 5, repeat: Infinity, ease: "easeInOut" }}
        />
        <motion.img
          src={MINECRAFT_ICONS.hero.diamond}
          alt=""
          aria-hidden="true"
          loading="lazy"
          decoding="async"
          className="absolute right-6 w-8 h-8 image-pixelated opacity-50"
          animate={isLowEnd ? undefined : { y: [0, 6, 0], rotate: [8, -8, 8] }}
          transition={isLowEnd ? undefined : { duration: 4.5, repeat: Infinity, ease: "easeInOut" }}
        />
      </div>

      {/* Bottom transition gradient to smoothly blend into the next section */}
      <div
        className={`absolute bottom-0 inset-x-0 h-32 sm:h-44 md:h-56 pointer-events-none z-10 ${
          theme === "dark"
            ? "bg-gradient-to-b from-transparent via-[#0a0a0a]/75 to-[#0a0a0a]"
            : "bg-gradient-to-b from-transparent via-white/75 to-white"
        }`}
      />

      <div className="container relative z-20 px-4 sm:px-6 mx-auto flex flex-col items-center text-center">
        {/* Staggered headline */}
        <motion.h1
          className={`text-3xl sm:text-5xl md:text-7xl leading-[1.18] sm:leading-tight mb-4 sm:mb-6 font-extrabold ${
            theme === "dark" ? "text-white" : "text-black"
          }`}
        >
          {headlineWords.map((word, i) => (
            <motion.span
              key={i}
              initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
              animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
              transition={{ duration: 0.5, delay: 0.15 + i * 0.08 }}
              className="inline-block mr-[0.3em]"
            >
              {word}
            </motion.span>
          ))}
          <motion.span
            initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
            animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            transition={{ duration: 0.5, delay: 0.35 }}
            className="inline-block text-[#7FE620] font-minecraft-word mr-[0.3em]"
          >
            Minecraft
          </motion.span>
          <motion.span
            initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
            animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            transition={{ duration: 0.5, delay: 0.45 }}
            className="inline-block mr-[0.3em]"
          >
            server
          </motion.span>
          <br />
          {headlineWords2.map((word, i) => (
            <motion.span
              key={`w2-${i}`}
              initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
              animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
              transition={{ duration: 0.5, delay: 0.55 + i * 0.08 }}
              className={`inline-block mr-[0.3em] ${
                theme === "dark" ? "text-white/60" : "text-black/60"
              }`}
            >
              {word}
            </motion.span>
          ))}
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.7 }}
          className={`text-sm sm:text-base md:text-lg max-w-2xl mx-auto mb-8 sm:mb-12 leading-relaxed font-medium ${
            theme === "dark" ? "text-white/50" : "text-black/60"
          }`}
        >
          Stop paying $10/month for hosting. Run a real Java Edition server on your Android device with Bedrock crossplay support. No PC. No setup. Just tap and play.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.8 }}
          className="flex flex-col sm:flex-row gap-3 sm:gap-4 items-center justify-center w-full max-w-xs sm:max-w-none mx-auto"
        >
          <motion.a
            href="https://github.com/AleemKanyu/PocketCraft/releases/latest/download/PocketHost.apk"
            download="PocketHost.apk"
            className="btn-duo shimmer-btn px-6 sm:px-12 py-4 text-sm uppercase tracking-wider font-bold inline-flex items-center justify-center gap-2 w-full sm:w-auto shadow-lg hover:shadow-[0_0_30px_rgba(127,230,32,0.35)] transition-shadow"
            whileHover={{ scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400 }}
          >
            <DownloadIcon className="w-5 h-5 flex-shrink-0" />
            Download APK
          </motion.a>

          <motion.a
            href="https://discord.com/invite/nc7ceYWVfT"
            target="_blank"
            rel="noopener noreferrer"
            className="px-6 sm:px-10 py-4 bg-[#5865F2] hover:bg-[#4752C4] text-white font-bold text-sm uppercase tracking-wider transition-all rounded-lg shadow-[0_4px_0_0_rgba(88,101,242,0.3)] inline-flex items-center justify-center gap-2 w-full sm:w-auto"
            whileHover={{ scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400 }}
          >
            <DiscordIcon className="w-4 h-4" />
            Join Discord
          </motion.a>
        </motion.div>
      </div>
    </section>
  );
}
