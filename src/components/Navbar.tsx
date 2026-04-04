import { Link, useLocation } from "react-router-dom";
import { motion } from "framer-motion";
import { useState } from "react";
import { Menu, X } from "lucide-react";

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const location = useLocation();

  const links = [
    { href: "/", label: "Home" },
    { href: "/faq", label: "FAQ" },
    { href: "/blog", label: "Blog" },
    { href: "/roadmap", label: "Roadmap" },
    { href: "/community", label: "Community" },
  ];

  return (
    <nav className="sticky top-0 z-50 bg-white border-b-2 border-black/5 backdrop-blur">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 sm:py-4">
        <div className="flex items-center justify-between">
          {/* Logo + App Name */}
          <Link to="/" className="flex items-center gap-2 sm:gap-3 group">
            <img
              src="/app-icon.png"
              alt="PocketCraft"
              className="w-9 h-9 sm:w-10 sm:h-10 rounded-lg"
            />
            <span className="text-lg sm:text-2xl font-extrabold text-black font-minecraft">
              PocketCraft
            </span>
          </Link>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            {links.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                className={`font-bold uppercase tracking-wider text-sm transition-colors ${
                  location.pathname === link.href
                    ? "text-[#7FE620]"
                    : "text-black/60 hover:text-black"
                }`}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsOpen(!isOpen)}
            className="md:hidden text-black p-2"
          >
            {isOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>

        {/* Mobile Navigation */}
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="md:hidden flex flex-col gap-4 mt-4 pt-4 border-t-2 border-black/5"
          >
            {links.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                onClick={() => setIsOpen(false)}
                className={`font-bold uppercase tracking-wider text-sm transition-colors ${
                  location.pathname === link.href
                    ? "text-[#7FE620]"
                    : "text-black/60 hover:text-black"
                }`}
              >
                {link.label}
              </Link>
            ))}
          </motion.div>
        )}
      </div>
    </nav>
  );
}
