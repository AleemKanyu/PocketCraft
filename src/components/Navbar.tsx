import { Link, useLocation } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { useState } from "react";
import { Menu, X, Sun, Moon } from "lucide-react";
import { useTheme } from "../lib/ThemeContext";

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();

  const links = [
    { href: "/", label: "Home" },
    { href: "/faq", label: "FAQ" },
    { href: "/blog", label: "Blog" },
    { href: "/roadmap", label: "Roadmap" },
    { href: "/community", label: "Community" },
  ];

  return (
    <nav className={`sticky top-0 z-50 backdrop-blur-xl border-b-2 section-transition ${
      theme === "dark"
        ? "bg-[#0a0a0a]/90 border-white/5"
        : "bg-white/90 border-black/5"
    }`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 sm:py-4">
        <div className="flex items-center justify-between">
          {/* Logo + App Name */}
          <Link to="/" className="flex items-center gap-2 sm:gap-3 group">
            <motion.img
              src="/app-icon.png"
              alt="PocketCraft"
              className="w-9 h-9 sm:w-10 sm:h-10 rounded-lg"
              whileHover={{ rotate: 12, scale: 1.1 }}
              transition={{ type: "spring", stiffness: 300 }}
            />
            <span className={`text-lg sm:text-2xl font-extrabold font-minecraft ${
              theme === "dark" ? "text-white" : "text-black"
            }`}>
              PocketCraft
            </span>
            <span className="text-[10px] font-bold uppercase px-2 py-1 rounded-full bg-orange-100 text-orange-600 ml-2">
              Beta
            </span>
          </Link>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            {links.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                className={`relative font-bold uppercase tracking-wider text-sm transition-colors ${
                  location.pathname === link.href
                    ? "text-[#7FE620]"
                    : theme === "dark"
                    ? "text-white/50 hover:text-white"
                    : "text-black/60 hover:text-black"
                }`}
              >
                {link.label}
                {location.pathname === link.href && (
                  <motion.div
                    layoutId="nav-indicator"
                    className="absolute -bottom-1 left-0 right-0 h-0.5 bg-[#7FE620] rounded-full"
                    transition={{ type: "spring", stiffness: 500, damping: 30 }}
                  />
                )}
              </Link>
            ))}

            {/* Dark Mode Toggle - Hidden as requested */}
            <motion.button
              onClick={toggleTheme}
              className={`hidden relative w-10 h-10 rounded-xl flex items-center justify-center transition-colors ${
                theme === "dark"
                  ? "bg-white/10 hover:bg-white/15 text-yellow-400"
                  : "bg-black/5 hover:bg-black/10 text-gray-700"
              }`}
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9, rotate: 180 }}
              transition={{ type: "spring", stiffness: 300 }}
              aria-label="Toggle dark mode"
            >
              <AnimatePresence mode="wait" initial={false}>
                {theme === "dark" ? (
                  <motion.div
                    key="sun"
                    initial={{ rotate: -90, opacity: 0, scale: 0.5 }}
                    animate={{ rotate: 0, opacity: 1, scale: 1 }}
                    exit={{ rotate: 90, opacity: 0, scale: 0.5 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Sun size={18} />
                  </motion.div>
                ) : (
                  <motion.div
                    key="moon"
                    initial={{ rotate: 90, opacity: 0, scale: 0.5 }}
                    animate={{ rotate: 0, opacity: 1, scale: 1 }}
                    exit={{ rotate: -90, opacity: 0, scale: 0.5 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Moon size={18} />
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.button>
          </div>

          {/* Mobile: Toggle + Menu */}
          <div className="flex items-center gap-2 md:hidden">
            <motion.button
              onClick={toggleTheme}
              className={`hidden w-9 h-9 rounded-xl flex items-center justify-center ${
                theme === "dark"
                  ? "bg-white/10 text-yellow-400"
                  : "bg-black/5 text-gray-700"
              }`}
              whileTap={{ scale: 0.9, rotate: 180 }}
              aria-label="Toggle dark mode"
            >
              {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
            </motion.button>

            <button
              onClick={() => setIsOpen(!isOpen)}
              className={`p-2 ${theme === "dark" ? "text-white" : "text-black"}`}
            >
              {isOpen ? <X size={24} /> : <Menu size={24} />}
            </button>
          </div>
        </div>

        {/* Mobile Navigation */}
        <AnimatePresence>
          {isOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.3, ease: "easeInOut" }}
              className={`md:hidden flex flex-col gap-4 mt-4 pt-4 border-t-2 overflow-hidden ${
                theme === "dark" ? "border-white/5" : "border-black/5"
              }`}
            >
              {links.map((link, i) => (
                <motion.div
                  key={link.href}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.05 }}
                >
                  <Link
                    to={link.href}
                    onClick={() => setIsOpen(false)}
                    className={`block font-bold uppercase tracking-wider text-sm transition-colors ${
                      location.pathname === link.href
                        ? "text-[#7FE620]"
                        : theme === "dark"
                        ? "text-white/50 hover:text-white"
                        : "text-black/60 hover:text-black"
                    }`}
                  >
                    {link.label}
                  </Link>
                </motion.div>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </nav>
  );
}
