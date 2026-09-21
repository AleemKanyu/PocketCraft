import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { Star, ExternalLink, Check, Sparkles } from "lucide-react";
import { useTheme } from "../lib/ThemeContext";

declare global {
  interface Window {
    PREFERRED_SOURCE?: {
      push: (callback: (api: any) => void) => void;
      ready?: () => Promise<any>;
      api?: {
        init: (options?: any) => void;
        addPreferredSource: (options?: any) => void;
      };
    } | Array<(api: any) => void>;
  }
}

const PREFERRED_SOURCE_URL = "https://www.google.com/preferences/source?q=pockethost.online";

export function triggerGooglePreferredSource(): void {
  if (typeof window === "undefined") return;

  let triggeredViaApi = false;

  try {
    const ps = window.PREFERRED_SOURCE;
    if (ps) {
      if (Array.isArray(ps)) {
        ps.push((api: any) => {
          if (api && typeof api.addPreferredSource === "function") {
            triggeredViaApi = true;
            api.addPreferredSource({ theme: "dark" });
          }
        });
      } else if (typeof ps.push === "function") {
        ps.push((api: any) => {
          if (api && typeof api.addPreferredSource === "function") {
            triggeredViaApi = true;
            api.addPreferredSource({ theme: "dark" });
          }
        });
      } else if (ps.api && typeof ps.api.addPreferredSource === "function") {
        triggeredViaApi = true;
        ps.api.addPreferredSource({ theme: "dark" });
      }
    }
  } catch (err) {
    console.warn("Failed to trigger Google Preferred Source via publisher.js:", err);
  }

  // Safety fallback: If publisher.js didn't open the modal within 400ms (e.g. adblock or script blocked),
  // open Google's official Source Preferences page directly.
  setTimeout(() => {
    if (!triggeredViaApi) {
      window.open(PREFERRED_SOURCE_URL, "_blank", "noopener,noreferrer");
    }
  }, 400);
}

interface GooglePreferredSourceProps {
  variant?: "button" | "card" | "badge" | "compact";
  className?: string;
}

