import { motion } from "framer-motion";
import { CheckCircle } from "lucide-react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const roadmapItems = [
  {
    phase: "Phase 1",
    title: "MVP Launch",
    status: "completed",
    items: [
      { text: "Host Java Edition servers on Android", status: "completed" },
      { text: "Relay infrastructure (AWS)", status: "completed" },
      { text: "Basic server management UI", status: "completed" },
      { text: "Player management", status: "completed" },
    ],
  },
  {
    phase: "Phase 2",
    title: "Cross-Platform Support",
    status: "completed",
    items: [
      { text: "Bedrock/Console compatibility", status: "completed" },
      { text: "Plugin system (Bukkit/Spigot)", status: "completed" },
      { text: "Server properties configuration", status: "completed" },
      { text: "Player management dashboard", status: "completed" },
    ],
  },
  {
    phase: "Phase 3",
    title: "Performance & Stability",
    status: "completed",
    items: [
      { text: "Performance optimization", status: "completed" },
      { text: "Advanced server management", status: "completed" },
      { text: "Multiple worlds support", status: "completed" },
      { text: "Player analytics dashboard", status: "completed" },
      { text: "Online Bedrock connection support", status: "completed" },
      { text: "File browser & world management", status: "completed" },
      { text: "Advanced plugin discovery", status: "completed" },
      { text: "Player whitelisting & bans", status: "completed" },
      { text: "Server customization options", status: "completed" },
    ],
  },
  {
    phase: "Phase 4",
    title: "Advanced Features & Expansion",
    status: "in_progress",
    items: [
      { text: "World backup to Google Drive", status: "completed" },
      { text: "Advanced world management", status: "completed" },
      { text: "Server status monitoring", status: "in_progress" },
      { text: "Performance tracking tools", status: "completed" },
      { text: "Advanced statistics & tracking", status: "completed" },
      { text: "Server templates & presets", status: "in_progress" },
    ],
  },
];

const statusConfig: Record<string, { color: string; label: string }> = {
  completed: { color: "#7FE620", label: "Completed" },
  in_progress: { color: "#1CB0F6", label: "In Progress" },
  planned: { color: "#999999", label: "Planned" },
};

export default function Roadmap() {
  const { theme } = useTheme();

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title="Product Roadmap - PocketHost"
        description="See what features are planned, currently in development, and recently launched for PocketHost Android Minecraft server hosting."
        path="/roadmap"
      />
      <Navbar />
      {/* Header */}
      <section
        className={`border-b-4 py-20 px-6 section-transition ${
          theme === "dark" ? "bg-[#0a0a0a] border-white/5" : "bg-white border-black/5"
        }`}
      >
        <div className="max-w-4xl mx-auto text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <h1 className={`text-4xl md:text-5xl font-extrabold mb-4 ${theme === "dark" ? "text-white" : "text-black"}`}>
              Development Roadmap
            </h1>
            <p className={`text-lg max-w-2xl mx-auto ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
              My vision for PocketHost. See what's done, in progress, and coming next.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Roadmap */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto space-y-12">
          {roadmapItems.map((phase, index) => {
            const config = statusConfig[phase.status];
            return (
              <motion.div
                key={index}
                initial={{ opacity: 0, x: index % 2 === 0 ? -40 : 40 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: index * 0.1 }}
              >
                <div className={`border-l-4 pl-6 relative ${theme === "dark" ? "border-white/10" : "border-black/10"}`}>
                  {/* Timeline dot */}
                  <div
                    className={`absolute -left-3.5 top-2 w-6 h-6 rounded-full border-4 ${
                      theme === "dark" ? "border-[#0a0a0a]" : "border-white"
                    }`}
                    style={{ backgroundColor: config.color }}
                  />

                  <div
                    className={`border-2 rounded-lg p-6 hover:border-[#7FE620] transition-colors ${
                      theme === "dark" ? "bg-white/[0.02] border-white/10" : "bg-white border-black/10"
                    }`}
                  >
                    <div className="flex items-start justify-between mb-4">
                      <div>
                        <span
                          className="text-xs font-bold uppercase px-3 py-1 rounded-full text-white"
                          style={{ backgroundColor: config.color }}
                        >
                          {config.label}
                        </span>
                        <h3 className={`text-3xl font-extrabold mt-3 mb-1 ${theme === "dark" ? "text-white" : "text-black"}`}>
                          {phase.title}
                        </h3>
                        <p className={`font-medium ${theme === "dark" ? "text-white/35" : "text-black/50"}`}>
                          {phase.phase}
                        </p>
                      </div>
                    </div>

                    <ul className="space-y-2">
                      {phase.items.map((item, idx) => {
                        const isCompleted = item.status === "completed";
                        const isInProgress = item.status === "in_progress";
                        return (
                          <li key={idx} className="flex items-start gap-3">
                            {isCompleted ? (
                              <CheckCircle size={20} className="text-[#7FE620] flex-shrink-0 mt-0.5" />
                            ) : isInProgress ? (
                              <motion.div
                                animate={{ opacity: [0.5, 1, 0.5] }}
                                transition={{ repeat: Infinity, duration: 2 }}
                                className="flex-shrink-0 mt-1.5"
                              >
                                <div className="w-3.5 h-3.5 rounded-full bg-[#1CB0F6] border-2 border-white/20" />
                              </motion.div>
                            ) : (
                              <div className="w-3.5 h-3.5 rounded-full border-2 border-white/20 flex-shrink-0 mt-1.5" />
                            )}
                            <span
                              className={`font-medium ${
                                isCompleted
                                  ? theme === "dark"
                                    ? "text-white/65"
                                    : "text-black/70"
                                  : isInProgress
                                  ? theme === "dark"
                                    ? "text-[#1CB0F6] font-bold"
                                    : "text-[#0c70c0] font-bold"
                                  : theme === "dark"
                                  ? "text-white/35"
                                  : "text-black/40"
                              }`}
                            >
                              {item.text}
                            </span>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </section>

      {/* CTA */}
      <section
        className={`py-20 px-6 border-t-4 section-transition ${
          theme === "dark" ? "bg-white/[0.02] border-white/5" : "bg-black/2 border-black/5"
        }`}
      >
        <div className="max-w-2xl mx-auto text-center">
          <h2 className={`text-3xl font-extrabold mb-6 ${theme === "dark" ? "text-white" : "text-black"}`}>
            Have Ideas?
          </h2>
          <p className={`text-lg mb-8 ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
            Help shape the future of PocketHost. Share your feature requests on Discord.
          </p>
          <a
            href="https://discord.com/invite/nc7ceYWVfT"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-duo px-12 py-4 inline-block"
          >
            Join Discord
          </a>
        </div>
      </section>

      <Footer />
    </div>
  );
}
