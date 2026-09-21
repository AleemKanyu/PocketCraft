import { useState } from "react";
import { motion } from "framer-motion";
import { Trash2, AlertTriangle, CheckCircle, Mail } from "lucide-react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";

type FormState = "idle" | "submitting" | "success" | "error";

export default function DeleteAccount() {
  const { theme } = useTheme();
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState("");
  const [formState, setFormState] = useState<FormState>("idle");
  const [errorMsg, setErrorMsg] = useState("");

  const isDark = theme === "dark";
  const inputBase = `w-full px-4 py-3 rounded-xl border text-sm outline-none transition-colors ${
    isDark
      ? "bg-white/5 border-white/10 text-white placeholder-white/30 focus:border-[#7FE620]/60"
      : "bg-black/5 border-black/10 text-black placeholder-black/30 focus:border-[#5ab30f]/60"
  }`;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim()) return;
    setFormState("submitting");
    setErrorMsg("");

    try {
      // Send deletion request via mailto link as a simple fallback that
      // works without a backend. The support team processes requests manually.
      const subject = encodeURIComponent("Account Deletion Request");
      const body = encodeURIComponent(
        `I would like to request deletion of my PocketHost account.\n\nEmail: ${email.trim()}\nReason: ${reason.trim() || "Not specified"}\n\nPlease delete all data associated with this account.`
      );
      window.location.href = `mailto:support@pockethost.online?subject=${subject}&body=${body}`;
      // Give the mail client time to open, then show success
      setTimeout(() => setFormState("success"), 800);
    } catch {
      setFormState("error");
      setErrorMsg("Something went wrong. Please email support@pockethost.online directly.");
    }
  }

  return (
    <div
      className={`min-h-screen selection:bg-[#7FE620] selection:text-black font-sans section-transition ${
        isDark ? "bg-[#0a0a0a] text-white" : "bg-white text-black"
      }`}
    >
      <SEO
        title="Delete Account – PocketHost"
        description="Request deletion of your PocketHost account and all associated data. We process all requests within 30 days."
        path="/delete-account"
      />
      <Navbar />

      {/* Hero */}
      <section
        className={`relative py-24 sm:py-32 px-6 overflow-hidden ${
          isDark ? "bg-[#0a0a0a]" : "bg-white"
        }`}
      >
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-red-500/6 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-2xl mx-auto relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="flex items-center gap-3 mb-6"
          >
            <Trash2 className="w-6 h-6 text-red-400" />
            <span className="text-xs font-bold uppercase tracking-wider text-red-400">
              Account Management
            </span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
            className={`text-4xl md:text-5xl font-extrabold mb-4 ${
              isDark ? "text-white" : "text-black"
            }`}
          >
            Delete Your Account
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className={`text-lg ${isDark ? "text-white/50" : "text-black/60"}`}
          >
            You can request deletion of your PocketHost account and all
            associated data at any time.
          </motion.p>
        </div>
      </section>

      {/* Content */}
      <section
        className={`py-12 px-6 border-t-4 ${
          isDark ? "border-white/5" : "border-black/5"
        }`}
      >
        <div className="max-w-2xl mx-auto space-y-10">

          {/* What gets deleted */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
            className={`rounded-2xl border p-6 ${
              isDark ? "border-white/8 bg-white/3" : "border-black/8 bg-black/3"
            }`}
          >
            <h2
              className={`text-lg font-bold mb-4 ${
                isDark ? "text-white" : "text-black"
              }`}
            >
              What will be deleted
            </h2>
            <ul
              className={`space-y-2 text-sm ${
                isDark ? "text-white/60" : "text-black/60"
              }`}
            >
              {[
                "Your account credentials and sign-in information",
                "Your donation / supporter tier membership history",
                "Any device-linked identifiers associated with your account",
                "Support correspondence linked to your email address",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2">
                  <span className="mt-0.5 text-red-400">✕</span>
                  {item}
                </li>
              ))}
            </ul>
            <p
              className={`mt-4 text-xs ${
                isDark ? "text-white/30" : "text-black/40"
              }`}
            >
              Note: Minecraft world data, server configs, and plugin files are
              stored locally on your device and are not held by PocketHost
              servers. Uninstalling the app removes this data from your device.
            </p>
          </motion.div>

          {/* Warning */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.05 }}
            className="flex gap-3 rounded-2xl border border-amber-500/20 bg-amber-500/5 p-5"
          >
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-sm text-amber-300/80">
              <strong className="text-amber-300">This action is permanent.</strong>{" "}
              Once your account is deleted, your supporter status cannot be
              restored. Active subscriptions should be cancelled in Google Play
              before requesting deletion.
            </p>
          </motion.div>

          {/* Form or success */}
          {formState === "success" ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="flex flex-col items-center gap-4 py-12 text-center"
            >
              <CheckCircle className="w-14 h-14 text-[#7FE620]" />
              <h3
                className={`text-2xl font-bold ${
                  isDark ? "text-white" : "text-black"
                }`}
              >
                Request received
              </h3>
              <p
                className={`text-sm max-w-sm ${
                  isDark ? "text-white/50" : "text-black/60"
                }`}
              >
                Your default mail app should have opened with a pre-filled
                request. If it didn't open, please email{" "}
                <a
                  href="mailto:support@pockethost.online"
                  className="text-[#7FE620] underline"
                >
                  support@pockethost.online
                </a>{" "}
                directly. We process all deletion requests within 30 days.
              </p>
            </motion.div>
          ) : (
            <motion.form
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: 0.1 }}
              onSubmit={handleSubmit}
              className="space-y-5"
            >
              <h2
                className={`text-lg font-bold ${
                  isDark ? "text-white" : "text-black"
                }`}
              >
                Submit a deletion request
              </h2>

              <div>
                <label
                  className={`block text-xs font-semibold mb-1.5 ${
                    isDark ? "text-white/50" : "text-black/50"
                  }`}
                >
                  Email address associated with your account *
                </label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className={inputBase}
                />
              </div>

              <div>
                <label
                  className={`block text-xs font-semibold mb-1.5 ${
                    isDark ? "text-white/50" : "text-black/50"
                  }`}
                >
                  Reason for leaving (optional)
                </label>
                <textarea
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Tell us why, so we can improve..."
                  rows={3}
                  className={`${inputBase} resize-none`}
                />
              </div>

              {formState === "error" && (
                <p className="text-sm text-red-400">{errorMsg}</p>
              )}

              <button
                type="submit"
                disabled={formState === "submitting"}
                className="w-full flex items-center justify-center gap-2 px-6 py-3 rounded-xl bg-red-500 hover:bg-red-600 active:scale-95 transition-all text-white font-bold text-sm disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Mail className="w-4 h-4" />
                {formState === "submitting"
                  ? "Opening mail app…"
                  : "Send deletion request"}
              </button>

              <p
                className={`text-xs text-center ${
                  isDark ? "text-white/30" : "text-black/40"
                }`}
              >
                Requests are processed within 30 days as required by applicable
                data protection law. You can also email{" "}
                <a
                  href="mailto:support@pockethost.online"
                  className="underline text-[#7FE620]"
                >
                  support@pockethost.online
                </a>{" "}
                directly.
              </p>
            </motion.form>
          )}
        </div>
      </section>

      <Footer />
    </div>
  );
}
