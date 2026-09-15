import { motion } from "framer-motion";
import { MessageCircle, Users } from "lucide-react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const communityLinks = [
  {
    icon: MessageCircle,
    title: "Discord Server",
    description: "Chat with players, report bugs, and get support from the community.",
    url: "https://discord.com/invite/nc7ceYWVfT",
    color: "#5865F2",
  },
  {
    icon: Users,
    title: "Instagram",
    description: "Follow for news, updates, and showcase of amazing servers.",
    url: "https://www.instagram.com/pockethostmc",
    color: "#E1306C",
  },
];

export default function Community() {
  const { theme } = useTheme();

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title="Community & Support - PocketHost"
        description="Join the PocketHost Minecraft community on Discord and Instagram. Connect with server admins, get help, and share feedback."
        path="/community"
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
              Join the Community
            </h1>
            <p className={`text-lg max-w-2xl mx-auto ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
              Connect with PocketHost players around the world. Share your servers, get help, and be part of the growing community.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Community Links */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-6">
          {communityLinks.map((link, index) => {
            const Icon = link.icon;

            return (
              <motion.a
                key={index}
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                className={`group border-2 rounded-lg p-8 hover:border-[#7FE620] transition-all block ${
                  theme === "dark"
                    ? "border-white/10 bg-white/[0.02] hover:shadow-[0_20px_80px_rgba(127,230,32,0.08)]"
                    : "border-black/10 bg-white hover:shadow-lg"
                }`}
              >
                <div className="flex items-start gap-4 mb-4">
                  <div
                    className="p-4 rounded-lg"
                    style={{ backgroundColor: link.color + "20" }}
                  >
                    <Icon size={32} style={{ color: link.color }} />
                  </div>
                </div>

                <h3 className={`text-2xl font-bold mb-2 group-hover:text-[#7FE620] transition-colors ${
                  theme === "dark" ? "text-white" : "text-black"
                }`}>
                  {link.title}
                </h3>
                <p className={`font-medium leading-relaxed ${theme === "dark" ? "text-white/65" : "text-black/70"}`}>
                  {link.description}
                </p>
              </motion.a>
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
            Ready to Join?
          </h2>
          <p className={`text-lg mb-8 ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
            Jump into Discord and say hello to the PocketHost community!
          </p>
          <a
            href="https://discord.com/invite/nc7ceYWVfT"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-duo px-12 py-4 inline-block"
          >
            Join Discord Now
          </a>
        </div>
      </section>

      <Footer />
    </div>
  );
}
