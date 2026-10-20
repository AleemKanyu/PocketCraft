import release from "../../public/update.json";

export const SITE_URL = "https://pockethost.online";
export const SITE_NAME = "PocketHost";
export const SITE_TAGLINE = "Free Minecraft server hosting on your Android phone";
export const DEFAULT_OG_IMAGE = `${SITE_URL}/og-image.png`;
export const LOGO_URL = `${SITE_URL}/app-icon-circle-hd.png`;
export const SUPPORT_EMAIL = "support@pockethost.online";

export const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.pockethost.app";
export const APK_URL = "https://github.com/AleemKanyu/PocketCraft/releases/latest/download/PocketHost.apk";
export const DISCORD_URL = "https://discord.com/invite/nc7ceYWVfT";
export const INSTAGRAM_URL = "https://www.instagram.com/pockethostmc";
export const GITHUB_URL = "https://github.com/AleemKanyu/PocketCraft";

export const ORGANIZATION_ID = `${SITE_URL}/#organization`;
export const WEBSITE_ID = `${SITE_URL}/#website`;
export const APP_ID = `${SITE_URL}/#app`;

export type JsonLd = Record<string, unknown>;

export const absoluteUrl = (path: string) => {
  const clean = path.startsWith("/") ? path : `/${path}`;
  return clean === "/" ? `${SITE_URL}/` : `${SITE_URL}${clean.replace(/\/+$/, "")}`;
};

// Present on every page so search engines and AI assistants tie each URL back to
// the same brand entity, and can tell it apart from other products named PocketHost.
export const organizationSchema: JsonLd = {
  "@type": "Organization",
  "@id": ORGANIZATION_ID,
  name: SITE_NAME,
  alternateName: ["Pocket Host", "PocketHost Minecraft Server", "PocketCraft"],
  url: `${SITE_URL}/`,
  logo: {
    "@type": "ImageObject",
    url: LOGO_URL,
    width: 512,
    height: 512,
  },
  description:
    "PocketHost makes a free Android app that runs a Minecraft Java Edition server, with Bedrock crossplay, directly on a phone.",
  email: SUPPORT_EMAIL,
  sameAs: [DISCORD_URL, INSTAGRAM_URL, GITHUB_URL],
};

export const websiteSchema: JsonLd = {
  "@type": "WebSite",
  "@id": WEBSITE_ID,
  url: `${SITE_URL}/`,
  name: SITE_NAME,
  alternateName: ["Pocket Host", "PocketHost Minecraft Server"],
  description: SITE_TAGLINE,
  inLanguage: "en",
  publisher: { "@id": ORGANIZATION_ID },
};

export const appSchema: JsonLd = {
  "@type": ["SoftwareApplication", "MobileApplication"],
  "@id": APP_ID,
  name: SITE_NAME,
  alternateName: "PocketHost Minecraft Server",
  description:
    "PocketHost is a free Android app that turns a phone into a Minecraft server. It runs PaperMC for Java Edition, lets Bedrock players join through Geyser crossplay, supports plugins, and gives friends a public address without port forwarding.",
  url: `${SITE_URL}/`,
  image: DEFAULT_OG_IMAGE,
  operatingSystem: "Android 8.0 or later (ARM64)",
  applicationCategory: "UtilitiesApplication",
  applicationSubCategory: "Minecraft server hosting",
  softwareVersion: release.version,
  installUrl: APK_URL,
  downloadUrl: APK_URL,
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
    availability: "https://schema.org/InStock",
  },
  featureList: [
    "Minecraft Java Edition server on Android with PaperMC",
    "Bedrock crossplay through Geyser",
    "Public server address without port forwarding",
    "Bukkit, Spigot and Paper plugin support",
    "World backups, including Google Drive",
    "No PC, VPS or root required",
  ],
  author: { "@id": ORGANIZATION_ID },
  publisher: { "@id": ORGANIZATION_ID },
};

export const breadcrumbSchema = (crumbs: { name: string; path: string }[]): JsonLd => ({
  "@type": "BreadcrumbList",
  itemListElement: [{ name: "Home", path: "/" }, ...crumbs].map((crumb, index) => ({
    "@type": "ListItem",
    position: index + 1,
    name: crumb.name,
    item: absoluteUrl(crumb.path),
  })),
});

export const faqSchema = (faqs: { question: string; answer: string }[]): JsonLd => ({
  "@type": "FAQPage",
  mainEntity: faqs.map((faq) => ({
    "@type": "Question",
    name: faq.question,
    acceptedAnswer: {
      "@type": "Answer",
      text: faq.answer,
    },
  })),
});

// "September 21, 2026" -> "2026-09-21". Parsed by hand so the result never shifts with the build machine's timezone.
const MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
export const toIsoDate = (humanDate: string): string | undefined => {
  const match = humanDate.trim().match(/^([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})$/);
  if (!match) return undefined;
  const month = MONTHS.indexOf(match[1].toLowerCase());
  if (month < 0) return undefined;
  return `${match[3]}-${String(month + 1).padStart(2, "0")}-${match[2].padStart(2, "0")}`;
};
