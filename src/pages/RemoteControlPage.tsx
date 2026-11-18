import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { ShieldCheck, Radio, ArrowLeft } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import ServerControl from "../components/remote/ServerControl";
import AccountRemote from "../components/remote/AccountRemote";
import { useTheme } from "../lib/ThemeContext";

/**
 * pockethost.online/remote
 *   #CODE            share link: start and stop (more with Premium)
 *   #CODE.ADMINKEY   Premium admin link: full control
 *   (no hash)        sign in with Google and pick a server from the account (Premium)
 */
export default function RemoteControlPage() {
  const { theme } = useTheme();
  const isDark = theme === "dark";
  const [code, setCode] = useState("");
  const [adminKey, setAdminKey] = useState("");
  const [ready, setReady] = useState(false);

  // The hash is only known in the browser (the page is prerendered without it).
  useEffect(() => {
    function read() {
      const raw = window.location.hash.replace(/^#/, "").trim();
      const [shareCode, key] = raw.split(".");
      setCode(shareCode || "");
      setAdminKey(key || "");
      setReady(true);
    }
    read();
    window.addEventListener("hashchange", read);
    return () => window.removeEventListener("hashchange", read);
  }, []);

  const muted = isDark ? "text-white/55" : "text-black/55";
  const faint = isDark ? "text-white/35" : "text-black/40";

  return (
    <div className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${isDark ? "bg-[#0a0a0a] text-white" : "bg-[#f6f7f5] text-black"}`}>
      <SEO
        title="Remote Control | PocketHost"
        description="Start, stop and manage your PocketHost Minecraft servers from any browser."
        path="/remote"
        noindex
      />
      <Navbar />

      <main className="relative pt-24 sm:pt-28 pb-20 px-4 sm:px-6 overflow-hidden">
        <div className="absolute top-10 left-1/2 -translate-x-1/2 w-[700px] h-[320px] bg-[#7FE620]/[0.07] rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-2xl mx-auto relative z-10">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="mb-6 sm:mb-8">
            <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-[#7FE620]/15 ${isDark ? "text-[#7FE620]" : "text-[#4c9a0c]"}`}>
              <Radio className="w-3.5 h-3.5" /> Remote Control
            </div>
            <h1 className="mt-3 text-3xl sm:text-4xl font-extrabold tracking-tight">
              {code ? "Your server" : "Your servers, anywhere"}
            </h1>
            <p className={`mt-1.5 text-sm sm:text-base ${muted}`}>
              {code
                ? adminKey
                  ? "Full control of your PocketHost server from this browser."
                  : "Start and stop this PocketHost server from your browser."
                : "Control the Minecraft servers running on your phones."}
            </p>
            {code && (
              <button
                type="button"
                onClick={() => {
                  history.replaceState(null, "", window.location.pathname);
                  setCode("");
                  setAdminKey("");
                }}
                className={`mt-3 inline-flex items-center gap-1.5 text-sm font-semibold ${muted} hover:underline`}
              >
                <ArrowLeft className="w-4 h-4" /> All my servers
              </button>
            )}
          </motion.div>

          {ready && (code ? <ServerControl key={code} code={code} adminKey={adminKey} isDark={isDark} /> : <AccountRemote isDark={isDark} />)}

          <div className={`mt-8 flex items-start gap-3 text-xs leading-relaxed ${faint}`}>
            <ShieldCheck className="w-4 h-4 shrink-0 text-[#5ab30f]" />
            <div>
              <p>
                {adminKey
                  ? "This is your admin link: it has full control of the server. Keep it private, and make a new link in the app if it leaks."
                  : "Your phone decides what a link may do. PocketHost never exposes your phone's own files to the web."}
              </p>
              <p className="mt-1.5">PocketHost is not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</p>
            </div>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
