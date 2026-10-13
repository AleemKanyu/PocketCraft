import { motion } from "framer-motion";
import { useParams, Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { useEffect } from "react";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { blogPosts } from "../data/blogData";
import { useTheme } from "../lib/ThemeContext";
import GooglePreferredSource from "../components/GooglePreferredSource";
import { renderMarkdown } from "../lib/renderMarkdown";
import NotFound from "./NotFound";
import { DEFAULT_OG_IMAGE, ORGANIZATION_ID, absoluteUrl, breadcrumbSchema, toIsoDate } from "../seo/site";

const categoryColors: Record<string, string> = {
  Announcement: "#7FE620",
  Feature: "#1CB0F6",
  Guide: "#FFD900",
  Comparison: "#7FE620",
  Update: "#1CB0F6",
  Release: "#FF85B3",
};

export default function BlogPost() {
  const { id } = useParams<{ id: string }>();
  const post = blogPosts.find((p) => p.id === id);
  const { theme } = useTheme();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [id]);

  if (!post) {
    return <NotFound />;
  }

  const postPath = `/blog/${post.id}`;
  const publishedTime = toIsoDate(post.date);
  const seoTitle = post.title.includes("PocketHost") ? post.title : `${post.title} | PocketHost`;

  const articleSchema = {
    "@type": "BlogPosting",
    "@id": `${absoluteUrl(postPath)}#article`,
    headline: post.title,
    description: post.excerpt,
    url: absoluteUrl(postPath),
    mainEntityOfPage: absoluteUrl(postPath),
    image: DEFAULT_OG_IMAGE,
    articleSection: post.category,
    inLanguage: "en",
    ...(publishedTime ? { datePublished: publishedTime, dateModified: publishedTime } : {}),
    author: { "@id": ORGANIZATION_ID },
    publisher: { "@id": ORGANIZATION_ID },
  };

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title={seoTitle}
        description={post.excerpt}
        path={postPath}
        type="article"
        publishedTime={publishedTime}
        modifiedTime={publishedTime}
        schema={[
          articleSchema,
          breadcrumbSchema([
            { name: "Blog", path: "/blog" },
            { name: post.title, path: postPath },
          ]),
        ]}
      />
      <Navbar />

      {/* Header */}
      <section
        className={`border-b-4 py-12 px-6 section-transition ${
          theme === "dark" ? "bg-[#0a0a0a] border-white/5" : "bg-white border-black/5"
        }`}
      >
        <div className="max-w-3xl mx-auto">
          <Link
            to="/blog"
            className="inline-flex items-center gap-2 text-[#7FE620] font-bold hover:gap-3 transition-all mb-8"
          >
            <ArrowLeft size={18} />
            Back to Blog
          </Link>

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <div className="flex items-start justify-between gap-4 mb-4">
              <h1 className={`text-4xl md:text-5xl font-extrabold flex-1 ${theme === "dark" ? "text-white" : "text-black"}`}>
                {post.title}
              </h1>
              <span
                className="px-2.5 py-1 rounded-sm text-xs font-mono font-bold text-white flex-shrink-0"
                style={{ backgroundColor: categoryColors[post.category] || "#7FE620" }}
              >
                {post.category}
              </span>
            </div>
            <p className={`font-medium text-lg ${theme === "dark" ? "text-white/35" : "text-black/50"}`}>
              <time dateTime={publishedTime}>{post.date}</time>
            </p>
          </motion.div>
        </div>
      </section>

      {/* Content */}
      <section className="py-20 px-6">
        <div className="max-w-3xl mx-auto">
          <motion.article
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className={`font-medium leading-relaxed space-y-6 ${theme === "dark" ? "text-white/65" : "text-black/70"}`}
          >
            {renderMarkdown(post.content, theme)}
          </motion.article>

          <GooglePreferredSource variant="card" className="mt-12 mb-4" />
        </div>
      </section>

      {/* Related Posts */}
      <section
        className={`py-20 px-6 border-t-4 section-transition ${
          theme === "dark" ? "bg-white/[0.02] border-white/5" : "bg-black/2 border-black/5"
        }`}
      >
        <div className="max-w-3xl mx-auto">
          <h2 className={`text-3xl font-bold mb-8 ${theme === "dark" ? "text-white" : "text-black"}`}>More Articles</h2>
          <div className="grid grid-cols-1 gap-6">
            {blogPosts
              .filter((p) => p.id !== id)
              .slice(0, 3)
              .map((relatedPost, index) => (
                <motion.div
                  key={relatedPost.id}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.5, delay: index * 0.1 }}
                >
                  <Link
                    to={`/blog/${relatedPost.id}`}
                    className={`block border-2 rounded-lg p-6 hover:border-[#7FE620] transition-all group ${
                      theme === "dark"
                        ? "border-white/10 bg-white/[0.02] hover:shadow-[0_20px_80px_rgba(127,230,32,0.08)]"
                        : "border-black/10 bg-white hover:shadow-lg"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-4">
                      <h3 className={`text-xl font-bold group-hover:text-[#7FE620] transition-colors flex-1 ${
                        theme === "dark" ? "text-white" : "text-black"
                      }`}>
                        {relatedPost.title}
                      </h3>
                      <span
                        className="px-2 py-0.5 rounded-sm text-xs font-mono font-bold text-white flex-shrink-0"
                        style={{ backgroundColor: categoryColors[relatedPost.category] || "#7FE620" }}
                      >
                        {relatedPost.category}
                      </span>
                    </div>
                    <p className={`text-sm font-medium mt-2 mb-3 ${theme === "dark" ? "text-white/35" : "text-black/50"}`}>
                      {relatedPost.date}
                    </p>
                    <p className={theme === "dark" ? "text-white/65 font-medium" : "text-black/70 font-medium"}>
                      {relatedPost.excerpt}
                    </p>
                  </Link>
                </motion.div>
              ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
