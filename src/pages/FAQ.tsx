import { motion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import { useState } from "react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";

const faqs = [
  {
    question: "Do I need a PC to run PocketCraft?",
    answer:
      "No! PocketCraft runs entirely on your Android phone. You don't need a computer, laptop, or separate server. All you need is an Android device and an internet connection.",
  },
  {
    question: "Can Bedrock players join my Java server?",
    answer:
      "Yes! With cross-platform support, players on Bedrock, Pocket Edition, and consoles can join your Java Edition server. It just works.",
  },
  {
    question: "How much does it cost?",
    answer:
      "PocketCraft is free to download and use. You can host one world server with unlimited player slots. However, relay hosting involves costs to keep servers running online across the globe. Some premium features may be introduced in the future, such as hosting multiple worlds simultaneously, advanced analytics, or priority relay infrastructure. These may be available through a one-time purchase or optional ads. The core experience will always remain free.",
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
      "Yes! PocketCraft supports Bukkit plugins and Spigot community plugins. Drag and drop .jar files directly in the app. Changes take effect immediately.",
  },
  {
    question: "Is my world data safe?",
    answer:
      "Yes. Your world files stay on your device. You can also backup to Google Drive for extra safety. I never access or inspect your game data.",
  },
  {
    question: "Do I need port forwarding?",
    answer:
      "No! PocketCraft uses proprietary relay technology so players connect without any router configuration. Just tap start and share the join link.",
  },
];

const FAQItem = ({ faq, index }: { faq: (typeof faqs)[0]; index: number; key?: number | string }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.5, delay: index * 0.1 }}
      className="border-2 border-black/10 rounded-lg overflow-hidden hover:border-[#7FE620] transition-colors"
    >
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full px-6 py-4 flex items-center justify-between bg-white hover:bg-black/2 transition-colors"
      >
        <h3 className="text-lg font-bold text-black text-left">{faq.question}</h3>
        <ChevronDown
          size={24}
          className={`text-[#7FE620] flex-shrink-0 transition-transform ${
            isOpen ? "rotate-180" : ""
          }`}
        />
      </button>

      {isOpen && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: "auto" }}
          exit={{ opacity: 0, height: 0 }}
          className="px-6 py-4 bg-black/2 border-t-2 border-black/10"
        >
          <p className="text-black/70 font-medium leading-relaxed">{faq.answer}</p>
        </motion.div>
      )}
    </motion.div>
  );
};

export default function FAQ() {
  return (
    <div className="min-h-screen bg-white">
      <Navbar />
      {/* Header */}
      <section className="bg-white border-b-4 border-black/5 py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <h1 className="text-4xl md:text-5xl font-extrabold text-black mb-4">Frequently Asked Questions</h1>
            <p className="text-black/60 text-lg max-w-2xl mx-auto">
              Everything you need to know about PocketCraft. Can't find your answer? Reach out on Discord.
            </p>
          </motion.div>
        </div>
      </section>

      {/* FAQs */}
      <section className="py-20 px-6">
        <div className="max-w-3xl mx-auto space-y-4">
          {faqs.map((faq, index) => (
            <FAQItem key={index} faq={faq} index={index} />
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-6 bg-black/2 border-t-4 border-black/5">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-extrabold text-black mb-6">Still have questions?</h2>
          <p className="text-black/60 text-lg mb-8">Join the Discord community and chat with other players.</p>
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
