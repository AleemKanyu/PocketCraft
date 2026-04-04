import { motion } from "framer-motion";
import { MessageCircle, Users } from "lucide-react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";

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
    url: "https://www.instagram.com/pocketcraftmc",
    color: "#E1306C",
  },
];

const CommunityCard = ({
  icon: Icon,
  title,
  description,
  url,
  color,
  index,
}: {
  icon: any;
  title: string;
  description: string;
  url: string;
  color: string;
  index: number;
}) => (
  <motion.a
    href={url}
    target="_blank"
    rel="noopener noreferrer"
    initial={{ opacity: 0, y: 20 }}
    whileInView={{ opacity: 1, y: 0 }}
    viewport={{ once: true }}
    transition={{ duration: 0.5, delay: index * 0.1 }}
    className="group border-2 border-black/10 rounded-lg p-8 hover:border-[#7FE620] hover:shadow-lg transition-all bg-white block"
  >
    <div className="flex items-start gap-4 mb-4">
      <div
        className="p-4 rounded-lg"
        style={{ backgroundColor: color + "20" }}
      >
        <Icon size={32} style={{ color }} />
      </div>
    </div>

    <h3 className="text-2xl font-bold text-black mb-2 group-hover:text-[#7FE620] transition-colors">
      {title}
    </h3>
    <p className="text-black/70 font-medium leading-relaxed">{description}</p>
  </motion.a>
);

export default function Community() {
  return (
    <div className="min-h-screen bg-white">
      <Navbar />
      {/* Header */}
      <section className="bg-white border-b-4 border-black/5 py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <h1 className="text-4xl md:text-5xl font-extrabold text-black mb-4">Join the Community</h1>
            <p className="text-black/60 text-lg max-w-2xl mx-auto">
              Connect with PocketCraft players around the world. Share your servers, get help, and be part of the growing community.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Community Links */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-6">
          {communityLinks.map((link, index) => (
            <CommunityCard key={index} {...link} index={index} />
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-6 bg-black/2 border-t-4 border-black/5">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-extrabold text-black mb-6">Ready to Join?</h2>
          <p className="text-black/60 text-lg mb-8">
            Jump into Discord and say hello to the PocketCraft community!
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
