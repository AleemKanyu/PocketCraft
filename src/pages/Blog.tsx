import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { blogPosts } from "../data/blogData";

const categoryColors: Record<string, string> = {
  Announcement: "#7FE620",
  Feature: "#1CB0F6",
  Guide: "#FFD900",
};

export default function Blog() {
  return (
    <div className="min-h-screen bg-white">
      <Navbar />
      {/* Header */}
      <section className="bg-white border-b-4 border-black/5 py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <h1 className="text-4xl md:text-5xl font-extrabold text-black mb-4">Blog</h1>
            <p className="text-black/60 text-lg max-w-2xl mx-auto">
              Latest updates, guides, and news about PocketCraft.
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
                className="block border-2 border-black/10 rounded-lg p-6 hover:border-[#7FE620] hover:shadow-lg transition-all group cursor-pointer"
              >
                <div className="flex items-start justify-between gap-4 mb-3">
                  <h2 className="text-2xl font-bold text-black group-hover:text-[#7FE620] transition-colors flex-1">
                    {post.title}
                  </h2>
                  <span
                    className="px-3 py-1 rounded-full text-xs font-bold text-white flex-shrink-0"
                    style={{ backgroundColor: categoryColors[post.category] || "#7FE620" }}
                  >
                    {post.category}
                  </span>
                </div>

                <p className="text-black/50 text-sm font-medium mb-3">{post.date}</p>
                <p className="text-black/70 font-medium leading-relaxed">{post.excerpt}</p>

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
      <section className="py-20 px-6 bg-black/2 border-t-4 border-black/5">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-extrabold text-black mb-6">Stay Updated</h2>
          <p className="text-black/60 text-lg mb-8">
            Follow my Discord and Instagram for the latest news about PocketCraft.
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
              href="https://www.instagram.com/pocketcraftmc"
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
