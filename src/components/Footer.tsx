import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Github, Instagram } from "lucide-react";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";
import { useTheme } from "../lib/ThemeContext";

const quickLinks = [
  { href: "/", label: "Home" },
  { href: "/faq", label: "FAQ" },
  { href: "/blog", label: "Blog" },
  { href: "/roadmap", label: "Roadmap" },
  { href: "/community", label: "Community" },
];

const legalLinks = [
  { href: "/privacy", label: "Privacy Policy" },
  { href: "/terms", label: "Terms of Service" },
  { href: "mailto:support@pocketcraft.online", label: "Contact Us", external: true },
];

export default function Footer() {
  const { theme } = useTheme();

  return (
    <footer className="relative z-0 py-20 px-4 sm:px-6 bg-[#080808] overflow-hidden">
      {/* Animated gradient top border */}
      <div className="absolute top-0 left-0 right-0 h-1">
        <div
          className="w-full h-full animate-gradient-shift"
          style={{
            background: "linear-gradient(90deg, #7FE620, #1CB0F6, #FFD900, #FF85B3, #7FE620)",
            backgroundSize: "300% 100%",
          }}
        />
      </div>

      {/* Background glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-[#7FE620]/5 rounded-full blur-3xl pointer-events-none" />

      {/* Subtle grid */}
      <div className="absolute inset-0 opacity-[0.02] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:48px_48px]" />
      </div>

      <div className="max-w-6xl mx-auto relative z-10">
        {/* Main Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-12 mb-16">
          {/* Brand Column */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
            className="sm:col-span-2 lg:col-span-1"
          >
            <Link to="/" className="flex items-center gap-3 group mb-4">
              <motion.img
                src={MINECRAFT_ICONS.brand}
                alt="PocketCraft"
                className="w-10 h-10 rounded-full object-cover border border-white/15"
                whileHover={{ rotate: 12 }}
                transition={{ type: "spring", stiffness: 300 }}
                onError={(e) => {
                  e.currentTarget.onerror = null;
                  e.currentTarget.src = "/grass-block.png";
                }}
              />
              <span className="text-xl font-minecraft text-white">PocketCraft</span>
            </Link>
            <p className="text-white/30 text-sm leading-relaxed mb-6 max-w-xs">
              Host your own Minecraft Java server from your Android phone. Free, no root, instant setup.
            </p>
            {/* Social links */}
            <div className="flex items-center gap-3">
              <motion.a
                href="https://discord.com/invite/nc7ceYWVfT"
                target="_blank"
                rel="noopener noreferrer"
                className="w-9 h-9 rounded-lg bg-white/5 hover:bg-[#5865F2]/20 flex items-center justify-center text-white/40 hover:text-[#5865F2] transition-all"
                whileHover={{ scale: 1.1, y: -2 }}
                whileTap={{ scale: 0.95 }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189z"/>
                </svg>
              </motion.a>
              <motion.a
                href="https://github.com/AleemKanyu/PocketCraft"
                target="_blank"
                rel="noopener noreferrer"
                className="w-9 h-9 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-white/40 hover:text-white transition-all"
                whileHover={{ scale: 1.1, y: -2 }}
                whileTap={{ scale: 0.95 }}
              >
                <Github size={16} />
              </motion.a>
              <motion.a
                href="https://instagram.com"
                target="_blank"
                rel="noopener noreferrer"
                className="w-9 h-9 rounded-lg bg-white/5 hover:bg-[#E1306C]/20 flex items-center justify-center text-white/40 hover:text-[#E1306C] transition-all"
                whileHover={{ scale: 1.1, y: -2 }}
                whileTap={{ scale: 0.95 }}
              >
                <Instagram size={16} />
              </motion.a>
            </div>
          </motion.div>

          {/* Quick Links */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <h4 className="text-xs font-bold uppercase tracking-widest text-[#7FE620] mb-6">
              Quick Links
            </h4>
            <ul className="space-y-3">
              {quickLinks.map((link) => (
                <li key={link.href}>
                  <Link
                    to={link.href}
                    className="text-white/40 hover:text-white text-sm transition-colors duration-200 flex items-center gap-2 group"
                  >
                    <span className="w-0 group-hover:w-2 h-0.5 bg-[#7FE620] transition-all duration-200 rounded-full" />
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </motion.div>

          {/* Legal */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.2 }}
          >
            <h4 className="text-xs font-bold uppercase tracking-widest text-[#7FE620] mb-6">
              Legal
            </h4>
            <ul className="space-y-3">
              {legalLinks.map((link) => (
                <li key={link.href}>
                  {link.external ? (
                    <a
                      href={link.href}
                      className="text-white/40 hover:text-white text-sm transition-colors duration-200 flex items-center gap-2 group"
                    >
                      <span className="w-0 group-hover:w-2 h-0.5 bg-[#7FE620] transition-all duration-200 rounded-full" />
                      {link.label}
                    </a>
                  ) : (
                    <Link
                      to={link.href}
                      className="text-white/40 hover:text-white text-sm transition-colors duration-200 flex items-center gap-2 group"
                    >
                      <span className="w-0 group-hover:w-2 h-0.5 bg-[#7FE620] transition-all duration-200 rounded-full" />
                      {link.label}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </motion.div>

          {/* Download */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.3 }}
          >
            <h4 className="text-xs font-bold uppercase tracking-widest text-[#7FE620] mb-6">
              Download
            </h4>
            <p className="text-white/30 text-sm mb-4 leading-relaxed">
              Get PocketCraft on your Android device. Available as APK.
            </p>
            <motion.a
              href="/api/apk/download"
              download
              className="inline-flex items-center gap-2 px-6 py-3 bg-[#7FE620] hover:bg-[#6FD614] text-black text-sm font-bold uppercase tracking-wider rounded-xl transition-colors shadow-[0_4px_0_0_rgba(0,0,0,0.2)]"
              whileHover={{ scale: 1.05, y: -2 }}
              whileTap={{ scale: 0.98 }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
              Download APK
            </motion.a>
            <p className="text-white/20 text-[10px] mt-3 uppercase tracking-wider">
              Stable release • Android 8.0+
            </p>
          </motion.div>
        </div>

        {/* Bottom bar */}
        <div className="pt-8 border-t border-white/[0.06] flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-white/15 text-[10px] font-mono uppercase tracking-[0.2em] text-center sm:text-left">
            © {new Date().getFullYear()} PocketCraft. All Rights Reserved.
          </p>
          <p className="text-white/10 text-[10px] uppercase tracking-[0.15em]">
            Made for players, by players 🎮
          </p>
        </div>
      </div>
    </footer>
  );
}
