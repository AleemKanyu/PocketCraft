import { motion, AnimatePresence } from "framer-motion";
import { useRef, useEffect, useState, useCallback } from "react";
import { useTheme } from "../lib/ThemeContext";
import { useLowEndDevice } from "../hooks/useLowEndDevice";
import { ChevronLeft, ChevronRight, X, ZoomIn } from "lucide-react";

interface ScreenshotItem {
  id: number;
  number: string;
  title: string;
  tag: string;
  description: string;
  image: string;
}

const screenshots: ScreenshotItem[] = [
  {
    id: 1,
    number: "01",
    title: "Server Dashboard",
    tag: "Real-time Metrics",
    description:
      "Monitor PaperMC server status, active players, real-time TPS, RAM consumption, and server uptime with a single glance.",
    image: "/screenshot-dashboard.png",
  },
  {
    id: 2,
    number: "02",
    title: "Player Controls & Stats",
    tag: "Player Management",
    description:
      "Inspect connected Java and Bedrock players. Kick, ban, whitelist, give operator status, or adjust permissions instantly.",
    image: "/screenshot-players.png",
  },
  {
    id: 3,
    number: "03",
    title: "Operator Chat & Terminal",
    tag: "Live Console",
    description:
      "Interactive server command terminal. Run commands (/gamemode, /tp, /weather) and converse with in-game players in real time.",
    image: "/screenshot-chat.png",
  },
  {
    id: 4,
    number: "04",
    title: "Themes & Customization",
    tag: "Personalization",
    description:
      "Customize PocketHost with Minecraft-inspired visual accents, true AMOLED dark mode, and tactile haptic feedback.",
    image: "/screenshot-themes.png",
  },
  {
    id: 5,
    number: "05",
    title: "Performance & Optimization",
    tag: "Engine Tuning",
    description:
      "Fine-tune JVM memory allocation, chunk simulation distance, network compression thresholds, and tick optimizations.",
    image: "/screenshot-settings.png",
  },
  {
    id: 6,
    number: "06",
    title: "Resource Packs & Mods",
    tag: "Addon Support",
    description:
      "Install Bedrock behavior packs, Java resource packs, and Bukkit/Spigot/Paper .jar plugins directly from device storage.",
    image: "/screenshot-mods.png",
  },
  {
    id: 7,
    number: "07",
    title: "Worlds & Cloud Backups",
    tag: "Safe Storage",
    description:
      "Export, import, and backup your Minecraft worlds in seconds. Stored 100% locally on your phone so your creations are never wiped.",
    image: "/screenshot-backups.png",
  },
];

interface ScreenshotCardProps {
  screenshot: ScreenshotItem;
  index: number;
  theme: string;
  isLowEnd: boolean;
  isActive: boolean;
  onSelect: (screenshot: ScreenshotItem) => void;
}

