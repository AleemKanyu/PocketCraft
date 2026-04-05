import { motion } from "framer-motion";
import { ScrollText, FileCheck, AlertTriangle, Scale, Gamepad2, Ban, RefreshCw, Mail } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { useTheme } from "../lib/ThemeContext";

const sections = [
  {
    icon: FileCheck,
    title: "1. Acceptance of Terms",
    content: [
      "By downloading, installing, or using PocketCraft (\"the App\"), you agree to be bound by these Terms of Service (\"Terms\"). If you do not agree to these Terms, do not use the App.",
      "We may update these Terms from time to time. Continued use of the App after changes constitutes acceptance of the updated Terms. We recommend checking this page periodically.",
    ],
  },
  {
    icon: Gamepad2,
    title: "2. Service Description",
    content: [
      "PocketCraft is a free Android application that enables users to run a Minecraft Java Edition server (PaperMC) directly on their Android device, with optional Bedrock crossplay support via GeyserMC.",
      "The App provides:",
      "• A mobile interface to start, stop, configure, and manage a Minecraft server.",
      "• A relay tunneling service that allows other players to connect to your phone-hosted server without port forwarding.",
      "• Plugin management for installing and configuring Bukkit/Spigot plugins.",
      "• World backup and restore functionality.",
      "The App is currently in **Beta**. Features may change, break, or be removed without notice. We make no guarantees about uptime, performance, or availability of any service.",
    ],
  },
  {
    icon: AlertTriangle,
    title: "3. User Responsibilities",
    content: [
      "As a user of PocketCraft, you agree to:",
      "• **Use the App lawfully.** Do not use PocketCraft to host servers that facilitate harassment, hate speech, illegal activities, or distribution of inappropriate content.",
      "• **Respect other players.** When hosting a public server, you are responsible for the behavior and content on your server. Moderate your community appropriately.",
      "• **Protect your device.** Running a Minecraft server consumes significant CPU, RAM, and battery. PocketCraft is not responsible for device damage, overheating, or excessive battery drain resulting from server operation.",
      "• **Do not abuse the relay service.** Our relay tunneling infrastructure is shared. Do not attempt to use it for non-Minecraft traffic, DDoS attacks, or any purpose other than legitimate Minecraft gameplay.",
      "• **Back up your data.** While PocketCraft provides backup tools, you are ultimately responsible for your world data. We are not liable for data loss.",
    ],
  },
  {
    icon: Scale,
    title: "4. Intellectual Property",
    content: [
      "**Minecraft** is a registered trademark of Mojang Studios, a subsidiary of Microsoft Corporation. PocketCraft is an **independent, fan-made project** and is:",
      "• NOT affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft.",
      "• NOT an official Minecraft product.",
      "• Built using open-source server software (PaperMC, GeyserMC) under their respective licenses.",
      "PocketCraft does not distribute Minecraft client software. Users must own a legitimate copy of Minecraft to connect to servers hosted with PocketCraft.",
      "The PocketCraft name, logo, and original app interface are the property of the PocketCraft team. You may not copy, modify, or redistribute the App without permission.",
    ],
  },
  {
    icon: Ban,
    title: "5. Disclaimers & Limitations",
    content: [
      "PocketCraft is provided **\"AS IS\"** and **\"AS AVAILABLE\"** without warranties of any kind, either express or implied.",
      "We specifically disclaim:",
      "• **No warranty of uninterrupted service.** Relay tunnels may experience downtime. Server performance depends on your device's hardware.",
      "• **No warranty of compatibility.** Not all Android devices can run a Minecraft server smoothly. Minimum requirements are Android 8.0+ with at least 4GB RAM.",
      "• **No liability for third-party plugins.** Plugins you install are created by third-party developers. We are not responsible for bugs, security vulnerabilities, or data collection by plugins.",
      "• **No liability for Minecraft account issues.** If your Minecraft account is affected by hosting or joining servers, that is between you and Mojang/Microsoft.",
      "**Limitation of Liability**: In no event shall PocketCraft, its developers, or contributors be liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of the App.",
    ],
  },
  {
    icon: RefreshCw,
    title: "6. Termination",
    content: [
      "We reserve the right to:",
      "• Block access to the relay tunneling service for users who abuse it.",
      "• Discontinue the App or any of its services at any time, with or without notice.",
      "• Remove the App from distribution if required by legal action or intellectual property claims.",
      "You may stop using PocketCraft at any time by simply uninstalling the App. All local server data will remain on your device unless you manually delete it.",
    ],
  },
  {
    icon: ScrollText,
    title: "7. Changes to These Terms",
    content: [
      "We may revise these Terms at our discretion. When we make significant changes, we will:",
      "• Update the \"Last Updated\" date at the top of this page.",
      "• Notify users through the App or our Discord community when possible.",
      "Your continued use of PocketCraft after any changes indicates your acceptance of the new Terms.",
    ],
  },
  {
    icon: Mail,
    title: "8. Contact",
    content: [
      "For questions about these Terms of Service, please contact us:",
      "• **Email**: support@pocketcraft.online",
      "• **Discord**: https://discord.com/invite/nc7ceYWVfT",
      "We're a small team of passionate Minecraft players building this for the community. We're happy to clarify anything.",
    ],
  },
];

export default function Terms() {
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
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-[#1CB0F6]/8 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-3xl mx-auto relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="flex items-center gap-3 mb-6"
          >
            <ScrollText className="w-6 h-6 text-[#1CB0F6]" />
            <span className="text-xs font-bold uppercase tracking-wider text-[#1CB0F6]">Legal</span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
            className={`text-4xl md:text-5xl font-extrabold mb-4 ${
              theme === "dark" ? "text-white" : "text-black"
            }`}
          >
            Terms of Service
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className={`text-lg mb-2 ${
              theme === "dark" ? "text-white/50" : "text-black/60"
            }`}
          >
            The rules of the game — please read before using PocketCraft.
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
                <div className="w-10 h-10 rounded-xl bg-[#1CB0F6]/10 flex items-center justify-center">
                  <section.icon className="w-5 h-5 text-[#1CB0F6]" />
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
