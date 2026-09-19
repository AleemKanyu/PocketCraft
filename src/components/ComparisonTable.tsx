import React from "react";
import { motion } from "framer-motion";
import { useTheme } from "../lib/ThemeContext";

type Status = "pocket" | "full" | "partial" | "none";

interface ComparisonRow {
  capability: string;
  aternos: Status;
  minehut: Status;
  paidHosts: Status;
  pocketHost: Status;
  tooltip?: string;
}

const COMPARISON_DATA: ComparisonRow[] = [
  {
    capability: "100% Free forever (no credit card)",
    aternos: "full",
    minehut: "partial",
    paidHosts: "none",
    pocketHost: "pocket",
    tooltip: "PocketHost is free to use without sneaky recurring subscription charges or payment gates.",
  },
  {
    capability: "Zero queue times (instant server start)",
    aternos: "none",
    minehut: "partial",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Aternos frequently forces 10–45 minute queues during peak hours. PocketHost boots up instantly.",
  },
  {
    capability: "No auto-shutdown on 0 players / inactivity",
    aternos: "none",
    minehut: "none",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Free cloud hosts shut your world down after 5 minutes of empty activity. PocketHost stays online as long as your phone is on.",
  },
  {
    capability: "Runs directly on your phone (No PC needed)",
    aternos: "none",
    minehut: "none",
    paidHosts: "none",
    pocketHost: "pocket",
    tooltip: "PocketHost embeds a complete native PaperMC server engine directly inside an Android app.",
  },
  {
    capability: "No port forwarding or router access needed",
    aternos: "full",
    minehut: "full",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Built-in encrypted relay tunneling connects players from anywhere without touching your router.",
  },
  {
    capability: "Zero cloud wipes (worlds never get deleted)",
    aternos: "partial",
    minehut: "partial",
    paidHosts: "partial",
    pocketHost: "pocket",
    tooltip: "Free cloud hosts wipe inactive servers after weeks of inactivity. PocketHost files stay permanently safe on your phone storage.",
  },
  {
    capability: "Custom plugins & mods (.jar drag and drop)",
    aternos: "partial",
    minehut: "partial",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Aternos & Minehut restrict you to curated lists. PocketHost lets you install any Bukkit/Spigot/Paper .jar plugin directly.",
  },
  {
    capability: "Bedrock + Java crossplay out-of-the-box",
    aternos: "partial",
    minehut: "partial",
    paidHosts: "partial",
    pocketHost: "pocket",
    tooltip: "One-tap GeyserMC integration lets friends join seamlessly from phone, console, or PC.",
  },
  {
    capability: "No forced in-game ads or lobby teleports",
    aternos: "partial",
    minehut: "none",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Minehut forces players into promotional lobbies and broadcasts chat spam. PocketHost provides a 100% clean vanilla experience.",
  },
  {
    capability: "Unlimited player slots (no paywalled caps)",
    aternos: "partial",
    minehut: "none",
    paidHosts: "partial",
    pocketHost: "pocket",
    tooltip: "Minehut strictly caps free servers to 10 slots. PocketHost sets no artificial player caps.",
  },
  {
    capability: "Live console & full server properties editor",
    aternos: "full",
    minehut: "full",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Real-time command terminal, gamerule switches, whitelist toggles, and MOTD configuration.",
  },
  {
    capability: "Works without public IP or VPN setups",
    aternos: "full",
    minehut: "full",
    paidHosts: "full",
    pocketHost: "pocket",
    tooltip: "Generate a sharable join link and invite your friends instantly anywhere in the world.",
  },
];

const PixelIcon: React.FC<{ status: Status }> = ({ status }) => {
  switch (status) {
    case "pocket":
      return (
        <span
          className="inline-block w-2.5 h-2.5 bg-[#4ade80] shadow-[0_0_8px_rgba(74,222,128,0.85)]"
          title="Included Free"
          aria-label="Included Free in PocketHost"
        />
      );
    case "full":
      return (
        <span
          className="inline-block w-2.5 h-2.5 bg-[#a89f8d]"
          title="Supported"
          aria-label="Supported"
        />
      );
    case "partial":
      return (
        <svg
          className="w-3 h-3 text-[#8a806e] inline-block"
          viewBox="0 0 12 12"
          fill="currentColor"
          title="Partial / Restricted"
          aria-label="Partial or Restricted"
        >
          <rect x="1" y="6" width="5" height="5" />
          <rect x="6" y="1" width="5" height="5" />
        </svg>
      );
    case "none":
    default:
      return (
        <span
          className="inline-block w-2.5 h-2.5 border-[1.5px] border-[#524a3c] bg-transparent"
          title="Not Supported / Paywalled"
          aria-label="Not Supported or Paywalled"
        />
      );
  }
};

