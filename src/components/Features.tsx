import { motion } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";
import { useTheme } from "../lib/ThemeContext";

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
  const { theme } = useTheme();

  return (
    <section id="features" className={`py-40 px-6 relative overflow-hidden section-transition ${
      theme === "dark" ? "bg-[#0a0a0a]" : "bg-white"
    }`}>
      {/* Subtle gradient blobs */}
      <motion.div
        className="absolute top-20 right-0 w-96 h-96 bg-gradient-to-br from-[#7FE620] to-transparent opacity-5 rounded-full blur-3xl pointer-events-none"
        animate={{ scale: [1, 1.2, 1] }}
        transition={{ duration: 10, repeat: Infinity }}
      />
      <motion.div
        className="absolute bottom-0 left-0 w-96 h-96 bg-gradient-to-tr from-[#1CB0F6] to-transparent opacity-5 rounded-full blur-3xl pointer-events-none"
        animate={{ scale: [1.2, 1, 1.2] }}
        transition={{ duration: 12, repeat: Infinity }}
      />

      <div className="max-w-7xl mx-auto relative z-10">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.8, ease: "easeOut" }}
          className="max-w-3xl mb-24 relative z-10"
        >
          {/* Floating animated asset */}
          <motion.img
            src={MINECRAFT_ICONS.hero.pickaxe}
            alt="Pickaxe"
            className="absolute -top-10 -right-10 md:-top-20 md:right-10 w-16 h-16 opacity-30 image-pixelated pointer-events-none"
            initial={{ y: 0, rotate: 0 }}
            animate={{ y: [-10, 10, -10], rotate: [15, 25, 15] }}
            transition={{ duration: 5, repeat: Infinity, ease: "easeInOut" }}
          />
          
          <div className="flex items-center gap-3 mb-6">
            <motion.div
              className="w-10 h-1 bg-[#7FE620] rounded-full"
              initial={{ width: 0 }}
              whileInView={{ width: 40 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: 0.3, ease: "easeOut" }}
            />
            <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">Core Features</span>
          </div>
          <h2 className={`text-4xl md:text-5xl font-extrabold mb-6 leading-tight ${
            theme === "dark" ? "text-white" : "text-black"
          }`}>
            Powerful <br />
            <span className="text-[#7FE620]">Mobile Hosting</span>
          </h2>
          <p className={`text-lg font-medium leading-relaxed max-w-2xl ${
            theme === "dark" ? "text-white/50" : "text-black/60"
          }`}>
            PocketCraft turns your Android phone into a high-performance Minecraft server with zero compromise on features or performance.
          </p>
        </motion.div>

        {/* Features Grid - 2x2 Layout */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-5xl">
          {features.map((f, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: i * 0.12 }}
              className="group relative tilt-card"
            >
              {/* Card */}
              <div
                className={`h-full flex flex-col p-8 rounded-3xl transition-all duration-300 overflow-hidden border-2 ${
                  theme === "dark"
                    ? "bg-[#111111] border-white/5 hover:border-white/10 hover:shadow-2xl hover:shadow-[#7FE620]/5"
                    : "bg-white border-black/5 hover:border-black/10 hover:shadow-xl"
                }`}
              >
                {/* Hover glow */}
                <div
                  className="absolute inset-0 rounded-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"
                  style={{
                    background: `radial-gradient(600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%), ${f.color}08, transparent 40%)`,
                  }}
                />

                {/* Icon Container */}
                <motion.div
                  whileHover={{ scale: 1.1, rotate: 5 }}
                  transition={{ type: "spring", stiffness: 300 }}
                  className="mb-8 relative w-20 h-20 rounded-2xl flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: f.color + "15" }}
                >
                  {/* Accent bar */}
                  <motion.div
                    className="absolute left-0 top-0 bottom-0 w-1 rounded-full"
                    style={{ backgroundColor: f.color }}
                    initial={{ height: 0 }}
                    whileInView={{ height: "100%" }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.6, delay: i * 0.1 + 0.3 }}
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
                <div className="flex-1 flex flex-col relative z-10">
                  <h3 className={`text-xl font-extrabold mb-2 leading-tight ${
                    theme === "dark" ? "text-white" : "text-black"
                  }`}>
                    {f.title}
                  </h3>
                  <p className={`font-medium text-sm leading-relaxed mb-6 ${
                    theme === "dark" ? "text-white/50" : "text-black/60"
                  }`}>
                    {f.detail}
                  </p>
                </div>

                {/* Status Badge */}
                <motion.div
                  initial={{ opacity: 0, x: -10 }}
                  whileInView={{ opacity: 1, x: 0 }}
                  viewport={{ once: true }}
                  transition={{ delay: i * 0.1 + 0.4 }}
                  className="flex items-center gap-2 relative z-10"
                >
                  <div
                    className="w-2.5 h-2.5 rounded-full animate-pulse"
                    style={{ backgroundColor: f.color }}
                  />
                  <span className="text-xs font-bold tracking-wider uppercase" style={{ color: f.color }}>
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
