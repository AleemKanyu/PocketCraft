import { motion } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";
import { useTheme } from "../lib/ThemeContext";

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

// Sparkle particle component
const Sparkle = ({ delay, x, y }: { delay: number; x: string; y: string }) => (
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

  const scrollToDownload = () => {
    document.getElementById("download")?.scrollIntoView({ behavior: "smooth" });
  };

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
    <section className={`relative min-h-screen w-full flex flex-col items-center justify-center overflow-hidden pt-32 pb-20 section-transition ${
      theme === "dark" ? "bg-[#0a0a0a]" : "bg-white"
    }`}>
      {/* Animated gradient background */}
      <div className={`absolute inset-0 animate-gradient-shift pointer-events-none ${
        theme === "dark"
          ? "bg-gradient-to-br from-[#7FE620]/5 via-[#0a0a0a] to-[#1CB0F6]/5"
          : "bg-gradient-to-br from-[#7FE620]/10 via-white to-[#1CB0F6]/5"
      }`} style={{ backgroundSize: "400% 400%" }} />

      {/* Colorful gradient blobs */}
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

      {/* Sparkle particles */}
      <div className="absolute inset-0 pointer-events-none z-0">
        {sparkles.map((s, i) => (
          <Sparkle key={i} {...s} />
        ))}
      </div>

      {/* Subtle grid background */}
      <div className={`absolute inset-0 z-0 opacity-5 [mask-image:radial-gradient(ellipse_at_center,transparent_20%,black)] pointer-events-none`}>
        <div className={`absolute inset-0 ${
          theme === "dark"
            ? "bg-[radial-gradient(#ffffff_1px,transparent_1px)]"
            : "bg-[radial-gradient(#1f1f1f_1px,transparent_1px)]"
        } [background-size:64px_64px]`} />
      </div>

      {/* Floating Minecraft Assets */}
      <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="-35vw" initialY="-30vh" flipX />
      <FloatingItem src={MINECRAFT_ICONS.hero.pickaxe} delay={0.8} initialX="-40vw" initialY="20vh" flipX />
      <FloatingItem src={MINECRAFT_ICONS.hero.diamond} delay={1.5} initialX="35vw" initialY="-30vh" />
      <FloatingItem src={MINECRAFT_ICONS.hero.trident} delay={0.8} initialX="40vw" initialY="20vh" />

      <div className="container relative z-10 px-6 mx-auto flex flex-col items-center text-center">
        {/* Badges */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5 }}
          className="mb-8 flex flex-wrap items-center justify-center gap-3"
        >
          <motion.div
            whileHover={{ scale: 1.05, y: -2 }}
            className="bg-orange-100 border-2 border-orange-300 px-4 py-2 text-[10px] md:text-[11px] font-semibold uppercase rounded-full text-orange-600"
          >
            🚀 Project in Beta - Expect Some Bugs
          </motion.div>
          <motion.div
            whileHover={{ scale: 1.05, y: -2 }}
            className={`border-2 px-4 py-2 text-[10px] md:text-[11px] font-semibold uppercase rounded-full ${
              theme === "dark"
                ? "bg-white/5 border-white/10 text-white/60"
                : "bg-black/5 border-black/10 text-black/60"
            }`}
          >
            Free Forever
          </motion.div>
          <motion.div
            whileHover={{ scale: 1.05, y: -2 }}
            className="bg-[#7FE620]/15 border-2 border-[#7FE620]/30 px-4 py-2 text-[10px] md:text-[11px] font-semibold uppercase rounded-full text-[#7FE620]"
          >
            + Bedrock Crossplay
          </motion.div>
        </motion.div>

        {/* Staggered headline */}
        <motion.h1
          className={`text-4xl md:text-7xl leading-tight mb-6 font-extrabold ${
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
          className={`text-base md:text-lg max-w-2xl mx-auto mb-12 leading-relaxed font-medium ${
            theme === "dark" ? "text-white/50" : "text-black/60"
          }`}
        >
          Stop paying $10/month for hosting. Run a real Java Edition server on your Android device with Bedrock crossplay support. No PC. No setup. Just tap and play.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.8 }}
          className="flex flex-col sm:flex-row gap-4 flex-wrap justify-center"
        >
          <motion.button
            onClick={scrollToDownload}
            className="btn-duo px-12 py-4 text-sm uppercase tracking-wider font-bold"
            whileHover={{ scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400 }}
          >
            Download APK
          </motion.button>

          <motion.button
            onClick={() => document.getElementById("how-it-works")?.scrollIntoView({ behavior: "smooth" })}
            className="btn-duo-secondary px-10 py-4 text-sm uppercase tracking-wider font-bold"
            whileHover={{ scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400 }}
          >
            How it works
          </motion.button>

          <motion.a
            href="https://discord.com/invite/nc7ceYWVfT"
            target="_blank"
            rel="noopener noreferrer"
            className="px-10 py-4 bg-[#5865F2] hover:bg-[#4752C4] text-white font-bold text-sm uppercase tracking-wider transition-all rounded-lg shadow-[0_4px_0_0_rgba(88,101,242,0.3)]"
            whileHover={{ scale: 1.05, y: -3 }}
            whileTap={{ scale: 0.98 }}
            transition={{ type: "spring", stiffness: 400 }}
          >
            Join Discord
          </motion.a>
        </motion.div>
      </div>
    </section>
  );
}