export const ComparisonTable: React.FC = () => {
  const { theme } = useTheme();
  const isDark = theme === "dark";

  return (
    <section
      id="compare"
      className={`scroll-mt-20 md:scroll-mt-24 py-20 sm:py-24 px-3 sm:px-6 border-t-4 section-transition ${
        isDark ? "border-white/5" : "border-black/5"
      }`}
    >
      <div className="max-w-5xl mx-auto relative z-10">
        {/* Section Header */}
        <div className="text-center mb-10 sm:mb-12">
          <div className="inline-flex items-center gap-2 px-3 py-1 mb-4 rounded-sm border border-[#7FE620]/40 bg-[#7FE620]/10 font-mono text-xs uppercase tracking-widest text-[#7FE620] font-bold">
            Honest Comparison
          </div>
          <h2
            className={`text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight mb-4 ${
              isDark ? "text-white" : "text-black"
            }`}
          >
            How PocketHost Compares
          </h2>
          <p
            className={`text-xs sm:text-sm md:text-base max-w-2xl mx-auto leading-relaxed ${
              isDark ? "text-white/60" : "text-black/60"
            }`}
          >
            No waiting queues, no auto-shutdowns after 5 minutes of walking away, and no surprise bills.
            Here is how PocketHost stacks up against Aternos, Minehut, and traditional hosting.
          </p>
        </div>

        {/* Authentic Comparison Table Container - 100% visible on mobile without horizontal scroll */}
        <div className="border-2 border-[#2b251e] shadow-2xl overflow-hidden bg-[#14120e] w-full">
          <div>
            {/* Table Header Bar */}
            <div className="flex items-stretch border-b border-[#28221b] h-32 sm:h-36 md:h-auto select-none">
              {/* Parchment Section: Capability + Competitors */}
              <div className="w-[86.5%] sm:w-[85.5%] md:w-[85%] flex items-stretch bg-[#ece3cb] text-[#191510] font-mono font-black tracking-wider uppercase">
                {/* Capability Header */}
                <div className="w-[53.18%] sm:w-[49.12%] md:w-[47.06%] px-3 sm:px-4 md:px-6 py-3.5 flex items-end md:items-center border-r border-[#d8cdb2] text-[11px] sm:text-xs md:text-sm">
                  Capability
                </div>

                {/* Competitor: Aternos */}
                <div className="w-[15.6%] sm:w-[16.96%] md:w-[17.65%] py-2 md:py-3.5 px-0.5 sm:px-1 flex items-center justify-center text-center border-r border-[#d8cdb2]">
                  <span className="[writing-mode:vertical-rl] rotate-180 md:[writing-mode:horizontal-tb] md:rotate-0 text-[10px] sm:text-xs md:text-sm font-black tracking-widest">
                    Aternos
                  </span>
                </div>

                {/* Competitor: Minehut */}
                <div className="w-[15.6%] sm:w-[16.96%] md:w-[17.65%] py-2 md:py-3.5 px-0.5 sm:px-1 flex items-center justify-center text-center border-r border-[#d8cdb2]">
                  <span className="[writing-mode:vertical-rl] rotate-180 md:[writing-mode:horizontal-tb] md:rotate-0 text-[10px] sm:text-xs md:text-sm font-black tracking-widest">
                    Minehut
                  </span>
                </div>

                {/* Competitor: Paid Hosts */}
                <div className="w-[15.6%] sm:w-[16.96%] md:w-[17.65%] py-2 md:py-3.5 px-0.5 sm:px-1 flex items-center justify-center text-center">
                  <span className="[writing-mode:vertical-rl] rotate-180 md:[writing-mode:horizontal-tb] md:rotate-0 text-[10px] sm:text-xs md:text-sm font-black tracking-widest">
                    Paid Hosts
                  </span>
                </div>
              </div>

              {/* PocketHost Green Header Block */}
              <div className="w-[13.5%] sm:w-[14.5%] md:w-[15%] bg-[#2d8647] text-white font-mono font-black uppercase flex items-center justify-center py-2 md:py-3.5 px-0.5 sm:px-2 shadow-inner">
                <span className="[writing-mode:vertical-rl] rotate-180 md:[writing-mode:horizontal-tb] md:rotate-0 text-[10px] sm:text-xs md:text-sm tracking-widest text-center">
                  PocketHost
                </span>
              </div>
            </div>

            {/* Table Rows */}
            <div className="divide-y divide-[#221e18]">
              {COMPARISON_DATA.map((row, idx) => (
                <motion.div
                  key={row.capability}
                  initial={{ opacity: 0, y: 6 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.2, delay: Math.min(0.2, idx * 0.015) }}
                  className="flex items-stretch hover:bg-[#1b1813] transition-colors group"
                >
                  {/* Capability Column */}
                  <div className="w-[46%] sm:w-[42%] md:w-[40%] py-2.5 sm:py-3 px-2.5 sm:px-4 md:px-6 font-mono text-[11px] sm:text-xs md:text-sm text-[#ddd6c7] flex items-center border-r border-[#221e18] leading-snug">
                    <span title={row.tooltip}>{row.capability}</span>
                  </div>

                  {/* Aternos */}
                  <div className="w-[13.5%] sm:w-[14.5%] md:w-[15%] py-2.5 sm:py-3 px-1 flex items-center justify-center border-r border-[#221e18]">
                    <PixelIcon status={row.aternos} />
                  </div>

                  {/* Minehut */}
                  <div className="w-[13.5%] sm:w-[14.5%] md:w-[15%] py-2.5 sm:py-3 px-1 flex items-center justify-center border-r border-[#221e18]">
                    <PixelIcon status={row.minehut} />
                  </div>

                  {/* Paid Hosts */}
                  <div className="w-[13.5%] sm:w-[14.5%] md:w-[15%] py-2.5 sm:py-3 px-1 flex items-center justify-center border-r border-[#221e18]">
                    <PixelIcon status={row.paidHosts} />
                  </div>

                  {/* PocketHost Winning Column */}
                  <div className="w-[13.5%] sm:w-[14.5%] md:w-[15%] py-2.5 sm:py-3 px-1 flex items-center justify-center bg-[#22c55e]/[0.08] border-l border-[#22c55e]/25 group-hover:bg-[#22c55e]/[0.14] transition-colors">
                    <PixelIcon status={row.pocketHost} />
                  </div>
                </motion.div>
              ))}
            </div>
          </div>

          {/* Table Footer Legend */}
          <div className="border-t border-[#26211a] bg-[#0e0d0a] px-3 sm:px-6 py-3.5 flex flex-wrap items-center justify-between gap-3 font-mono text-[11px] sm:text-xs text-[#a09786]">
            <div className="flex flex-wrap items-center gap-3 sm:gap-6">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 bg-[#4ade80] shadow-[0_0_6px_rgba(74,222,128,0.7)]" />
                <span>PocketHost Free Native</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 bg-[#a89f8d]" />
                <span>Supported</span>
              </div>
              <div className="flex items-center gap-2">
                <svg className="w-2.5 h-2.5 text-[#8a806e]" viewBox="0 0 12 12" fill="currentColor">
                  <rect x="1" y="6" width="5" height="5" />
                  <rect x="6" y="1" width="5" height="5" />
                </svg>
                <span>Partial / Paywalled</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 border-[1.5px] border-[#524a3c] bg-transparent" />
                <span>Not Supported</span>
              </div>
            </div>
          </div>
        </div>

        {/* Quick Conversion Banner */}
        <div className="mt-8 p-6 rounded-none border border-[#2b251e] bg-gradient-to-r from-[#171410] to-[#12100d] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            <div className="font-mono text-sm font-bold text-white mb-1">
              Ready to host without queues or subscriptions?
            </div>
            <p className="text-xs sm:text-sm text-[#a09786]">
              PocketHost runs right on your phone. Completely free, no credit card required.
            </p>
          </div>
          <a
            href="#download"
            className="btn-duo px-6 py-3 text-xs uppercase tracking-wider font-bold whitespace-nowrap shadow-md flex-shrink-0"
          >
            Download Free APK
          </a>
        </div>
      </div>
    </section>
  );
};

export default ComparisonTable;
