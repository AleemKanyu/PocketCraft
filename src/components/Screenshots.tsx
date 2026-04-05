import { motion } from "framer-motion";
import { useRef, useEffect, useState } from "react";
import { useTheme } from "../lib/ThemeContext";

const screenshots = [
  { id: 1, title: "Homepage", image: "/homepage.png" },
  { id: 2, title: "Plugins Page", image: "/pluginspage.png" },
  { id: 3, title: "Server Settings", image: "/serversettings.png" },
  { id: 4, title: "Advanced Settings", image: "/advanced settings.png" },
  { id: 5, title: "Version Selection", image: "/version selection screen.png" },
  { id: 6, title: "Player Dashboard", image: "/player dashboard.png" },
  { id: 7, title: "Backup & Restore", image: "/world backup adn restore screen.png" },
];

interface ScreenshotCardProps {
  screenshot: (typeof screenshots)[0];
  index: number;
  theme: string;
}

const ScreenshotCard = ({ screenshot, index, theme }: ScreenshotCardProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30, scale: 0.9 }}
      whileInView={{ opacity: 1, y: 0, scale: 1 }}
      viewport={{ once: false, margin: "-50px" }}
      transition={{
        duration: 0.6,
        delay: index * 0.08,
        ease: [0.25, 0.46, 0.45, 0.94]
      }}
      className="flex-shrink-0 snap-center group"
    >
      {/* Phone Frame */}
      <motion.div
        className="relative phone-frame w-44 sm:w-56 md:w-64"
        whileHover={{ y: -8, rotateY: -3, rotateX: 2 }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
        style={{ perspective: 1000 }}
      >
        {/* Side buttons */}
        <div className="side-button-right" />
        <div className="side-button-left-1" />
        <div className="side-button-left-2" />
        <div className="side-button-left-3" />

        {/* Glow behind phone on hover */}
        <div
          className="absolute -inset-4 rounded-[3rem] opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none blur-xl"
          style={{
            background: `radial-gradient(circle, ${theme === "dark" ? "rgba(127,230,32,0.15)" : "rgba(127,230,32,0.1)"}, transparent 70%)`,
          }}
        />

        {/* Screen */}
        <div className="phone-screen">
          <img
            src={screenshot.image}
            alt={screenshot.title}
            className="w-full h-full object-cover"
            onError={(e) => {
              e.currentTarget.src =
                "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='800'%3E%3Crect fill='%23f5f5f5' width='400' height='800'/%3E%3Ctext x='50%25' y='50%25' font-size='16' fill='%23999' text-anchor='middle' dy='.3em'%3E" +
                screenshot.title +
                "%3C/text%3E%3C/svg%3E";
            }}
          />

          {/* Screen glare overlay */}
          <div className="absolute inset-0 pointer-events-none z-20 opacity-30 group-hover:opacity-50 transition-opacity duration-300"
            style={{
              background: "linear-gradient(135deg, rgba(255,255,255,0.2) 0%, transparent 50%, transparent 100%)",
            }}
          />
        </div>
      </motion.div>

      {/* Label below */}
      <motion.p
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: false }}
        transition={{ duration: 0.3, delay: index * 0.08 + 0.2 }}
        className={`text-center text-xs sm:text-sm font-bold uppercase tracking-wider mt-4 sm:mt-6 ${
          theme === "dark" ? "text-white/70" : "text-black"
        }`}
      >
        {screenshot.title}
      </motion.p>
    </motion.div>
  );
};