const ScreenshotCard = ({
  screenshot,
  index,
  theme,
  isLowEnd,
  isActive,
  onSelect,
}: ScreenshotCardProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30, scale: 0.95 }}
      whileInView={{ opacity: 1, y: 0, scale: 1 }}
      viewport={{ once: true, margin: "-40px" }}
      transition={{
        duration: 0.5,
        delay: index * 0.05,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
      className="flex-shrink-0 snap-center group cursor-pointer"
      onClick={() => onSelect(screenshot)}
    >
      {/* Phone Frame */}
      <motion.div
        className={`relative phone-frame w-52 sm:w-60 md:w-64 transition-all duration-300 ${
          isActive
            ? "ring-2 ring-[#7FE620] shadow-[0_20px_50px_rgba(127,230,32,0.2)]"
            : "ring-1 ring-white/10 dark:ring-white/15 hover:ring-white/30"
        }`}
        whileHover={isLowEnd ? undefined : { y: -8, scale: 1.02 }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
      >
        {/* Side buttons */}
        <div className="side-button-right" />
        <div className="side-button-left-1" />
        <div className="side-button-left-2" />
        <div className="side-button-left-3" />

        {/* Ambient glow behind active phone */}
        {isActive && (
          <div
            className="absolute -inset-4 rounded-[3rem] opacity-70 pointer-events-none blur-2xl -z-10"
            style={{
              background: `radial-gradient(circle, ${
                theme === "dark" ? "rgba(127,230,32,0.25)" : "rgba(127,230,32,0.2)"
              }, transparent 70%)`,
            }}
          />
        )}

        {/* Screen */}
        <div className="phone-screen relative overflow-hidden bg-black">
          <img
            src={screenshot.image}
            alt={screenshot.title}
            loading="lazy"
            decoding="async"
            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            onError={(e) => {
              e.currentTarget.src =
                "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='800'%3E%3Crect fill='%231a1a1a' width='400' height='800'/%3E%3Ctext x='50%25' y='50%25' font-size='16' fill='%237FE620' text-anchor='middle' dy='.3em'%3E" +
                encodeURIComponent(screenshot.title) +
                "%3C/text%3E%3C/svg%3E";
            }}
          />

          {/* Screen glare overlay */}
          <div
            className="absolute inset-0 pointer-events-none z-10 opacity-30 group-hover:opacity-50 transition-opacity duration-300"
            style={{
              background:
                "linear-gradient(135deg, rgba(255,255,255,0.25) 0%, transparent 50%, transparent 100%)",
            }}
          />

          {/* Hover Zoom Badge */}
          <div className="absolute inset-0 z-20 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-300 bg-black/45 backdrop-blur-[2px]">
            <div className="px-3.5 py-2 rounded-sm bg-[#7FE620] text-black font-mono font-bold text-xs uppercase tracking-wider flex items-center gap-1.5 shadow-xl transform translate-y-2 group-hover:translate-y-0 transition-transform duration-300">
              <ZoomIn size={14} className="stroke-[2.5]" />
              <span>Expand Preview</span>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Label below */}
      <div className="text-center mt-4 sm:mt-5">
        <span className="font-mono text-[11px] uppercase tracking-widest text-[#7FE620] font-bold block mb-1">
          {screenshot.number} • {screenshot.tag}
        </span>
        <p
          className={`text-xs sm:text-sm font-bold tracking-wide transition-colors ${
            isActive
              ? "text-[#7FE620]"
              : theme === "dark"
              ? "text-white/80 group-hover:text-white"
              : "text-black/80 group-hover:text-black"
          }`}
        >
          {screenshot.title}
        </p>
      </div>
    </motion.div>
  );
};

