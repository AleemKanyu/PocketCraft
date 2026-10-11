import { motion } from "framer-motion";
import { Shield, Eye, Server, Users, Lock, Mail, Megaphone, UserCheck } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const sections = [
  {
    icon: Eye,
    title: "1. Information We Collect",
    content: [
      "PocketHost collects only what it needs to run the service and keep it stable:",
      "• **Account Information (optional)**: You can use the app without an account. If you create one or sign in with Google, we store your email address and an account ID so your custom server address, settings and supporter status follow your account. If you use the web dashboard, your server's status and the commands you send from it are stored with your account.",
      "• **Device Information**: Basic device model and Android version to ensure compatibility and optimize performance.",
      "• **Usage Analytics**: Data about how the app is used (for example server start and stop events), collected through Google Firebase Analytics to improve stability. The app asks whether you allow this before it is switched on.",
      "• **Crash Reports**: Automatic crash and error reports sent through Firebase Crashlytics to help us fix bugs. These contain technical stack traces and device details, and may include the last lines of your server console.",
      "• **IP Address**: Used by our relay tunnel service to route players to your server, and may appear in short-lived operational logs kept for security and abuse prevention. We do not use it to identify you.",
      "• **Feedback and Support**: Messages, and any log excerpt you choose to attach, when you send feedback or contact support.",
      "• **Supporter Status**: If you support PocketHost through Google Play or Patreon, we record that your account is a supporter. Payments are handled by Google Play or Patreon; we never see your card details.",
      "• **Notification Token**: A push token used to deliver announcements, if notifications are on.",
      "• **Advertising Data**: The app may show ads through Google AdMob. AdMob collects your device's advertising ID and related data to do so. See the Advertising section below.",
      "We do not collect your phone number, your contacts or your precise location.",
    ],
  },
  {
    icon: Server,
    title: "2. How Your Server Works",
    content: [
      "PocketHost runs a Minecraft Java Edition server directly on your Android device. This means:",
      "• **Your world data stays on your device.** We never upload, access, or store your Minecraft worlds, configurations, or plugin data on our servers. If you turn on Google Drive backup, backups go to your own Google Drive, which we cannot access.",
      "• **Server files are local.** All server JARs, configs, and player data are stored in your device's local storage.",
      "• **Relay tunneling is ephemeral.** Our relay service creates a temporary network tunnel so other players can connect to your phone. Gameplay traffic passes through the relay to reach your phone, but it is not stored. Once your server stops, the tunnel is closed.",
      "• **Plugins run locally.** Any Bukkit/Spigot plugins you install execute on your device. We are not responsible for data collection by third-party plugins.",
    ],
  },
  {
    icon: Users,
    title: "3. Third-Party Services",
    content: [
      "PocketHost relies on the following third-party services, which process data on our behalf or under their own policies:",
      "• **Google Firebase**: Sign-in (Authentication), account data storage (Firestore), Analytics, Crashlytics, push notifications (Cloud Messaging) and Remote Config.",
      "• **Google AdMob**: Shows ads in the app and collects the data described in the Advertising section below.",
      "• **Google Play and Patreon**: Handle supporter payments.",
      "• **Google Drive**: Only if you turn on cloud backup, and only to your own Drive.",
      "• **Mojang / Microsoft**: Minecraft is a trademark of Mojang Studios / Microsoft. PocketHost is an independent project and is not affiliated with, endorsed by, or connected to Mojang or Microsoft in any way. Players connecting to your server use their own Minecraft accounts.",
      "• **Discord**: We maintain a community Discord server for support and updates. Discord's own privacy policy applies when you use their platform.",
      "• **Download and CDN Services**: Used to deliver app updates, server software and plugins. These services may process your IP address to deliver content.",
      "We do not sell or rent your data. The only data shared for advertising is what Google AdMob collects to show ads, as described in the Advertising section.",
    ],
  },
  {
    icon: Megaphone,
    title: "4. Advertising",
    content: [
      "PocketHost may show ads in some parts of the app to help pay for the relay servers. Ads are provided by **Google AdMob**. Your server, console and players are never interrupted by ads.",
      "• **What AdMob collects**: Your device's advertising ID, your IP address (which gives an approximate location), device and app information, and how you interact with ads. Google and its partners use this to show and measure ads, limit how often you see the same ad, and detect fraud.",
      "• **Personalised ads**: Where the law allows and where you have agreed, Google may use this data to show ads based on your interests. Otherwise you are shown non-personalised ads.",
      "• **Your choice in Europe**: If you are in the European Economic Area, the United Kingdom or Switzerland, the app asks for your choice before any ad is requested. You can change or withdraw that choice at any time in the app under **Settings → About → Ad privacy choices**.",
      "• **Your advertising ID**: You can reset or delete your advertising ID, or opt out of ad personalisation, in the Google section of your Android settings.",
      "Google's handling of this data is covered by Google's Privacy Policy (https://policies.google.com/privacy). How Google uses data from apps that use its services is explained at https://policies.google.com/technologies/partner-sites.",
    ],
  },
  {
    icon: UserCheck,
    title: "5. Your Choices and Rights",
    content: [
      "• **Delete your account**: You can delete your account and the data stored with it at any time in the app under Settings → Account, or at https://pockethost.online/delete-account.",
      "• **Analytics and ads**: Usage analytics is only switched on if you allow it when the app asks. You can change your ad consent as described in the Advertising section.",
      "• **Access and correction**: You can ask us for a copy of the data linked to your account, or ask us to correct it, by contacting us.",
      "Depending on where you live, you may have further rights under laws such as the GDPR, including the right to object to or restrict processing and to lodge a complaint with your local data protection authority.",
    ],
  },
  {
    icon: Users,
    title: "6. Children's Privacy",
    content: [
      "PocketHost is intended for users aged 13 and over. We do not knowingly collect personal information from children under the age of 13. The app can be used without creating an account.",
      "If you are a parent or guardian and believe your child has provided personal information to us (e.g., through a support email), please contact us and we will promptly delete it.",
      "Parents should supervise their children's multiplayer gaming activities and ensure appropriate behavior when hosting public servers.",
    ],
  },
  {
    icon: Lock,
    title: "7. Data Security",
    content: [
      "We take reasonable measures to protect any data we process:",
      "• Account data is stored with Google Firebase rather than on servers we run ourselves.",
      "• Crash reports and analytics are processed through secure, industry-standard services.",
      "• App updates are delivered over HTTPS.",
      "However, no method of electronic transmission or storage is 100% secure. While we strive to protect your data, we cannot guarantee absolute security.",
    ],
  },
  {
    icon: Mail,
    title: "8. Contact Us",
    content: [
      "If you have any questions about this Privacy Policy or your data, you can reach us at:",
      "• **Email**: support@pockethost.online",
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
      <SEO
        title="Privacy Policy - PocketHost"
        description="Learn how PocketHost handles your data. Your Minecraft world files and server data stay on your device; ads, where shown, are served by Google AdMob."
        path="/privacy"
      />
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
            How PocketHost handles your data, and what our ad partner collects.
          </motion.p>

          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className={`text-sm ${
              theme === "dark" ? "text-white/30" : "text-black/40"
            }`}
          >
            Last updated: October 11, 2026
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
