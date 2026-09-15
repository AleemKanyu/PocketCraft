import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import { useState } from "react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const faqs = [
  {
    question: "Do I need a PC to run PocketHost?",
    answer:
      "No! PocketHost runs entirely on your Android phone. You don't need a computer, laptop, or separate server. All you need is an Android device and an internet connection.",
  },
  {
    question: "Can Bedrock players join my Java server?",
    answer:
      "Yes! With cross-platform support, players on Bedrock, Pocket Edition, and consoles can join your Java Edition server. It just works.",
  },
  {
    question: "How much does it cost?",
    answer:
      "PocketHost is free to download and use. You can host one world server with unlimited player slots. However, relay hosting involves costs to keep servers running online across the globe. Some premium features may be introduced in the future, such as hosting multiple worlds simultaneously, advanced analytics, or priority relay infrastructure. These may be available through a one-time purchase or optional ads. The core experience will always remain free.",
  },
  {
    question: "Will my server stay online when I lock my phone?",
    answer:
      "Yes. Once your server starts, it keeps running even if your screen turns off. You can use your phone normally while friends play on your server.",
  },
  {
    question: "What's the maximum number of players?",
    answer:
      "It depends on your phone's hardware. Most modern Android devices can handle 10-20 players comfortably. More powerful devices can support more players.",
  },
  {
    question: "Can I install plugins and mods?",
    answer:
      "Yes! PocketHost supports Bukkit plugins and Spigot community plugins. Drag and drop .jar files directly in the app. Changes take effect immediately.",
  },
  {
    question: "Is my world data safe?",
    answer:
      "Yes. Your world files stay on your device. You can also backup to Google Drive for extra safety. I never access or inspect your game data.",
  },
  {
    question: "Do I need port forwarding?",
    answer:
      "No! PocketHost uses proprietary relay technology so players connect without any router configuration. Just tap start and share the join link.",
  },
];

export default function FAQ() {
  const { theme } = useTheme();
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const faqSchema = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: faqs.map((f) => ({
      "@type": "Question",
      name: f.question,
      acceptedAnswer: {
        "@type": "Answer",
        text: f.answer,
      },
    })),
  };

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title="Frequently Asked Questions - PocketHost"
        description="Got questions about running a Minecraft server on Android? Find answers about Bedrock crossplay, port forwarding, performance, and plugins."
        path="/faq"
        schema={faqSchema}
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
              Frequently Asked Questions
            </h1>
            <p className={`text-lg max-w-2xl mx-auto ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
              Everything you need to know about PocketHost. Can't find your answer? Reach out on Discord.
            </p>
          </motion.div>
        </div>
      </section>

      {/* FAQs */}
      <section className="py-20 px-6">
        <div className="max-w-3xl mx-auto space-y-4">
          {faqs.map((faq, index) => {
            const isOpen = openIndex === index;

            return (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                className={`border-2 rounded-lg overflow-hidden hover:border-[#7FE620] transition-colors ${
                  theme === "dark" ? "border-white/10 bg-white/[0.02]" : "border-black/10 bg-white"
                }`}
              >
                <button
                  onClick={() => setOpenIndex(isOpen ? null : index)}
                  className={`w-full px-6 py-4 flex items-center justify-between transition-colors ${
                    theme === "dark" ? "bg-white/[0.02] hover:bg-white/[0.04]" : "bg-white hover:bg-black/2"
                  }`}
                >
                  <h3 className={`text-lg font-bold text-left ${theme === "dark" ? "text-white" : "text-black"}`}>
                    {faq.question}
                  </h3>
                  <ChevronDown
                    size={24}
                    className={`text-[#7FE620] flex-shrink-0 transition-transform ${isOpen ? "rotate-180" : ""}`}
                  />
                </button>

                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: "auto" }}
                      exit={{ opacity: 0, height: 0 }}
                      className={`px-6 py-4 border-t-2 ${
                        theme === "dark" ? "bg-white/[0.03] border-white/10" : "bg-black/2 border-black/10"
                      }`}
                    >
                      <p className={`font-medium leading-relaxed ${theme === "dark" ? "text-white/65" : "text-black/70"}`}>
                        {faq.answer}
                      </p>
                    </motion.div>
                  )}
                </AnimatePresence>
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
            Still have questions?
          </h2>
          <p className={`text-lg mb-8 ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
            Join the Discord community and chat with other players.
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
