import { motion } from "framer-motion";
import { ScrollText, FileCheck, AlertTriangle, Scale, Gamepad2, Ban, RefreshCw, Mail, CreditCard, Users } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const sections = [
  {
    icon: FileCheck,
    title: "1. Acceptance of Terms",
    content: [
      "By downloading, installing, or using PocketHost (\"the App\"), you agree to be bound by these Terms of Service (\"Terms\"). If you do not agree to these Terms, do not use the App.",
      "We may update these Terms from time to time. Continued use of the App after changes constitutes acceptance of the updated Terms. We recommend checking this page periodically.",
      "**Age.** The app asks your age once. If you are under 13, you may use a limited version of PocketHost only with a parent's or guardian's permission: you can host a server and let friends join, but sign-in, accounts, Google Drive backup, Remote Control and push notifications are switched off. If you are under the age of majority where you live, a parent or guardian must agree to these Terms for you and supervise your use, especially when you host a server that other people can join.",
    ],
  },
  {
    icon: Gamepad2,
    title: "2. Service Description",
    content: [
      "PocketHost is a free-to-use Android application (with an optional paid Premium plan, see section 4) that enables users to run a Minecraft Java Edition server (PaperMC) directly on their Android device, with optional Bedrock crossplay support via GeyserMC.",
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
      "As a user of PocketHost, you agree to:",
      "• **Use the App lawfully.** Do not use PocketHost to host servers that facilitate harassment, hate speech, illegal activities, or distribution of inappropriate content.",
      "• **Respect other players.** When hosting a public server, you are responsible for the behavior and content on your server. Moderate your community appropriately.",
      "• **Protect your device.** Running a Minecraft server consumes significant CPU, RAM, and battery. PocketHost is not responsible for device damage, overheating, or excessive battery drain resulting from server operation.",
      "• **Do not abuse the relay service.** Our relay tunneling infrastructure is shared. Do not attempt to use it for non-Minecraft traffic, DDoS attacks, or any purpose other than legitimate Minecraft gameplay.",
      "• **Back up your data.** While PocketHost provides backup tools, you are ultimately responsible for your world data. We are not liable for data loss.",
    ],
  },
  {
    icon: CreditCard,
    title: "4. Premium, Subscriptions and Refunds",
    content: [
      "Hosting a server is free. **PocketHost Premium** is optional and removes ads and adds extra features such as full remote control. It is sold through Google Play as a monthly or yearly subscription, or, where offered, a one-time Lifetime purchase.",
      "• **Price and billing**: The price, billing period and currency are shown in the app and on the Google Play payment screen before you pay. Google Play charges your Google account.",
      "• **Automatic renewal**: Monthly and yearly plans renew automatically at the end of each period until you cancel. If the price goes up, Google Play tells you before the new price applies.",
      "• **Cancelling**: Cancel any time in Google Play → Payments & subscriptions → Subscriptions. Premium stays active until the end of the period you already paid for. Uninstalling the app does not cancel a subscription.",
      "• **Refunds**: Payments are processed by Google Play, so refunds follow Google Play's refund policy (https://support.google.com/googleplay/answer/2479637). Nothing in these Terms limits any right to a refund or withdrawal that the law where you live gives you.",
      "• **Watching an ad**: Some features can be unlocked for a limited time by watching a rewarded ad. This is not a purchase and has no cash value.",
    ],
  },
  {
    icon: Users,
    title: "5. Your Server, Your Content and Remote Control",
    content: [
      "• **Minecraft EULA**: Running a Minecraft server means agreeing to the Minecraft End User License Agreement (https://www.minecraft.net/eula). The app asks you to accept it before your first server starts. You must follow it, including its rules about charging players.",
      "• **Your content**: Worlds, plugins, mods, skins, chat and files on your server are yours and your players'. You are responsible for having the right to upload and share them, and for what happens on your server. We do not review this content, and it stays on your phone unless you back it up or share it.",
      "• **Remote Control links**: Anyone who has your Remote Control or admin link can control your server (start and stop it, and with the admin link also use the console, files, players and settings). Keep these links private. Make a new link in the app if one is shared by mistake.",
      "• **Public servers**: If you open your server to the internet, follow the law and the rules of any platform you advertise it on, and moderate it appropriately.",
    ],
  },
  {
    icon: Scale,
    title: "6. Intellectual Property",
    content: [
      "**Minecraft** is a registered trademark of Mojang Studios, a subsidiary of Microsoft Corporation. PocketHost is an **independent, fan-made project** and is:",
      "• NOT affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft.",
      "• NOT an official Minecraft product.",
      "• Built using open-source server software (PaperMC, GeyserMC) under their respective licenses.",
      "PocketHost does not distribute Minecraft client software. Users must own a legitimate copy of Minecraft to connect to servers hosted with PocketHost.",
      "The PocketHost name, logo, and original app interface are the property of the PocketHost team. You may not copy, modify, or redistribute the App without permission.",
      "**Copyright and takedown requests**: If you believe something in the App, on this website or reachable through our relay service infringes your copyright or trademark, email support@pockethost.online with: what the work is, where it appears (link or details), your contact details, and a statement that you are the owner or authorised to act for them. We will look into it and remove or disable the material where appropriate.",
    ],
  },
  {
    icon: Ban,
    title: "7. Disclaimers & Limitations",
    content: [
      "PocketHost is provided **\"AS IS\"** and **\"AS AVAILABLE\"** without warranties of any kind, either express or implied.",
      "We specifically disclaim:",
      "• **No warranty of uninterrupted service.** Relay tunnels may experience downtime. Server performance depends on your device's hardware.",
      "• **No warranty of compatibility.** Not all Android devices can run a Minecraft server smoothly. Minimum requirements are Android 8.0+ with at least 4GB RAM.",
      "• **No liability for third-party plugins.** Plugins you install are created by third-party developers. We are not responsible for bugs, security vulnerabilities, or data collection by plugins.",
      "• **No liability for Minecraft account issues.** If your Minecraft account is affected by hosting or joining servers, that is between you and Mojang/Microsoft.",
      "**Limitation of Liability**: In no event shall PocketHost, its developers, or contributors be liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of the App.",
      "Nothing in these Terms excludes or limits liability that cannot be excluded or limited by law, or takes away rights you have as a consumer under the law of the country where you live.",
    ],
  },
  {
    icon: RefreshCw,
    title: "8. Termination",
    content: [
      "We reserve the right to:",
      "• Block access to the relay tunneling service for users who abuse it.",
      "• Discontinue the App or any of its services at any time, with or without notice.",
      "• Remove the App from distribution if required by legal action or intellectual property claims.",
      "You may stop using PocketHost at any time by simply uninstalling the App. All local server data will remain on your device unless you manually delete it.",
    ],
  },
  {
    icon: ScrollText,
    title: "9. Changes to These Terms",
    content: [
      "We may revise these Terms at our discretion. When we make significant changes, we will:",
      "• Update the \"Last Updated\" date at the top of this page.",
      "• Notify users through the App or our Discord community when possible.",
      "Your continued use of PocketHost after any changes indicates your acceptance of the new Terms.",
    ],
  },
  {
    icon: Mail,
    title: "10. Contact",
    content: [
      "For questions about these Terms of Service, please contact us:",
      "• **Email**: support@pockethost.online",
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
      <SEO
        title="Terms of Service | PocketHost"
        description="Review the PocketHost terms of service for hosting dedicated Minecraft servers on Android devices and relay networking."
        path="/terms"
      />
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
            The rules of the game — please read before using PocketHost.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className={`text-sm ${
              theme === "dark" ? "text-white/30" : "text-black/40"
            }`}
          >
            {/* A fixed date: showing today's date would claim the terms change every day. */}
            Last updated: November 21, 2026
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
