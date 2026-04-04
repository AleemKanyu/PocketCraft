import { motion } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

const features = [
  {
    icon: MINECRAFT_ICONS.features.vanilla,
    title: "Vanilla Engine",
    detail: "Full Java Edition compatibility with zero compromises.",
    color: "#7FE620"
  },
  {
    icon: MINECRAFT_ICONS.features.latency,
    title: "Zero-Latency Tunnel",
    detail: "Proprietary relay tech. Play from anywhere, no port forwarding.",
    color: "#FF85B3"
  },
  {
    icon: MINECRAFT_ICONS.features.social,
    title: "Cross Bedrock Play",
    detail: "Java + Bedrock players on the same server. True crossplay.",
    color: "#1CB0F6"
  },
  {
    icon: MINECRAFT_ICONS.features.plugin,
    title: "Plugin Support",
    detail: "Drag & drop Bukkit plugins. Hot-reload without downtime.",
    color: "#FFD900"
  },
];

export default function Features() {
  return (
    <section id="features" className="py-40 px-6 bg-white relative overflow-hidden">
      {/* Subtle gradient blobs */}
      <div className="absolute top-20 right-0 w-96 h-96 bg-gradient-to-br from-[#7FE620] to-transparent opacity-5 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-96 h-96 bg-gradient-to-tr from-[#1CB0F6] to-transparent opacity-5 rounded-full blur-3xl pointer-events-none" />

      <div className="max-w-7xl mx-auto relative z-10">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="max-w-3xl mb-24"
        >
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-1 bg-[#7FE620] rounded-full" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">Core Features</span>
          </div>
          <h2 className="text-4xl md:text-5xl font-extrabold text-black mb-6 leading-tight">
            Powerful <br />
            <span className="text-[#7FE620]">Mobile Hosting</span>
          </h2>
          <p className="text-black/60 text-lg font-medium leading-relaxed max-w-2xl">
            PocketCraft turns your Android phone into a high-performance Minecraft server with zero compromise on features or performance.
          </p>
        </motion.div>

        {/* Features Grid - 2x2 Layout */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-5xl">
          {features.map((f, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: i * 0.1 }}
              className="group relative"
            >
              {/* Card */}
              <div
                className="h-full flex flex-col p-8 rounded-3xl transition-all duration-300 hover:shadow-xl bg-white border-2 border-black/5 hover:border-black/10 overflow-hidden"
              >
                {/* Icon Container - Colored Background */}
                <motion.div
                  whileHover={{ scale: 1.08, rotate: 3 }}
                  transition={{ type: "spring", stiffness: 200 }}
                  className="mb-8 relative w-20 h-20 rounded-2xl flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: f.color + "15" }}
                >
                  {/* Accent bar */}
                  <div
                    className="absolute left-0 top-0 bottom-0 w-1 rounded-full"
                    style={{ backgroundColor: f.color }}
                  />

                  <img
                    src={f.icon}
                    alt={f.title}
                    className="w-10 h-10 image-pixelated"
                    onError={(e) => {
                      e.currentTarget.onerror = null;
                      e.currentTarget.src = "/grass-block.png";
                    }}
                  />
                </motion.div>

                {/* Content */}
                <div className="flex-1 flex flex-col">
                  <h3 className="text-xl font-extrabold text-black mb-2 leading-tight">
                    {f.title}
                  </h3>
                  <p className="text-black/60 font-medium text-sm leading-relaxed mb-6">
                    {f.detail}
                  </p>
                </div>

                {/* Status Badge */}
                <motion.div
                  initial={{ opacity: 0 }}
                  whileInView={{ opacity: 1 }}
                  viewport={{ once: true }}
                  transition={{ delay: i * 0.1 + 0.3 }}
                  className="flex items-center gap-2"
                >
                  <div
                    className="w-2.5 h-2.5 rounded-full animate-pulse"
                    style={{ backgroundColor: f.color }}
                  />
                  <span className="text-xs font-bold track-wider uppercase" style={{ color: f.color }}>
                    Ready
                  </span>
                </motion.div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
