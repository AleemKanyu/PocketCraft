import { motion } from "framer-motion";
import { Spotlight } from "./ui/spotlight";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

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

export function ModernHero() {
  const scrollToDownload = () => {
    document.getElementById("download")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section className="relative min-h-screen w-full flex flex-col items-center justify-center overflow-hidden bg-white pt-32 pb-20">
      {/* Colorful gradient blobs */}
      <div className="absolute top-20 left-10 w-72 h-72 bg-[#7FE620]/20 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute top-1/3 right-20 w-96 h-96 bg-[#1CB0F6]/15 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 left-1/3 w-80 h-80 bg-[#FFD900]/10 rounded-full blur-3xl pointer-events-none" />

      {/* Subtle grid background */}
      <div className="absolute inset-0 z-0 opacity-5 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#1f1f1f_1px,transparent_1px)] [background-size:64px_64px]" />
      </div>

      {/* Floating Minecraft Assets */}
      <div className="hidden md:block">
        <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="-35vw" initialY="-30vh" flipX />
        <FloatingItem src={MINECRAFT_ICONS.hero.pickaxe} delay={0.8} initialX="-40vw" initialY="20vh" flipX />
        <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="35vw" initialY="-30vh" />
        <FloatingItem src={MINECRAFT_ICONS.hero.trident} delay={0.8} initialX="40vw" initialY="20vh" />
      </div>

      <div className="container relative z-10 px-6 mx-auto flex flex-col items-center text-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5 }}
          className="mb-8 flex flex-wrap items-center justify-center gap-3"
        >
          <div className="bg-black/5 border-2 border-black/10 px-4 py-2 text-[10px] md:text-[11px] font-semibold uppercase rounded-full text-black/60">
            Free Forever
          </div>
          <div className="bg-[#7FE620]/15 border-2 border-[#7FE620]/30 px-4 py-2 text-[10px] md:text-[11px] font-semibold uppercase rounded-full text-[#7FE620]">
            + Bedrock Crossplay
          </div>
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="text-4xl md:text-7xl text-black leading-tight mb-6 font-extrabold"
        >
          Host your <span className="text-[#7FE620] font-minecraft-word">Minecraft</span> server <br />
          <span className="text-black/60">from your phone</span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className="text-black/60 text-base md:text-lg max-w-2xl mx-auto mb-12 leading-relaxed font-medium"
        >
          Stop paying $10/month for hosting. Run a real Java Edition server on your Android device with Bedrock crossplay support. No PC. No setup. Just tap and play.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.4 }}
          className="flex flex-col sm:flex-row gap-4 flex-wrap justify-center"
        >
          <button
            onClick={scrollToDownload}
            className="btn-duo px-12 py-4 text-sm uppercase tracking-wider font-bold"
          >
            Download APK
          </button>

          <a
            href="https://discord.com/invite/nc7ceYWVfT"
            target="_blank"
            rel="noopener noreferrer"
            className="px-10 py-4 bg-[#5865F2] hover:bg-[#4752C4] text-white font-bold text-sm uppercase tracking-wider transition-all rounded-lg shadow-[0_4px_0_0_rgba(88,101,242,0.3)] inline-flex items-center gap-2"
          >
            <DiscordIcon className="w-4 h-4" />
            Join Discord
          </a>
        </motion.div>
      </div>
    </section>
  );
}
