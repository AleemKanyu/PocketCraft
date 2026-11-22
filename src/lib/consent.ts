// Cookie and third-party consent for pockethost.online.
//
// The site sets no advertising or analytics cookies. What needs consent is content that
// makes the visitor's browser contact Google: the Google Fonts stylesheet and Google's
// "Preferred Sources" script (news.google.com). Neither is in index.html any more; they
// are added here, and only after the visitor allows "Google fonts and services".
//
// Strictly necessary storage needs no consent and is not listed as a choice: the theme
// (light/dark) in localStorage, this consent record, and the Firebase sign-in that the
// Remote Control page uses only when the visitor signs in.
//
// Touch window/document only inside functions: pages are prerendered in Node.

export const CONSENT_STORAGE_KEY = "ph-consent";
/** Bump when the categories change, so everyone is asked again. */
export const CONSENT_VERSION = 1;

export interface ConsentChoices {
  /** Google Fonts and Google's Preferred Sources script. */
  google: boolean;
}

export interface ConsentRecord extends ConsentChoices {
  v: number;
  /** When the choice was made (ISO), kept as proof of consent. */
  at: string;
}

const CHANGE_EVENT = "ph-consent-change";
const OPEN_EVENT = "ph-consent-open";

export function readConsent(): ConsentRecord | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(CONSENT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ConsentRecord;
    if (parsed?.v !== CONSENT_VERSION || typeof parsed.google !== "boolean") return null;
    // Ask again after a year, as the privacy policy says.
    const age = Date.now() - Date.parse(parsed.at);
    if (!Number.isFinite(age) || age > 365 * 24 * 60 * 60 * 1000) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveConsent(choices: ConsentChoices): ConsentRecord {
  const record: ConsentRecord = { ...choices, v: CONSENT_VERSION, at: new Date().toISOString() };
  try {
    window.localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(record));
  } catch {
    // Storage blocked (private mode): the choice still applies for this page view.
  }
  applyConsent(record);
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: record }));
  return record;
}

export function hasConsent(category: keyof ConsentChoices): boolean {
  return readConsent()?.[category] === true;
}

export function onConsentChange(listener: (record: ConsentRecord) => void): () => void {
  const handler = (e: Event) => listener((e as CustomEvent<ConsentRecord>).detail);
  window.addEventListener(CHANGE_EVENT, handler);
  return () => window.removeEventListener(CHANGE_EVENT, handler);
}

/** Reopens the banner with the choices panel, e.g. from "Cookie settings" in the footer. */
export function openConsentSettings(): void {
  window.dispatchEvent(new Event(OPEN_EVENT));
}

export function onConsentSettingsOpen(listener: () => void): () => void {
  window.addEventListener(OPEN_EVENT, listener);
  return () => window.removeEventListener(OPEN_EVENT, listener);
}

const GOOGLE_FONTS_HREF =
  "https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@500;600;700&family=Press+Start+2P&family=Space+Grotesk:wght@500;600;700&display=swap";
const PREFERRED_SOURCE_SRC = "https://news.google.com/swg/js/v1/publisher.js";

function addOnce(id: string, create: () => HTMLElement) {
  if (document.getElementById(id)) return;
  const el = create();
  el.id = id;
  document.head.appendChild(el);
}

/**
 * Loads what the visitor allowed. Withdrawing consent cannot unload a script that already
 * ran, so a withdrawal reloads the page (see CookieConsent) to start clean.
 */
export function applyConsent(record: ConsentRecord | null = readConsent()): void {
  if (typeof document === "undefined" || !record?.google) return;
  addOnce("ph-google-fonts", () => {
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = GOOGLE_FONTS_HREF;
    return link;
  });
  addOnce("ph-preferred-source", () => {
    const script = document.createElement("script");
    script.async = true;
    script.src = PREFERRED_SOURCE_SRC;
    return script;
  });
}
