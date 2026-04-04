import { motion } from "framer-motion";
import { useRef, useEffect, useState } from "react";
import { Smartphone, Server, Settings, Users, HardDrive } from "lucide-react";

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
}

const ScreenshotCard = ({ screenshot, index }: ScreenshotCardProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20, scale: 0.95 }}
      whileInView={{ opacity: 1, y: 0, scale: 1 }}
      viewport={{ once: false, margin: "-50px" }}
      transition={{
        duration: 0.5,
        delay: index * 0.05,
        ease: "easeOut"
      }}
      className="flex-shrink-0 snap-center group"
    >
      {/* Phone frame with glow effect */}
      <div className="relative w-44 sm:w-56 md:w-64 rounded-3xl overflow-hidden shadow-lg sm:shadow-2xl bg-black border-[6px] sm:border-[10px] md:border-[12px] border-black aspect-[9/19.5]">
        {/* Glow effect */}
        <div className="absolute inset-0 opacity-0 group-hover:opacity-20 transition-opacity duration-300 bg-gradient-to-br from-[#7FE620] to-transparent pointer-events-none rounded-3xl" />
        
        {/* Screenshot image with parallax effect */}
        <motion.img
          src={screenshot.image}
          alt={screenshot.title}
          className="w-full h-full object-cover"
          whileHover={{ scale: 1.02 }}
          transition={{ duration: 0.3 }}
          onError={(e) => {
            e.currentTarget.src =
              "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='800'%3E%3Crect fill='%23f5f5f5' width='400' height='800'/%3E%3Ctext x='50%25' y='50%25' font-size='16' fill='%23999' text-anchor='middle' dy='.3em'%3E" +
              screenshot.title +
              "%3C/text%3E%3C/svg%3E";
          }}
        />
      </div>

      {/* Label below */}
      <motion.p
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: false }}
        transition={{ duration: 0.3, delay: index * 0.05 + 0.15 }}
        className="text-center text-xs sm:text-sm font-bold text-black uppercase tracking-wider mt-2 sm:mt-4"
      >
        {screenshot.title}
      </motion.p>
    </motion.div>
  );
};

export default function Screenshots() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [currentIndex, setCurrentIndex] = useState(0);

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
    return () => container.removeEventListener("scroll", handleScroll);
  }, []);

  // Decorative icons positions (top area, spaced out)
  const iconPositions = [
    { icon: Smartphone, top: "-10%", left: "5%", delay: 0 },
    { icon: Server, top: "-5%", right: "8%", delay: 0.2 },
    { icon: Settings, top: "0%", left: "15%", delay: 0.4 },
    { icon: Users, top: "-8%", right: "20%", delay: 0.6 },
    { icon: HardDrive, top: "5%", left: "25%", delay: 0.8 },
  ];

  return (
    <section className="py-12 sm:py-24 md:py-40 px-3 sm:px-6 bg-white border-t-4 border-black/5 relative overflow-hidden">
      {/* Decorative background icons */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {iconPositions.map(({ icon: Icon, top, left, right, delay }, idx) => (
          <motion.div
            key={idx}
            initial={{ opacity: 0, y: -20 }}
            whileInView={{ opacity: 0.08, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay }}
            className="absolute"
            style={{
              top,
              left: left || "auto",
              right: right || "auto",
              width: "120px",
              height: "120px",
            }}
          >
            <Icon size={120} className="text-black w-full h-full" />
          </motion.div>
        ))}
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
          <div className="w-16 h-1 bg-[#7FE620] mx-auto mt-2 rounded-full" />
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
              <div className="w-6 sm:w-12 h-1 bg-[#7FE620]" />
              <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">Screenshots</span>
              <div className="w-6 sm:w-12 h-1 bg-[#7FE620]" />
            </div>
            <h2 className="text-2xl sm:text-4xl md:text-5xl font-extrabold text-black mb-2 sm:mb-4">
              Powerful Mobile Interface
            </h2>
            <p className="text-black/60 text-sm sm:text-lg max-w-2xl mx-auto px-2">
              Control your Minecraft server from your phone with an intuitive, polished interface.
            </p>
          </motion.div>
        </div>

        {/* Horizontally Scrollable Carousel */}
        <div className="relative max-w-6xl mx-auto">
          <div
            ref={containerRef}
            className="flex gap-3 sm:gap-6 md:gap-8 overflow-x-auto pb-6 px-2 sm:px-4 md:px-6 scrollbar-hide scroll-smooth"
            style={{
              scrollSnapType: "x mandatory",
              WebkitOverflowScrolling: "touch",
            }}
          >
            {screenshots.map((screenshot, index) => (
              <div key={screenshot.id} style={{ scrollSnapAlign: "center" }}>
                <ScreenshotCard screenshot={screenshot} index={index} />
              </div>
            ))}
          </div>
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
              whileHover={{ scale: 1.2 }}
              className={`rounded-full transition-all duration-300 ${
                currentIndex === index
                  ? "w-8 h-2 bg-[#7FE620] shadow-lg shadow-[#7FE620]/50"
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
          <p className="text-xs text-black/40 uppercase tracking-widest font-semibold">
            Swipe to explore • {currentIndex + 1} of {screenshots.length}
          </p>
        </motion.div>
      </div>
    </section>
  );
}