export default function GooglePreferredSource({
  variant = "card",
  className = "",
}: GooglePreferredSourceProps) {
  const { theme } = useTheme();
  const [copied, setCopied] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Initialize official Google Preferred Source container on mount and theme toggle
  useEffect(() => {
    if (typeof window === "undefined") return;

    try {
      const ps = (window.PREFERRED_SOURCE = window.PREFERRED_SOURCE || []);
      const initFn = (api: any) => {
        if (api && typeof api.init === "function") {
          api.init({
            theme: theme === "dark" ? "dark" : "light",
            lang: "en",
          });
        }
      };

      if (Array.isArray(ps)) {
        ps.push(initFn);
      } else if (typeof ps.push === "function") {
        ps.push(initFn);
      }
    } catch {
      // Ignored
    }
  }, [theme]);

  const handleAction = () => {
    setCopied(true);
    triggerGooglePreferredSource();
    setTimeout(() => setCopied(false), 3000);
  };

  // 1. Badge variant (e.g. for sub-headers or nav pills)
  if (variant === "badge") {
    return (
      <button
        onClick={handleAction}
        type="button"
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all border ${
          theme === "dark"
            ? "bg-white/5 hover:bg-white/10 text-white/90 border-white/10 hover:border-[#7FE620]/40"
            : "bg-black/5 hover:bg-black/10 text-black/90 border-black/10 hover:border-[#7FE620]"
        } ${className}`}
        title="Add PocketHost as a Preferred Source on Google"
      >
        <Star className="w-3.5 h-3.5 text-[#FBBC05] fill-[#FBBC05]" />
        <span>Star on Google</span>
      </button>
    );
  }

  // 2. Button variant (e.g. for footers, download sections)
  if (variant === "button") {
    return (
      <motion.button
        onClick={handleAction}
        type="button"
        whileHover={{ scale: 1.02, y: -2 }}
        whileTap={{ scale: 0.98 }}
        className={`inline-flex items-center justify-center gap-2.5 px-4 py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider transition-all border shadow-sm ${
          theme === "dark"
            ? "bg-[#1f1f1f] hover:bg-[#2a2a2a] text-white border-white/15 hover:border-[#7FE620]/50"
            : "bg-white hover:bg-gray-50 text-gray-900 border-gray-200 hover:border-[#7FE620]"
        } ${className}`}
        title="Follow PocketHost in Google Search, Top Stories & AI Overviews"
      >
        <div className="flex items-center gap-1.5">
          <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            />
            <path
              fill="#34A853"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="#FBBC05"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
            />
            <path
              fill="#EA4335"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
            />
          </svg>
          <Star className="w-3.5 h-3.5 text-[#FBBC05] fill-[#FBBC05]" />
        </div>
        <span>{copied ? "Opening Google..." : "Prefer on Google"}</span>
      </motion.button>
    );
  }

  // 3. Compact variant
  if (variant === "compact") {
    return (
      <div
        className={`flex items-center justify-between gap-4 p-4 rounded-xl border ${
          theme === "dark" ? "bg-white/[0.03] border-white/10" : "bg-black/[0.02] border-black/10"
        } ${className}`}
      >
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-[#FBBC05]/15 flex items-center justify-center flex-shrink-0">
            <Star className="w-5 h-5 text-[#FBBC05] fill-[#FBBC05]" />
          </div>
          <div>
            <h5 className={`text-sm font-bold ${theme === "dark" ? "text-white" : "text-gray-900"}`}>
              Google Preferred Source
            </h5>
            <p className={`text-xs ${theme === "dark" ? "text-white/50" : "text-black/60"}`}>
              Prioritize PocketHost guides in AI Overviews & Search
            </p>
          </div>
        </div>
        <button
          onClick={handleAction}
          className="px-3.5 py-2 bg-[#7FE620] hover:bg-[#6FD614] text-black text-xs font-bold uppercase tracking-wider rounded-lg transition-colors flex items-center gap-1.5 flex-shrink-0"
        >
          <span>Star Source</span>
          <ExternalLink className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  }

  // 4. Rich Card variant (Default for Blog, Community, and FAQ)
  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.4 }}
      className={`relative overflow-hidden rounded-2xl border p-6 sm:p-8 ${
        theme === "dark"
          ? "bg-gradient-to-br from-white/[0.05] via-[#121212] to-white/[0.02] border-white/10 shadow-[0_10px_40px_rgba(0,0,0,0.5)]"
          : "bg-gradient-to-br from-[#f8fdf2] via-white to-gray-50 border-black/10 shadow-lg"
      } ${className}`}
    >
      {/* Glow decorative effect */}
      <div className="absolute top-0 right-0 w-64 h-64 bg-[#7FE620]/10 rounded-full blur-3xl pointer-events-none -mr-20 -mt-20" />
      <div className="absolute bottom-0 left-0 w-48 h-48 bg-[#4285F4]/10 rounded-full blur-3xl pointer-events-none -ml-16 -mb-16" />

      <div className="relative z-10 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
        <div className="space-y-3 max-w-xl">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-[#FBBC05]/15 border border-[#FBBC05]/30 text-[#FBBC05] text-[11px] font-bold uppercase tracking-wider">
            <Sparkles className="w-3.5 h-3.5" />
            <span>New on Google Search</span>
          </div>

          <h3 className={`text-xl sm:text-2xl font-extrabold flex items-center gap-2.5 ${
            theme === "dark" ? "text-white" : "text-gray-900"
          }`}>
            <span>Make PocketHost Your Preferred Source</span>
            <Star className="w-5 h-5 text-[#FBBC05] fill-[#FBBC05] flex-shrink-0" />
          </h3>

          <p className={`text-sm leading-relaxed ${
            theme === "dark" ? "text-white/65" : "text-gray-600"
          }`}>
            Google now lets you star trusted websites. Designating <strong>PocketHost</strong> puts our 100% free server hosting guides, PaperMC plugins, and mod setups at the top of your <strong>Top Stories</strong>, <strong>AI Overviews</strong>, and search results.
          </p>
        </div>

        {/* Action Button & Google Official Slot */}
        <div className="flex flex-col sm:flex-row md:flex-col items-stretch sm:items-center md:items-end gap-3 w-full md:w-auto flex-shrink-0">
          <motion.button
            onClick={handleAction}
            type="button"
            whileHover={{ scale: 1.03, y: -2 }}
            whileTap={{ scale: 0.97 }}
            className="inline-flex items-center justify-center gap-2.5 px-6 py-3.5 bg-[#7FE620] hover:bg-[#6FD614] text-black font-bold text-xs uppercase tracking-wider rounded-xl transition-all shadow-[0_4px_14px_rgba(127,230,32,0.35)]"
          >
            {copied ? (
              <>
                <Check className="w-4 h-4" />
                <span>Opening Google...</span>
              </>
            ) : (
              <>
                <Star className="w-4 h-4 fill-black" />
                <span>Star on Google Search</span>
                <ExternalLink className="w-3.5 h-3.5 opacity-70" />
              </>
            )}
          </motion.button>

          {/* Official Google Preferred Source Button Container (auto-styled by publisher.js) */}
          <div
            ref={containerRef}
            google-add-preferred-source-btn=""
            data-theme={theme === "dark" ? "dark" : "light"}
            data-lang="en"
            className="min-h-[36px] flex items-center justify-center"
          />

          <span className={`text-[10px] tracking-wide uppercase font-medium text-center md:text-right ${
            theme === "dark" ? "text-white/30" : "text-gray-400"
          }`}>
            One-click Google preference • Free & Instant
          </span>
        </div>
      </div>
    </motion.div>
  );
}
