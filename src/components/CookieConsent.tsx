import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Cookie, ChevronDown, Lock } from "lucide-react";
import { useTheme } from "../lib/ThemeContext";
import {
  applyConsent,
  onConsentSettingsOpen,
  readConsent,
  saveConsent,
  type ConsentChoices,
} from "../lib/consent";

/**
 * Cookie banner. Shown until the visitor picks Accept all, Reject all or saves custom
 * choices; "Cookie settings" in the footer brings it back. Accept and Reject look the
 * same and sit side by side, so neither is the easy way out.
 *
 * Rendered only after mount: the prerendered HTML never contains it, so hydration matches
 * and visitors who already chose never see it flash.
 */
export default function CookieConsent() {
  const { theme } = useTheme();
  const isDark = theme === "dark";
  const reduceMotion = useReducedMotion();
  const [mounted, setMounted] = useState(false);
  const [open, setOpen] = useState(false);
  const [customize, setCustomize] = useState(false);
  const [google, setGoogle] = useState(false);
  const panelId = useId();
  const titleId = useId();
  const firstButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    setMounted(true);
    const existing = readConsent();
    if (existing) {
      applyConsent(existing);
      setGoogle(existing.google);
    } else {
      setOpen(true);
    }
    return onConsentSettingsOpen(() => {
      const current = readConsent();
      setGoogle(current?.google ?? false);
      setCustomize(true);
      focusOnOpen.current = true;
      setOpen(true);
    });
  }, []);

  // Focus moves to the banner only when the visitor opened it from "Cookie settings",
  // never on page load, so keyboard and screen-reader users are not pulled away.
  const focusOnOpen = useRef(false);
  useEffect(() => {
    if (open && focusOnOpen.current) {
      focusOnOpen.current = false;
      firstButton.current?.focus({ preventScroll: true });
    }
  }, [open]);

  const choose = (choices: ConsentChoices) => {
    const before = readConsent();
    saveConsent(choices);
    setOpen(false);
    setCustomize(false);
    // A loaded script cannot be unloaded: start the page again without it.
    if (before?.google && !choices.google) window.location.reload();
  };

  if (!mounted) return null;

  const card = isDark
    ? "bg-[#141414] border-white/10 text-white shadow-[0_18px_50px_-12px_rgba(0,0,0,0.8)]"
    : "bg-white border-black/10 text-black shadow-[0_18px_50px_-12px_rgba(0,0,0,0.25)]";
  const muted = isDark ? "text-white/60" : "text-black/60";
  // Accept and Reject share one style on purpose (equal weight).
  const choiceButton = `flex-1 min-h-11 rounded-xl border-2 border-b-4 px-4 py-2.5 text-sm font-extrabold uppercase tracking-wide transition-[transform,background-color] active:translate-y-[2px] active:border-b-2 focus:outline-none focus-visible:ring-4 focus-visible:ring-[#7FE620]/40 bg-[#7FE620] border-[#58A700] text-black hover:bg-[#8cf02c]`;

  const enter = reduceMotion
    ? { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 } }
    : {
        initial: { opacity: 0, y: 40, scale: 0.96 },
        animate: { opacity: 1, y: 0, scale: 1 },
        exit: { opacity: 0, y: 24, scale: 0.98 },
      };

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          key="cookie-consent"
          role="dialog"
          aria-modal="false"
          aria-labelledby={titleId}
          {...enter}
          transition={reduceMotion ? { duration: 0.15 } : { type: "spring", stiffness: 380, damping: 32 }}
          className="fixed z-[100] bottom-4 left-4 right-4 sm:right-auto sm:w-[420px] select-text"
        >
          <div className={`rounded-2xl border-2 border-b-4 p-5 ${card}`}>
            <div className="flex items-start gap-3">
              <motion.div
                className="w-10 h-10 shrink-0 rounded-xl bg-[#7FE620]/15 flex items-center justify-center"
                animate={reduceMotion ? undefined : { rotate: [0, -12, 10, -6, 0] }}
                transition={{ duration: 0.9, delay: 0.35, ease: "easeInOut" }}
                aria-hidden="true"
              >
                <Cookie className="w-5 h-5 text-[#58A700]" />
              </motion.div>
              <div className="min-w-0">
                <h2 id={titleId} className="font-extrabold text-base leading-tight">
                  Cookies? Just the basics.
                </h2>
                <p className={`mt-1 text-[13px] leading-relaxed ${muted}`}>
                  We don't use ad or tracking cookies. With your OK we load fonts and a Google
                  sources button from Google, which sees your IP address.{" "}
                  <Link to="/privacy#cookies" className="font-bold underline underline-offset-2 text-[#58A700]">
                    Details
                  </Link>
                </p>
              </div>
            </div>

            <AnimatePresence initial={false}>
              {customize && (
                <motion.div
                  id={panelId}
                  key="panel"
                  initial={reduceMotion ? { opacity: 0 } : { height: 0, opacity: 0 }}
                  animate={reduceMotion ? { opacity: 1 } : { height: "auto", opacity: 1 }}
                  exit={reduceMotion ? { opacity: 0 } : { height: 0, opacity: 0 }}
                  transition={{ duration: reduceMotion ? 0.1 : 0.25, ease: [0.22, 1, 0.36, 1] }}
                  className="overflow-hidden"
                >
                  <div className="mt-4 space-y-2">
                    <ChoiceRow
                      isDark={isDark}
                      title="Necessary"
                      detail="Your light/dark choice, this cookie choice, and sign-in on the Remote Control page. Always on."
                      locked
                    />
                    <ChoiceRow
                      isDark={isDark}
                      title="Google fonts and services"
                      detail="Fonts from Google Fonts and the Google Preferred Sources button (news.google.com)."
                      checked={google}
                      onChange={setGoogle}
                    />
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="mt-4 flex gap-2">
              <button ref={firstButton} type="button" className={choiceButton} onClick={() => choose({ google: false })}>
                Reject all
              </button>
              <button type="button" className={choiceButton} onClick={() => choose({ google: true })}>
                Accept all
              </button>
            </div>
            <div className="mt-2 flex justify-center">
              {customize ? (
                <button
                  type="button"
                  onClick={() => choose({ google })}
                  className={`min-h-10 px-3 text-sm font-bold underline underline-offset-4 ${isDark ? "text-white/80" : "text-black/75"} focus:outline-none focus-visible:ring-4 focus-visible:ring-[#7FE620]/40 rounded-lg`}
                >
                  Save my choices
                </button>
              ) : (
                <button
                  type="button"
                  aria-expanded={customize}
                  aria-controls={panelId}
                  onClick={() => setCustomize(true)}
                  className={`min-h-10 px-3 inline-flex items-center gap-1 text-sm font-bold ${isDark ? "text-white/80" : "text-black/75"} focus:outline-none focus-visible:ring-4 focus-visible:ring-[#7FE620]/40 rounded-lg`}
                >
                  Customize <ChevronDown className="w-4 h-4" aria-hidden="true" />
                </button>
              )}
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function ChoiceRow({
  isDark,
  title,
  detail,
  checked = true,
  locked = false,
  onChange,
}: {
  isDark: boolean;
  title: string;
  detail: string;
  checked?: boolean;
  locked?: boolean;
  onChange?: (value: boolean) => void;
}) {
  const id = useId();
  return (
    <div
      className={`flex items-start gap-3 rounded-xl border-2 p-3 ${
        isDark ? "border-white/10 bg-white/[0.03]" : "border-black/10 bg-black/[0.02]"
      }`}
    >
      <div className="min-w-0 flex-1">
        <label htmlFor={id} className="font-bold text-sm flex items-center gap-1.5">
          {title}
          {locked && <Lock className="w-3.5 h-3.5 opacity-60" aria-hidden="true" />}
        </label>
        <p className={`text-xs leading-relaxed mt-0.5 ${isDark ? "text-white/55" : "text-black/55"}`}>{detail}</p>
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-disabled={locked || undefined}
        disabled={locked}
        onClick={() => onChange?.(!checked)}
        className={`relative mt-0.5 h-7 w-12 shrink-0 rounded-full border-2 transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-[#7FE620]/40 ${
          checked ? "bg-[#7FE620] border-[#58A700]" : isDark ? "bg-white/10 border-white/20" : "bg-black/10 border-black/15"
        } ${locked ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}`}
      >
        <span
          className={`absolute left-0 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform duration-200 motion-reduce:transition-none ${
            checked ? "translate-x-[22px]" : "translate-x-0.5"
          }`}
        />
      </button>
    </div>
  );
}
