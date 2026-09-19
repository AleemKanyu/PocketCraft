import React, { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ChevronDown, Download, ExternalLink, Smartphone } from "lucide-react";
import { PlayStoreIcon } from "./ui/PlayStoreIcon";
import { useTheme } from "../lib/ThemeContext";

interface DownloadDropdownProps {
  className?: string;
  buttonText?: string;
  align?: "center" | "left" | "right";
  size?: "default" | "sm";
}

export const DownloadDropdown: React.FC<DownloadDropdownProps> = ({
  className = "",
  buttonText = "Download App",
  align = "center",
  size = "default",
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { theme } = useTheme();
  const isDark = theme === "dark";

  // Close when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("touchstart", handleClickOutside);
      document.addEventListener("keydown", handleKeyDown);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("touchstart", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const alignmentClass =
    align === "left"
      ? "left-0"
      : align === "right"
      ? "right-0"
      : "left-1/2 -translate-x-1/2";

  return (
    <div ref={containerRef} className={`relative inline-block ${className}`}>
      {/* Main Trigger Button */}
      <motion.button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        aria-haspopup="true"
        aria-label="Download options: Google Play Store or Direct APK"
        className={`btn-duo shimmer-btn ${
          size === "sm" ? "px-5 py-2.5 text-xs" : "px-7 sm:px-10 py-4 text-sm"
        } uppercase tracking-wider font-bold inline-flex items-center justify-center gap-2.5 w-full shadow-lg hover:shadow-[0_0_30px_rgba(127,230,32,0.35)] transition-all select-none cursor-pointer`}
        whileHover={{ scale: 1.03, y: -2 }}
        whileTap={{ scale: 0.98 }}
        transition={{ type: "spring", stiffness: 400 }}
      >
        <Download className={`${size === "sm" ? "w-4 h-4" : "w-5 h-5"} flex-shrink-0`} />
        <span>{buttonText}</span>
        <motion.div
          animate={{ rotate: isOpen ? 180 : 0 }}
          transition={{ duration: 0.2 }}
          className="flex-shrink-0"
        >
          <ChevronDown className="w-4 h-4" />
        </motion.div>
      </motion.button>

      {/* Animated Dropdown Menu */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: 8, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.96 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className={`absolute top-full mt-2.5 z-50 w-72 sm:w-80 p-2 border-2 shadow-2xl ${alignmentClass} ${
              isDark
                ? "bg-[#14120e]/95 border-[#2b251e] shadow-[0_16px_40px_rgba(0,0,0,0.85)]"
                : "bg-white/95 border-[#d8cdb2] shadow-[0_16px_40px_rgba(0,0,0,0.15)]"
            } backdrop-blur-xl text-left`}
          >
            {/* Header / Subtitle */}
            <div className="px-3 py-2 border-b border-white/5 mb-1 flex items-center justify-between">
              <span className="font-mono text-[10px] uppercase tracking-wider text-[#7FE620] font-bold">
                Download PocketHost
              </span>
              <span
                className={`font-mono text-[10px] ${
                  isDark ? "text-white/40" : "text-black/40"
                }`}
              >
                Android 8.0+
              </span>
            </div>

            {/* Option 1: Google Play Store */}
            <a
              href="https://play.google.com/store/apps/details?id=com.pockethost.app"
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => setIsOpen(false)}
              className={`group flex items-center gap-3 p-2.5 transition-all ${
                isDark
                  ? "hover:bg-white/[0.06] text-white"
                  : "hover:bg-black/[0.04] text-black"
              }`}
            >
              <div className="w-9 h-9 rounded-lg bg-black/20 flex items-center justify-center flex-shrink-0 border border-white/10 group-hover:border-[#7FE620]/40 transition-colors">
                <PlayStoreIcon className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-1">
                  <span className="font-bold text-sm tracking-wide group-hover:text-[#7FE620] transition-colors">
                    Google Play
                  </span>
                  <span className="px-1.5 py-0.5 bg-[#7FE620]/15 text-[#7FE620] font-mono text-[9px] uppercase tracking-wider font-bold">
                    Official
                  </span>
                </div>
                <p
                  className={`text-xs mt-0.5 truncate ${
                    isDark ? "text-white/50" : "text-black/50"
                  }`}
                >
                  Get it on Google Play
                </p>
              </div>
            </a>

            {/* Divider */}
            <div
              className={`my-1 border-t ${
                isDark ? "border-white/5" : "border-black/5"
              }`}
            />

            {/* Option 2: Direct APK Download */}
            <a
              href="https://github.com/AleemKanyu/PocketCraft/releases/latest/download/PocketHost.apk"
              download="PocketHost.apk"
              onClick={() => setIsOpen(false)}
              className={`group flex items-center gap-3 p-2.5 transition-all ${
                isDark
                  ? "hover:bg-white/[0.06] text-white"
                  : "hover:bg-black/[0.04] text-black"
              }`}
            >
              <div className="w-9 h-9 rounded-lg bg-black/20 flex items-center justify-center flex-shrink-0 border border-white/10 group-hover:border-[#7FE620]/40 transition-colors">
                <Smartphone className="w-5 h-5 text-[#7FE620]" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-1">
                  <span className="font-bold text-sm tracking-wide group-hover:text-[#7FE620] transition-colors">
                    Direct APK
                  </span>
                  <span
                    className={`px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-wider font-bold ${
                      isDark
                        ? "bg-white/10 text-white/60"
                        : "bg-black/5 text-black/60"
                    }`}
                  >
                    .apk
                  </span>
                </div>
                <p
                  className={`text-xs mt-0.5 truncate ${
                    isDark ? "text-white/50" : "text-black/50"
                  }`}
                >
                  Download standalone package
                </p>
              </div>
            </a>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default DownloadDropdown;
