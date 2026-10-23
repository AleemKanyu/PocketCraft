import React, { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ChevronDown, Smartphone, Laptop } from "lucide-react";
import { PlayStoreIcon } from "./ui/PlayStoreIcon";
import { useTheme } from "../lib/ThemeContext";

const PLAY_URL = "https://play.google.com/store/apps/details?id=com.pockethost.app";
const APK_BASE = "https://github.com/AleemKanyu/PocketCraft/releases/latest/download";

interface DownloadDropdownProps {
  className?: string;
  /** Kept for existing callers; the main button always says "Get it on Google Play". */
  buttonText?: string;
  align?: "center" | "left" | "right";
  size?: "default" | "sm";
}

/**
 * The download block: one main Google Play button, and a small "download the APK" link under it
 * that asks which device the APK is for (phone, or Chromebook / PC emulator).
 */
export const DownloadDropdown: React.FC<DownloadDropdownProps> = ({
  className = "",
  align = "center",
  size = "default",
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { theme } = useTheme();
  const isDark = theme === "dark";

  // Close when clicking outside or pressing Escape.
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setIsOpen(false);
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
    align === "left" ? "left-0" : align === "right" ? "right-0" : "left-1/2 -translate-x-1/2";
  const itemsAlign = align === "left" ? "items-start" : align === "right" ? "items-end" : "items-center";

  const apkOptions = [
    {
      href: `${APK_BASE}/PocketHost.apk`,
      file: "PocketHost.apk",
      icon: Smartphone,
      title: "Phone or tablet",
      tag: "ARM64",
      detail: "Almost every Android phone",
    },
    {
      href: `${APK_BASE}/PocketHost-x86_64.apk`,
      file: "PocketHost-x86_64.apk",
      icon: Laptop,
      title: "Chromebook or PC emulator",
      tag: "x86_64",
      detail: "Intel / AMD: BlueStacks, Google Play Games on PC",
    },
  ];

  return (
    <div ref={containerRef} className={`relative inline-flex flex-col gap-2.5 ${itemsAlign} ${className}`}>
      {/* Main button: Google Play */}
      <motion.a
        href={PLAY_URL}
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Get PocketHost on Google Play"
        className={`btn-duo shimmer-btn ${
          size === "sm" ? "px-5 py-2.5 text-xs" : "px-7 sm:px-10 py-4 text-sm"
        } uppercase tracking-wider font-bold inline-flex items-center justify-center gap-2.5 w-full shadow-lg hover:shadow-[0_0_30px_rgba(127,230,32,0.35)] transition-all select-none`}
        whileHover={{ scale: 1.03, y: -2 }}
        whileTap={{ scale: 0.98 }}
        transition={{ type: "spring", stiffness: 400 }}
      >
        <PlayStoreIcon className={`${size === "sm" ? "w-4 h-4" : "w-5 h-5"} flex-shrink-0`} />
        <span>Get it on Google Play</span>
      </motion.a>

      {/* Small link: direct APK, then pick the device type */}
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        aria-haspopup="true"
        className={`inline-flex items-center gap-1 text-xs font-semibold underline-offset-4 hover:underline cursor-pointer ${
          isDark ? "text-white/55 hover:text-[#7FE620]" : "text-black/55 hover:text-[#58A700]"
        }`}
      >
        or download the APK
        <motion.span animate={{ rotate: isOpen ? 180 : 0 }} transition={{ duration: 0.2 }} className="inline-flex">
          <ChevronDown className="w-3.5 h-3.5" />
        </motion.span>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: 8, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.96 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className={`absolute top-full mt-2 z-50 w-72 sm:w-80 p-2 border-2 shadow-2xl ${alignmentClass} ${
              isDark
                ? "bg-[#14120e]/95 border-[#2b251e] shadow-[0_16px_40px_rgba(0,0,0,0.85)]"
                : "bg-white/95 border-[#d8cdb2] shadow-[0_16px_40px_rgba(0,0,0,0.15)]"
            } backdrop-blur-xl text-left`}
          >
            <div className="px-3 py-2 border-b border-white/5 mb-1 flex items-center justify-between">
              <span className="font-mono text-[10px] uppercase tracking-wider text-[#7FE620] font-bold">
                Which device?
              </span>
              <span className={`font-mono text-[10px] ${isDark ? "text-white/40" : "text-black/40"}`}>
                Android 8.0+
              </span>
            </div>
            {apkOptions.map(({ href, file, icon: Icon, title, tag, detail }) => (
              <a
                key={file}
                href={href}
                download={file}
                onClick={() => setIsOpen(false)}
                className={`group flex items-center gap-3 p-2.5 transition-all ${
                  isDark ? "hover:bg-white/[0.06] text-white" : "hover:bg-black/[0.04] text-black"
                }`}
              >
                <div className="w-9 h-9 rounded-lg bg-black/20 flex items-center justify-center flex-shrink-0 border border-white/10 group-hover:border-[#7FE620]/40 transition-colors">
                  <Icon className="w-5 h-5 text-[#7FE620]" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-1">
                    <span className="font-bold text-sm tracking-wide group-hover:text-[#7FE620] transition-colors">
                      {title}
                    </span>
                    <span
                      className={`px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-wider font-bold ${
                        isDark ? "bg-white/10 text-white/60" : "bg-black/5 text-black/60"
                      }`}
                    >
                      {tag}
                    </span>
                  </div>
                  <p className={`text-xs mt-0.5 truncate ${isDark ? "text-white/50" : "text-black/50"}`}>{detail}</p>
                </div>
              </a>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default DownloadDropdown;
