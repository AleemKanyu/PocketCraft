import { motion } from "framer-motion";
import { Shield, Eye, Server, Users, Lock, Mail } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { useTheme } from "../lib/ThemeContext";

const sections = [
  {
    icon: Eye,
    title: "1. Information We Collect",
    content: [
      "PocketCraft is designed with your privacy in mind. We collect minimal data to provide and improve our service:",
      "• **Device Information**: Basic device model and Android version to ensure compatibility and optimize performance.",
      "• **Usage Analytics**: Anonymous, aggregated data about app usage patterns (e.g., server start/stop events) to improve stability. No personal data is included.",
      "• **Crash Reports**: Automatic crash logs to help us fix bugs and improve reliability. These contain technical stack traces only — no personal information.",
      "• **IP Address**: Temporarily processed for our relay tunnel service to enable multiplayer connections. We do not store or log IP addresses.",
      "We do NOT collect your name, email, phone number, location, contacts, photos, or any other personal information unless you voluntarily provide it (e.g., when contacting support).",
    ],
  },
  {
    icon: Server,
    title: "2. How Your Server Works",
    content: [
      "PocketCraft runs a Minecraft Java Edition server directly on your Android device. This means:",
      "• **Your world data stays on your device.** We never upload, access, or store your Minecraft worlds, configurations, or plugin data on our servers.",
      "• **Server files are local.** All server JARs, configs, and player data are stored in your device's local storage.",
      "• **Relay tunneling is ephemeral.** Our relay service creates a temporary network tunnel so other players can connect to your phone. Once your server stops, the tunnel is destroyed. No gameplay data passes through or is stored on our relay infrastructure.",
      "• **Plugins run locally.** Any Bukkit/Spigot plugins you install execute on your device. We are not responsible for data collection by third-party plugins.",
    ],
  },
  {
    icon: Users,
    title: "3. Third-Party Services",
    content: [
      "PocketCraft may interact with the following third-party services:",
      "• **Mojang / Microsoft**: Minecraft is a trademark of Mojang Studios / Microsoft. PocketCraft is an independent project and is not affiliated with, endorsed by, or connected to Mojang or Microsoft in any way. Players connecting to your server use their own Minecraft accounts.",
      "• **Discord**: We maintain a community Discord server for support and updates. Discord's own privacy policy applies when you use their platform.",
      "• **CDN Services**: We use content delivery networks to serve app updates and assets. These services may process your IP address to deliver content.",
      "We do not sell, rent, or share your data with any third parties for advertising or marketing purposes.",
    ],
  },
  {
    icon: Users,
    title: "4. Children's Privacy",
    content: [
      "PocketCraft does not knowingly collect personal information from children under the age of 13. Our app is a tool for hosting Minecraft servers and does not require account creation or personal data submission.",
      "If you are a parent or guardian and believe your child has provided personal information to us (e.g., through a support email), please contact us and we will promptly delete it.",
      "Parents should supervise their children's multiplayer gaming activities and ensure appropriate behavior when hosting public servers.",
    ],
  },
  {
    icon: Lock,
    title: "5. Data Security",
    content: [
      "We take reasonable measures to protect any data we process:",
      "• All relay tunnel connections use encrypted channels.",
      "• We do not maintain databases of user information.",
      "• Crash reports and analytics are processed through secure, industry-standard services.",
      "• App updates are delivered over HTTPS.",
      "However, no method of electronic transmission or storage is 100% secure. While we strive to protect your data, we cannot guarantee absolute security.",
    ],
  },
  {
    icon: Mail,
    title: "6. Contact Us",
    content: [
      "If you have any questions about this Privacy Policy or your data, you can reach us at:",
      "• **Email**: support@pocketcraft.online",
      "• **Discord**: https://discord.com/invite/nc7ceYWVfT",
      "We aim to respond to all privacy-related inquiries within 72 hours.",
    ],
  },
];

export default function Privacy() {
  const { theme } = useTheme();

  return (
    <div className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
      theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"
    }`}>
      <Navbar />

      {/* Hero */}
      <section className={`relative py-24 sm:py-32 px-6 overflow-hidden ${
        theme === "dark" ? "bg-[#0a0a0a]" : "bg-white"
      }`}>
        {/* Background glow */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-[#7FE620]/8 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-3xl mx-auto relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="flex items-center gap-3 mb-6"
          >
            <Shield className="w-6 h-6 text-[#7FE620]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#7FE620]">Legal</span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
            className={`text-4xl md:text-5xl font-extrabold mb-4 ${
              theme === "dark" ? "text-white" : "text-black"
            }`}
          >
            Privacy Policy
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className={`text-lg mb-2 ${
              theme === "dark" ? "text-white/50" : "text-black/60"
            }`}
          >
            How PocketCraft handles your data — spoiler: we barely collect any.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className={`text-sm ${
              theme === "dark" ? "text-white/30" : "text-black/40"
            }`}
          >
            Last updated: {new Date().toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
          </motion.p>
        </div>
      </section>

      {/* Content */}
      <section className={`py-16 px-6 border-t-4 ${
        theme === "dark" ? "border-white/5" : "border-black/5"
      }`}>
        <div className="max-w-3xl mx-auto legal-page">
          {sections.map((section, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-50px" }}
              transition={{ duration: 0.5, delay: i * 0.05 }}
              className={`mb-12 pb-12 ${
                i < sections.length - 1
                  ? theme === "dark" ? "border-b border-white/5" : "border-b border-black/5"
                  : ""
              }`}
            >
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl bg-[#7FE620]/10 flex items-center justify-center">
                  <section.icon className="w-5 h-5 text-[#7FE620]" />
                </div>
                <h3 className={`text-xl font-bold ${
                  theme === "dark" ? "text-white" : "text-black"
                }`}>
                  {section.title}
                </h3>
              </div>
              <div className="pl-[52px]">
                {section.content.map((paragraph, j) => (
                  <p
                    key={j}
                    className={`text-sm leading-relaxed mb-3 ${
                      theme === "dark" ? "text-white/50" : "text-black/60"
                    }`}
                    dangerouslySetInnerHTML={{
                      __html: paragraph
                        .replace(/\*\*(.*?)\*\*/g, `<strong class="${theme === "dark" ? "text-white/80" : "text-black/80"}">$1</strong>`)
                    }}
                  />
                ))}
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      <Footer />
    </div>
  );
}
