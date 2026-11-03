import React from "react";
import { motion } from "framer-motion";
import { PlayStoreIcon } from "./ui/PlayStoreIcon";

const PLAY_URL = "https://play.google.com/store/apps/details?id=com.pockethost.app";

interface DownloadDropdownProps {
  className?: string;
  /** Kept for existing callers; the button always says "Get it on Google Play". */
  buttonText?: string;
  align?: "center" | "left" | "right";
  size?: "default" | "sm";
}

/** The download block: one Google Play button. */
export const DownloadDropdown: React.FC<DownloadDropdownProps> = ({
  className = "",
  align = "center",
  size = "default",
}) => {
  const itemsAlign = align === "left" ? "items-start" : align === "right" ? "items-end" : "items-center";

  return (
    <div className={`inline-flex flex-col ${itemsAlign} ${className}`}>
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
    </div>
  );
};

export default DownloadDropdown;