export default function Screenshots() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const { theme } = useTheme();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const scrollLeft = container.scrollLeft;
      const cardWidth = container.scrollWidth / screenshots.length;
      const index = Math.round(scrollLeft / cardWidth);
      setCurrentIndex(Math.min(index, screenshots.length - 1));
    };

    container.addEventListener("scroll", handleScroll);
    
    const scrollInterval = setInterval(() => {
      if (!container) return;
      const scrollLeft = container.scrollLeft;
      const cardWidth = container.scrollWidth / screenshots.length;
      const index = Math.round(scrollLeft / cardWidth);
      let nextIndex = index + 1;
      if (nextIndex >= screenshots.length) {
        nextIndex = 0;
      }
      container.scrollTo({
        left: cardWidth * nextIndex,
        behavior: "smooth"
      });
    }, 3000);

    return () => {
      container.removeEventListener("scroll", handleScroll);
      clearInterval(scrollInterval);
    };
  }, []);

  return (
    <section className={`py-12 sm:py-24 md:py-40 px-3 sm:px-6 border-t-4 relative overflow-hidden section-transition ${
      theme === "dark"
        ? "bg-[#0a0a0a] border-white/5"
        : "bg-white border-black/5"
    }`}>
      {/* Decorative gradient */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        <motion.div
          className={`absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] rounded-full blur-3xl ${
            theme === "dark" ? "bg-[#7FE620]/5" : "bg-[#7FE620]/8"
          }`}
          animate={{ scale: [1, 1.1, 1] }}
          transition={{ duration: 8, repeat: Infinity }}
        />
      </div>

      {/* Content */}
      <div className="relative z-10">
        {/* App Name Header */}
        <motion.div
          initial={{ opacity: 0, y: -15 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-6 sm:mb-8"
        >
          <h1 className="text-2xl sm:text-3xl md:text-4xl font-black text-[#7FE620] tracking-tight">
            PocketCraft
          </h1>
          <motion.div
            className="w-16 h-1 bg-[#7FE620] mx-auto mt-2 rounded-full"
            initial={{ width: 0 }}
            whileInView={{ width: 64 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, delay: 0.2 }}
          />
        </motion.div>

        {/* Header */}
        <div className="max-w-4xl mx-auto mb-8 sm:mb-16 md:mb-20">
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4, delay: 0.1 }}
            className="text-center"
          >
            <div className="flex items-center justify-center gap-2 sm:gap-4 mb-3 sm:mb-6">
              <motion.div
                className="w-6 sm:w-12 h-1 bg-[#7FE620]"
                initial={{ width: 0 }}
                whileInView={{ width: "3rem" }}
                viewport={{ once: true }}
                transition={{ duration: 0.5 }}
              />
              <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">Screenshots</span>
              <motion.div
                className="w-6 sm:w-12 h-1 bg-[#7FE620]"
                initial={{ width: 0 }}
                whileInView={{ width: "3rem" }}
                viewport={{ once: true }}
                transition={{ duration: 0.5 }}
              />
            </div>
            <h2 className={`text-2xl sm:text-4xl md:text-5xl font-extrabold mb-2 sm:mb-4 ${
              theme === "dark" ? "text-white" : "text-black"
            }`}>
              Powerful Mobile Interface
            </h2>
            <p className={`text-sm sm:text-lg max-w-2xl mx-auto px-2 ${
              theme === "dark" ? "text-white/50" : "text-black/60"
            }`}>
              Control your Minecraft server from your phone with an intuitive, polished interface.
            </p>
          </motion.div>
        </div>

        {/* Horizontally Scrollable Carousel */}
        <div className="relative max-w-6xl mx-auto">
          <div
            ref={containerRef}
            className="flex gap-4 sm:gap-8 md:gap-10 overflow-x-auto pb-6 px-4 sm:px-8 md:px-12 scrollbar-hide scroll-smooth"
            style={{
              scrollSnapType: "x mandatory",
              WebkitOverflowScrolling: "touch",
            }}
          >
            {screenshots.map((screenshot, index) => (
              <div key={screenshot.id} style={{ scrollSnapAlign: "center" }}>
                <ScreenshotCard screenshot={screenshot} index={index} theme={theme} />
              </div>
            ))}
          </div>

          {/* Gradient fade edges */}
          <div className={`absolute left-0 top-0 bottom-0 w-12 pointer-events-none z-20 ${
            theme === "dark"
              ? "bg-gradient-to-r from-[#0a0a0a] to-transparent"
              : "bg-gradient-to-r from-white to-transparent"
          }`} />
          <div className={`absolute right-0 top-0 bottom-0 w-12 pointer-events-none z-20 ${
            theme === "dark"
              ? "bg-gradient-to-l from-[#0a0a0a] to-transparent"
              : "bg-gradient-to-l from-white to-transparent"
          }`} />
        </div>

        {/* Indicator Dots */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: 0.3 }}
          className="flex justify-center items-center gap-1.5 mt-8 sm:mt-12"
        >
          {screenshots.map((_, index) => (
            <motion.button
              key={index}
              onClick={() => {
                if (containerRef.current) {
                  const cardWidth = containerRef.current.scrollWidth / screenshots.length;
                  containerRef.current.scrollTo({
                    left: cardWidth * index,
                    behavior: "smooth"
                  });
                }
              }}
              whileHover={{ scale: 1.3 }}
              whileTap={{ scale: 0.9 }}
              className={`rounded-full transition-all duration-300 ${
                currentIndex === index
                  ? "w-8 h-2 bg-[#7FE620] shadow-lg shadow-[#7FE620]/50"
                  : theme === "dark"
                  ? "w-2 h-2 bg-white/20 hover:bg-white/40"
                  : "w-2 h-2 bg-black/20 hover:bg-black/40"
              }`}
              aria-label={`Go to screenshot ${index + 1}`}
            />
          ))}
        </motion.div>

        {/* Mobile scroll hint */}
        <motion.div
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: 0.4 }}
          className="text-center mt-6 sm:mt-8"
        >
          <p className={`text-xs uppercase tracking-widest font-semibold ${
            theme === "dark" ? "text-white/30" : "text-black/40"
          }`}>
            Swipe to explore • {currentIndex + 1} of {screenshots.length}
          </p>
        </motion.div>
      </div>
    </section>
  );
}