export default function Screenshots() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedScreenshot, setSelectedScreenshot] = useState<ScreenshotItem | null>(null);
  const { theme } = useTheme();
  const isLowEnd = useLowEndDevice();

  // Scroll listener to update active index
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let ticking = false;
    const handleScroll = () => {
      if (ticking) return;

      ticking = true;
      requestAnimationFrame(() => {
        const scrollLeft = container.scrollLeft;
        const cardWidth = container.scrollWidth / screenshots.length;
        const index = Math.round(scrollLeft / cardWidth);
        setCurrentIndex(Math.min(Math.max(0, index), screenshots.length - 1));
        ticking = false;
      });
    };

    container.addEventListener("scroll", handleScroll, { passive: true });
    return () => container.removeEventListener("scroll", handleScroll);
  }, []);

  // Lock body scroll when popup modal is active
  useEffect(() => {
    if (selectedScreenshot) {
      const prevOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.body.style.overflow = prevOverflow;
      };
    }
  }, [selectedScreenshot]);

  // Navigate to specific index
  const scrollToIndex = useCallback((index: number) => {
    if (!containerRef.current) return;
    const cardWidth = containerRef.current.scrollWidth / screenshots.length;
    containerRef.current.scrollTo({
      left: cardWidth * index,
      behavior: "smooth",
    });
    setCurrentIndex(index);
  }, []);

  const scrollPrev = () => {
    const target = Math.max(0, currentIndex - 1);
    scrollToIndex(target);
  };

  const scrollNext = () => {
    const target = Math.min(screenshots.length - 1, currentIndex + 1);
    scrollToIndex(target);
  };

  // Modal navigation handlers
  const handleModalPrev = useCallback(() => {
    if (!selectedScreenshot) return;
    const curIdx = screenshots.findIndex((s) => s.id === selectedScreenshot.id);
    const prevIdx = curIdx > 0 ? curIdx - 1 : screenshots.length - 1;
    setSelectedScreenshot(screenshots[prevIdx]);
    scrollToIndex(prevIdx);
  }, [selectedScreenshot, scrollToIndex]);

  const handleModalNext = useCallback(() => {
    if (!selectedScreenshot) return;
    const curIdx = screenshots.findIndex((s) => s.id === selectedScreenshot.id);
    const nextIdx = curIdx < screenshots.length - 1 ? curIdx + 1 : 0;
    setSelectedScreenshot(screenshots[nextIdx]);
    scrollToIndex(nextIdx);
  }, [selectedScreenshot, scrollToIndex]);

  // Keyboard navigation for modal
  useEffect(() => {
    if (!selectedScreenshot) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setSelectedScreenshot(null);
      } else if (e.key === "ArrowLeft") {
        handleModalPrev();
      } else if (e.key === "ArrowRight") {
        handleModalNext();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selectedScreenshot, handleModalPrev, handleModalNext]);

  return (
    <section
      id="screenshots"
      className={`py-16 sm:py-24 md:py-36 px-3 sm:px-6 border-t-4 relative overflow-hidden section-transition ${
        theme === "dark" ? "border-white/5" : "border-black/5"
      }`}
    >
      {/* Decorative ambient stage glow */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        <motion.div
          className={`absolute top-1/4 left-1/2 -translate-x-1/2 w-[900px] h-[450px] rounded-full blur-3xl ${
            theme === "dark" ? "bg-[#7FE620]/5" : "bg-[#7FE620]/8"
          }`}
          animate={isLowEnd ? undefined : { scale: [1, 1.1, 1] }}
          transition={isLowEnd ? undefined : { duration: 8, repeat: Infinity }}
        />
      </div>

      {/* Content */}
      <div className="relative z-10 max-w-7xl mx-auto">
        {/* Header */}
        <div className="max-w-4xl mx-auto mb-8 sm:mb-12 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 mb-4 rounded-sm border border-[#7FE620]/40 bg-[#7FE620]/10 font-mono text-xs uppercase tracking-widest text-[#7FE620] font-bold">
            App Interface
          </div>
          <h2
            className={`text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight mb-3 sm:mb-4 ${
              theme === "dark" ? "text-white" : "text-black"
            }`}
          >
            Powerful Mobile Interface
          </h2>
          <p
            className={`text-sm sm:text-base max-w-2xl mx-auto px-2 leading-relaxed ${
              theme === "dark" ? "text-white/60" : "text-black/60"
            }`}
          >
            Built for touch-first server administration. Tap any phone to inspect real-time metrics,
            live console commands, and player controls in high-resolution detail.
          </p>
        </div>

        {/* Category Selector Tabs */}
        <div className="flex items-center justify-start sm:justify-center gap-2 overflow-x-auto pb-4 mb-8 scrollbar-hide px-2">
          {screenshots.map((item, idx) => (
            <button
              key={item.id}
              onClick={() => scrollToIndex(idx)}
              className={`font-mono text-xs px-3.5 py-2 rounded-sm whitespace-nowrap transition-all duration-200 border flex items-center gap-1.5 ${
                currentIndex === idx
                  ? "bg-[#7FE620] text-black font-bold border-[#7FE620] shadow-[0_0_15px_rgba(127,230,32,0.4)]"
                  : theme === "dark"
                  ? "bg-white/[0.03] text-white/60 border-white/10 hover:border-white/20 hover:text-white"
                  : "bg-black/[0.03] text-black/60 border-black/10 hover:border-black/20 hover:text-black"
              }`}
            >
              <span className="opacity-60">{item.number}</span>
              <span>{item.title}</span>
            </button>
          ))}
        </div>

        {/* Enhanced Carousel Stage with Outer Floating Arrows (NO side gradient masks) */}
        <div className="relative max-w-6xl mx-auto py-4">
          {/* Left Floating Arrow */}
          <button
            onClick={scrollPrev}
            disabled={currentIndex === 0}
            className={`absolute -left-2 sm:-left-6 top-[42%] -translate-y-1/2 z-30 w-12 h-12 rounded-full flex items-center justify-center transition-all duration-200 shadow-2xl border ${
              currentIndex === 0
                ? "opacity-20 cursor-not-allowed bg-black/40 border-white/10 text-white/30"
                : "bg-black/85 hover:bg-[#7FE620] border-white/20 hover:border-[#7FE620] text-white hover:text-black hover:scale-110 active:scale-95 cursor-pointer backdrop-blur-md"
            }`}
            aria-label="Previous screenshot"
          >
            <ChevronLeft size={24} className="stroke-[2.5] -translate-x-0.5" />
          </button>

          {/* Right Floating Arrow */}
          <button
            onClick={scrollNext}
            disabled={currentIndex === screenshots.length - 1}
            className={`absolute -right-2 sm:-right-6 top-[42%] -translate-y-1/2 z-30 w-12 h-12 rounded-full flex items-center justify-center transition-all duration-200 shadow-2xl border ${
              currentIndex === screenshots.length - 1
                ? "opacity-20 cursor-not-allowed bg-black/40 border-white/10 text-white/30"
                : "bg-black/85 hover:bg-[#7FE620] border-white/20 hover:border-[#7FE620] text-white hover:text-black hover:scale-110 active:scale-95 cursor-pointer backdrop-blur-md"
            }`}
            aria-label="Next screenshot"
          >
            <ChevronRight size={24} className="stroke-[2.5] translate-x-0.5" />
          </button>

          {/* Horizontally Scrollable Carousel Strip (Clear unobstructed views) */}
          <div
            ref={containerRef}
            className="flex gap-4 sm:gap-8 md:gap-10 overflow-x-auto py-6 px-4 sm:px-8 md:px-12 scrollbar-hide scroll-smooth"
            style={{
              scrollSnapType: "x mandatory",
              WebkitOverflowScrolling: "touch",
            }}
          >
            {screenshots.map((screenshot, index) => (
              <div key={screenshot.id} style={{ scrollSnapAlign: "center" }}>
                <ScreenshotCard
                  screenshot={screenshot}
                  index={index}
                  theme={theme}
                  isLowEnd={isLowEnd}
                  isActive={currentIndex === index}
                  onSelect={(item) => setSelectedScreenshot(item)}
                />
              </div>
            ))}
          </div>
        </div>

        {/* Bottom Navigation & Indicator Progress */}
        <div className="flex flex-col items-center gap-3 mt-4 sm:mt-6">
          <div className="flex justify-center items-center gap-2">
            {screenshots.map((_, index) => (
              <button
                key={index}
                onClick={() => scrollToIndex(index)}
                className={`rounded-full transition-all duration-300 ${
                  currentIndex === index
                    ? "w-8 h-2 bg-[#7FE620] shadow-[0_0_10px_rgba(127,230,32,0.6)]"
                    : theme === "dark"
                    ? "w-2 h-2 bg-white/25 hover:bg-white/50"
                    : "w-2 h-2 bg-black/25 hover:bg-black/50"
                }`}
                aria-label={`Go to screenshot ${index + 1}`}
              />
            ))}
          </div>

          <p
            className={`font-mono text-xs uppercase tracking-widest font-semibold ${
              theme === "dark" ? "text-white/40" : "text-black/50"
            }`}
          >
            {currentIndex + 1} of {screenshots.length} • Tap any phone to inspect
          </p>
        </div>
      </div>

      {/* Premium Screenshot Popup Lightbox Modal */}
      <AnimatePresence>
        {selectedScreenshot && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/90 backdrop-blur-2xl select-none"
            onClick={() => setSelectedScreenshot(null)}
          >
            {/* Modal Container */}
            <motion.div
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              transition={{ type: "spring", stiffness: 350, damping: 28 }}
              className="relative max-w-4xl w-full flex flex-col md:flex-row items-center justify-center gap-6 sm:gap-10 p-4 sm:p-8"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Close Button */}
              <button
                onClick={() => setSelectedScreenshot(null)}
                className="absolute -top-2 right-2 sm:top-2 sm:right-2 z-50 w-11 h-11 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-all duration-200 border border-white/20 hover:scale-105"
                aria-label="Close popup"
              >
                <X size={20} className="stroke-[2.5]" />
              </button>

              {/* Prev / Next Buttons in Modal */}
              <button
                onClick={handleModalPrev}
                className="absolute left-1 sm:left-2 top-1/2 -translate-y-1/2 z-40 w-12 h-12 rounded-full bg-black/70 hover:bg-[#7FE620] border border-white/20 hover:border-[#7FE620] text-white hover:text-black flex items-center justify-center transition-all duration-200 shadow-2xl hover:scale-110"
                aria-label="Previous screenshot"
              >
                <ChevronLeft size={26} className="stroke-[2.5]" />
              </button>
              <button
                onClick={handleModalNext}
                className="absolute right-1 sm:right-2 top-1/2 -translate-y-1/2 z-40 w-12 h-12 rounded-full bg-black/70 hover:bg-[#7FE620] border border-white/20 hover:border-[#7FE620] text-white hover:text-black flex items-center justify-center transition-all duration-200 shadow-2xl hover:scale-110"
                aria-label="Next screenshot"
              >
                <ChevronRight size={26} className="stroke-[2.5]" />
              </button>

              {/* Premium Phone Mockup Showcase */}
              <div className="relative phone-frame w-56 sm:w-64 md:w-72 flex-shrink-0 shadow-[0_30px_70px_rgba(0,0,0,0.95)] ring-2 ring-white/25">
                {/* Physical side buttons */}
                <div className="side-button-right" />
                <div className="side-button-left-1" />
                <div className="side-button-left-2" />
                <div className="side-button-left-3" />

                {/* Subtle outer emerald aura */}
                <div className="absolute -inset-6 rounded-[3.5rem] bg-[#7FE620]/25 blur-2xl pointer-events-none -z-10" />

                {/* High-res Screen */}
                <div className="phone-screen bg-black relative">
                  {/* Camera Punch Hole */}
                  <div className="absolute top-2.5 left-1/2 -translate-x-1/2 w-3.5 h-3.5 bg-black rounded-full z-30 border border-[#333]" />

                  <img
                    src={selectedScreenshot.image}
                    alt={selectedScreenshot.title}
                    className="w-full h-full object-cover"
                  />

                  {/* Glass Sheen */}
                  <div
                    className="absolute inset-0 pointer-events-none z-20 opacity-35"
                    style={{
                      background:
                        "linear-gradient(135deg, rgba(255,255,255,0.3) 0%, transparent 45%, transparent 100%)",
                    }}
                  />
                </div>
              </div>

              {/* Feature Description Card on Right */}
              <div className="text-left max-w-sm text-white space-y-4">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-sm border border-[#7FE620]/50 bg-[#7FE620]/10 font-mono text-xs uppercase tracking-widest text-[#7FE620] font-bold">
                  {selectedScreenshot.number} • {selectedScreenshot.tag}
                </div>
                <h3 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">
                  {selectedScreenshot.title}
                </h3>
                <p className="text-sm sm:text-base text-white/70 leading-relaxed">
                  {selectedScreenshot.description}
                </p>

                {/* Thumbnail Navigation Bar */}
                <div className="pt-3 border-t border-white/10 flex items-center gap-2 flex-wrap">
                  {screenshots.map((s, idx) => (
                    <button
                      key={s.id}
                      onClick={() => {
                        setSelectedScreenshot(s);
                        scrollToIndex(idx);
                      }}
                      className={`w-8 h-11 rounded-sm overflow-hidden border transition-all ${
                        s.id === selectedScreenshot.id
                          ? "border-[#7FE620] ring-2 ring-[#7FE620]/50 scale-110"
                          : "border-white/20 opacity-50 hover:opacity-100"
                      }`}
                      title={s.title}
                    >
                      <img src={s.image} alt={s.title} className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-2 text-xs font-mono text-white/40">
                  <span>Use ← → keys to browse</span>
                  <span>Esc to close</span>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}
