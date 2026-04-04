import { motion } from "framer-motion";
import { Cloud, Globe, Zap, Shield, Server, MapPin } from "lucide-react";

const infrastructureFeatures = [
  {
    icon: Cloud,
    title: "AWS Global Relay",
    description: "Distributed across multiple regions for ultra-low latency worldwide",
    detail: "Play.pocketcraft.online",
    gradient: "from-blue-400 to-blue-600",
  },
  {
    icon: Zap,
    title: "Instant Deployment",
    description: "Start hosting in seconds, no configuration needed",
    detail: "Zero-setup technology",
    gradient: "from-yellow-400 to-orange-500",
  },
  {
    icon: Shield,
    title: "Enterprise Security",
    description: "End-to-end encryption and DDoS protection included",
    detail: "Always protected",
    gradient: "from-green-400 to-emerald-600",
  },
  {
    icon: Server,
    title: "Mobile Optimized",
    description: "Runs on your Android device with minimal resource usage",
    detail: "Efficient performance",
    gradient: "from-purple-400 to-pink-500",
  },
  {
    icon: Globe,
    title: "Global Network",
    description: "Connect players from anywhere with true point-to-point relay",
    detail: "50+ countries supported",
    gradient: "from-cyan-400 to-blue-500",
  },
  {
    icon: MapPin,
    title: "Regional Servers",
    description: "Choose your relay location for optimal performance",
    detail: "Global, NA, EU, APAC",
    gradient: "from-red-400 to-pink-600",
  },
];

const stats = [
  { number: "99.9%", label: "Uptime SLA" },
  { number: "<50ms", label: "Avg Latency" },
  { number: "256Mbps", label: "Bandwidth" },
  { number: "∞", label: "Player Slots" },
];

export default function InfrastructureSection() {
  return (
    <section className="py-32 px-6 bg-white relative overflow-hidden border-t-4 border-black/5">
      {/* Background elements */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-20 right-0 w-96 h-96 bg-[#7FE620]/5 rounded-full blur-3xl" />
        <div className="absolute bottom-0 left-1/4 w-80 h-80 bg-[#1CB0F6]/5 rounded-full blur-3xl" />
      </div>

      <div className="max-w-7xl mx-auto relative z-10">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="max-w-3xl mb-20"
        >
          <div className="flex items-center gap-4 mb-6">
            <div className="w-12 h-1 bg-[#7FE620]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">
              Infrastructure
            </span>
          </div>
          <h2 className="text-4xl md:text-6xl font-extrabold text-black mb-6 leading-tight">
            Enterprise-Grade <br /> <span className="text-[#7FE620]">Hosting</span> on Mobile
          </h2>
          <p className="text-black/60 text-lg font-medium max-w-2xl">
            PocketCraft runs on a globally distributed AWS infrastructure, giving your players the same experience as traditional server hosting—but from your phone.
          </p>
        </motion.div>

        {/* Stats Row */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="grid grid-cols-2 md:grid-cols-4 gap-6 mb-20 bg-black/2 border-2 border-black/5 rounded-2xl p-8"
        >
          {stats.map((stat, idx) => (
            <div key={idx} className="text-center">
              <div className="text-3xl md:text-4xl font-extrabold text-[#7FE620] mb-2">
                {stat.number}
              </div>
              <div className="text-sm font-semibold text-black/60 uppercase tracking-wider">
                {stat.label}
              </div>
            </div>
          ))}
        </motion.div>

        {/* Features Grid - 3x2 Layout */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-20">
          {infrastructureFeatures.map((feature, idx) => {
            const Icon = feature.icon;
            return (
              <motion.div
                key={idx}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: idx * 0.08 }}
                className="group relative"
              >
                <div className="h-full border-2 border-black/10 hover:border-[#7FE620] rounded-2xl p-8 bg-white transition-all duration-300 hover:shadow-xl flex flex-col">
                  {/* Icon */}
                  <motion.div
                    whileHover={{ scale: 1.1, rotate: 5 }}
                    className={`w-14 h-14 rounded-xl bg-gradient-to-br ${feature.gradient} flex items-center justify-center mb-6 group-hover:shadow-lg transition-shadow`}
                  >
                    <Icon className="w-7 h-7 text-white" />
                  </motion.div>

                  {/* Content */}
                  <h3 className="text-xl font-bold text-black mb-3 group-hover:text-[#7FE620] transition-colors">
                    {feature.title}
                  </h3>
                  <p className="text-black/60 font-medium text-sm leading-relaxed mb-6 flex-1">
                    {feature.description}
                  </p>

                  {/* Bottom accent */}
                  <div className="pt-4 border-t-2 border-black/5 text-xs font-bold text-[#7FE620] uppercase tracking-wider">
                    {feature.detail}
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>

        {/* Architecture Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.2 }}
          className="bg-gradient-to-br from-black/2 to-[#7FE620]/5 border-2 border-black/5 rounded-2xl p-12 overflow-hidden"
        >
          <div className="flex flex-col md:flex-row gap-12 items-start">
            <div className="flex-1">
              <h3 className="text-3xl font-bold text-black mb-6">How It Works</h3>

              <div className="space-y-6">
                {[
                  {
                    step: "1",
                    title: "Run on Android",
                    desc: "Start a Minecraft server directly on your phone",
                  },
                  {
                    step: "2",
                    title: "Connect to Relay",
                    desc: "Your server automatically connects to our global relay network",
                  },
                  {
                    step: "3",
                    title: "Get Public Address",
                    desc: "Receive a unique play.pocketcraft.online address",
                  },
                  {
                    step: "4",
                    title: "Share & Play",
                    desc: "Friends join from anywhere with zero configuration",
                  },
                ].map((item, idx) => (
                  <motion.div
                    key={idx}
                    initial={{ opacity: 0, x: -20 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.5, delay: idx * 0.1 }}
                    className="flex gap-4"
                  >
                    <div className="flex-shrink-0 w-12 h-12 rounded-full bg-[#7FE620] text-black font-bold text-lg flex items-center justify-center">
                      {item.step}
                    </div>
                    <div>
                      <h4 className="font-bold text-black mb-1">{item.title}</h4>
                      <p className="text-black/60 font-medium text-sm">{item.desc}</p>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Visual Diagram */}
            <div className="flex-1">
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: 0.3 }}
                className="bg-white border-2 border-black/10 rounded-xl p-8"
              >
                <div className="space-y-4 text-center">
                  {/* Your Phone */}
                  <div className="bg-gradient-to-br from-blue-100 to-blue-50 border-2 border-blue-300 rounded-lg p-4">
                    <div className="font-bold text-black mb-2">📱 Your Phone</div>
                    <div className="text-xs text-black/60">Minecraft Server</div>
                  </div>

                  {/* Arrow */}
                  <div className="flex justify-center">
                    <div className="text-2xl">↓</div>
                  </div>

                  {/* Relay Network */}
                  <div className="bg-gradient-to-br from-[#7FE620] to-green-500 rounded-lg p-4 text-white">
                    <div className="font-bold mb-2">🌐 AWS Relay Network</div>
                    <div className="text-xs opacity-90">Global Distribution</div>
                  </div>

                  {/* Arrow */}
                  <div className="flex justify-center">
                    <div className="text-2xl">↓</div>
                  </div>

                  {/* Players */}
                  <div className="bg-gradient-to-br from-purple-100 to-purple-50 border-2 border-purple-300 rounded-lg p-4">
                    <div className="font-bold text-black mb-2">👥 Players Worldwide</div>
                    <div className="text-xs text-black/60">Java + Bedrock</div>
                  </div>
                </div>
              </motion.div>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
