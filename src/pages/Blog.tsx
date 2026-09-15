import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { blogPosts } from "../data/blogData";
import { useTheme } from "../lib/ThemeContext";

const categoryColors: Record<string, string> = {
  Announcement: "#7FE620",
  Feature: "#1CB0F6",
  Guide: "#FFD900",
  Comparison: "#7FE620",
  Update: "#1CB0F6",
  Release: "#FF85B3",
};

export default function Blog() {
  const { theme } = useTheme();

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title="Blog & Updates - PocketHost"
        description="Latest announcements, engineering updates, server optimization tips, and feature releases for PocketHost Android Minecraft hosting."
        path="/blog"
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
              Blog
            </h1>
            <p className={`text-lg max-w-2xl mx-auto ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
              Latest updates, guides, and news about PocketHost.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Posts */}
      <section className="py-20 px-6">
        <div className="max-w-3xl mx-auto space-y-6">
          {blogPosts.map((post, index) => (
            <motion.div
              key={post.id}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: index * 0.1 }}
            >
              <Link
                to={`/blog/${post.id}`}
                className={`block border-2 rounded-lg p-6 hover:border-[#7FE620] transition-all group cursor-pointer ${
                  theme === "dark"
                    ? "border-white/10 bg-white/[0.02] hover:shadow-[0_20px_80px_rgba(127,230,32,0.08)]"
                    : "border-black/10 bg-white hover:shadow-lg"
                }`}
              >
                <div className="flex items-start justify-between gap-4 mb-3">
                  <h2 className={`text-2xl font-bold group-hover:text-[#7FE620] transition-colors flex-1 ${
                    theme === "dark" ? "text-white" : "text-black"
                  }`}>
                    {post.title}
                  </h2>
                  <span
                    className="px-2.5 py-0.5 rounded-sm text-xs font-mono font-bold text-white flex-shrink-0"
                    style={{ backgroundColor: categoryColors[post.category] || "#7FE620" }}
                  >
                    {post.category}
                  </span>
                </div>

                <p className={`text-sm font-medium mb-3 ${theme === "dark" ? "text-white/35" : "text-black/50"}`}>
                  {post.date}
                </p>
                <p className={`font-medium leading-relaxed ${theme === "dark" ? "text-white/65" : "text-black/70"}`}>
                  {post.excerpt}
                </p>

                <div className="mt-4">
                  <span className="text-[#7FE620] font-bold text-sm uppercase tracking-wider group-hover:gap-2">
                    Read More →
                  </span>
                </div>
              </Link>
            </motion.div>
          ))}
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
            Stay Updated
          </h2>
          <p className={`text-lg mb-8 ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
            Follow my Discord and Instagram for the latest news about PocketHost.
          </p>
          <div className="flex flex-wrap gap-4 justify-center">
            <a
              href="https://discord.com/invite/nc7ceYWVfT"
              target="_blank"
              rel="noopener noreferrer"
              className="btn-duo px-8 py-3 inline-block text-sm"
            >
              Discord
            </a>
            <a
              href="https://www.instagram.com/pockethostmc"
              target="_blank"
              rel="noopener noreferrer"
              className="btn-duo-secondary px-8 py-3 inline-block text-sm"
            >
              Instagram
            </a>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
