import { Link } from "react-router-dom";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

const destinations = [
  { to: "/", label: "Home" },
  { to: "/faq", label: "FAQ" },
  { to: "/blog", label: "Blog" },
  { to: "/community", label: "Community" },
];

export default function NotFound() {
  const { theme } = useTheme();

  return (
    <div className={`min-h-screen section-transition ${theme === "dark" ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title="Page Not Found | PocketHost"
        description="This page does not exist on pockethost.online. Head back to the PocketHost home page, FAQ or blog."
        path="/404"
        noindex
      />
      <Navbar />
      <section className="py-28 px-6">
        <div className="max-w-2xl mx-auto text-center">
          <p className="font-mono text-sm font-bold uppercase tracking-widest text-[#7FE620] mb-4">Error 404</p>
          <h1 className={`text-4xl md:text-5xl font-extrabold mb-4 ${theme === "dark" ? "text-white" : "text-black"}`}>
            This page does not exist
          </h1>
          <p className={`text-lg mb-10 ${theme === "dark" ? "text-white/55" : "text-black/60"}`}>
            The link may be old or mistyped. Try one of these instead.
          </p>
          <div className="flex flex-wrap gap-4 justify-center">
            {destinations.map((destination, index) => (
              <Link
                key={destination.to}
                to={destination.to}
                className={`${index === 0 ? "btn-duo" : "btn-duo-secondary"} px-8 py-3 inline-block text-sm`}
              >
                {destination.label}
              </Link>
            ))}
          </div>
        </div>
      </section>
      <Footer />
    </div>
  );
}
