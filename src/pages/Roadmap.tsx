import { motion } from "framer-motion";
import { CheckCircle } from "lucide-react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";

const roadmapItems = [
  {
    phase: "Phase 1",
    title: "MVP Launch",
    status: "completed",
    items: [
      "Host Java Edition servers on Android",
      "Relay infrastructure (AWS)",
      "Basic server management UI",
      "Player management",
    ],
  },
  {
    phase: "Phase 2",
    title: "Cross-Platform Support",
    status: "completed",
    items: [
      "Bedrock/Console compatibility",
      "Plugin system (Bukkit/Spigot)",
      "Server properties configuration",
      "Player management dashboard",
    ],
  },
  {
    phase: "Phase 3",
    title: "Performance & Stability",
    status: "completed",
    items: [
      "Performance optimization",
      "Advanced server management",
      "Multiple worlds support",
      "Player analytics dashboard",
      "File browser & world management",
      "Advanced plugin discovery",
      "Player whitelisting & bans",
      "Server customization options",
    ],
  },
  {
    phase: "Phase 4",
    title: "Advanced Features & Expansion",
    status: "in_progress",
    items: [
      "World backup to Google Drive",
      "Advanced world management",
      "Server status monitoring",
      "Performance tracking tools",
      "Online Bedrock connection support",
      "Advanced statistics & tracking",
      "Server templates & presets",
    ],
  },
];

const statusConfig: Record<string, { color: string; label: string }> = {
  completed: { color: "#7FE620", label: "Completed" },
  in_progress: { color: "#1CB0F6", label: "In Progress" },
  planned: { color: "#999999", label: "Planned" },
};

export default function Roadmap() {
  return (
    <div className="min-h-screen bg-white">
      <Navbar />
      {/* Header */}
      <section className="bg-white border-b-4 border-black/5 py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <h1 className="text-4xl md:text-5xl font-extrabold text-black mb-4">Development Roadmap</h1>
            <p className="text-black/60 text-lg max-w-2xl mx-auto">
              My vision for PocketCraft. See what's done, in progress, and coming next.
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
                <div className="border-l-4 border-black/10 pl-6 relative">
                  {/* Timeline dot */}
                  <div
                    className="absolute -left-3.5 top-2 w-6 h-6 rounded-full border-4 border-white"
                    style={{ backgroundColor: config.color }}
                  />

                  <div className="bg-white border-2 border-black/10 rounded-lg p-6 hover:border-[#7FE620] transition-colors">
                    <div className="flex items-start justify-between mb-4">
                      <div>
                        <span
                          className="text-xs font-bold uppercase px-3 py-1 rounded-full text-white"
                          style={{ backgroundColor: config.color }}
                        >
                          {config.label}
                        </span>
                        <h3 className="text-3xl font-extrabold text-black mt-3 mb-1">{phase.title}</h3>
                        <p className="text-black/50 font-medium">{phase.phase}</p>
                      </div>
                    </div>

                    <ul className="space-y-2">
                      {phase.items.map((item, idx) => (
                        <li key={idx} className="flex items-start gap-3">
                          <CheckCircle size={20} className="text-[#7FE620] flex-shrink-0 mt-0.5" />
                          <span className="text-black/70 font-medium">{item}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-6 bg-black/2 border-t-4 border-black/5">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-extrabold text-black mb-6">Have Ideas?</h2>
          <p className="text-black/60 text-lg mb-8">
            Help shape the future of PocketCraft. Share your feature requests on Discord.
          </p>
          <a
            href="https://discord.gg/NGPzXFYp"
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
