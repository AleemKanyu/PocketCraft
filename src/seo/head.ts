import {
  DEFAULT_OG_IMAGE,
  SITE_NAME,
  SITE_TAGLINE,
  absoluteUrl,
  organizationSchema,
  websiteSchema,
  type JsonLd,
} from "./site";

export interface SEOProps {
  title?: string;
  description?: string;
  path?: string;
  image?: string;
  imageAlt?: string;
  type?: "website" | "article";
  schema?: JsonLd | JsonLd[];
  noindex?: boolean;
  publishedTime?: string;
  modifiedTime?: string;
}

export interface HeadData {
  title: string;
  description: string;
  canonical: string;
  robots: string;
  image: string;
  imageAlt: string;
  type: string;
  publishedTime?: string;
  modifiedTime?: string;
  jsonLd: JsonLd;
}

const DEFAULT_TITLE = `${SITE_NAME}: Free Minecraft Server Hosting on Android`;
const DEFAULT_DESCRIPTION =
  "PocketHost is a free Android app that turns your phone into a Minecraft server. Java and Bedrock crossplay, plugins, no port forwarding and no PC needed.";
const INDEX_ROBOTS = "index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1";
const NOINDEX_ROBOTS = "noindex, follow";
const OG_IMAGE_WIDTH = "1200";
const OG_IMAGE_HEIGHT = "630";
export const STRUCTURED_DATA_ID = "page-structured-data";

export function buildHead({
  title = DEFAULT_TITLE,
  description = DEFAULT_DESCRIPTION,
  path = "/",
  image = DEFAULT_OG_IMAGE,
  imageAlt = `${SITE_NAME}: ${SITE_TAGLINE}`,
  type = "website",
  schema,
  noindex = false,
  publishedTime,
  modifiedTime,
}: SEOProps): HeadData {
  const pageSchemas = (schema ? (Array.isArray(schema) ? schema : [schema]) : []).map(
    ({ "@context": _context, ...node }) => node,
  );

  return {
    title,
    description,
    canonical: absoluteUrl(path),
    robots: noindex ? NOINDEX_ROBOTS : INDEX_ROBOTS,
    image,
    imageAlt,
    type,
    publishedTime,
    modifiedTime,
    jsonLd: {
      "@context": "https://schema.org",
      "@graph": [organizationSchema, websiteSchema, ...pageSchemas],
    },
  };
}

type MetaTag = { attr: "name" | "property"; key: string; content: string };

const metaTags = (head: HeadData): MetaTag[] => {
  const tags: MetaTag[] = [
    { attr: "name", key: "description", content: head.description },
    { attr: "name", key: "robots", content: head.robots },
    { attr: "property", key: "og:site_name", content: SITE_NAME },
    { attr: "property", key: "og:locale", content: "en_US" },
    { attr: "property", key: "og:type", content: head.type },
    { attr: "property", key: "og:title", content: head.title },
    { attr: "property", key: "og:description", content: head.description },
    { attr: "property", key: "og:url", content: head.canonical },
    { attr: "property", key: "og:image", content: head.image },
    { attr: "property", key: "og:image:alt", content: head.imageAlt },
    { attr: "name", key: "twitter:card", content: "summary_large_image" },
    { attr: "name", key: "twitter:title", content: head.title },
    { attr: "name", key: "twitter:description", content: head.description },
    { attr: "name", key: "twitter:image", content: head.image },
    { attr: "name", key: "twitter:image:alt", content: head.imageAlt },
  ];
  if (head.image === DEFAULT_OG_IMAGE) {
    tags.push(
      { attr: "property", key: "og:image:width", content: OG_IMAGE_WIDTH },
      { attr: "property", key: "og:image:height", content: OG_IMAGE_HEIGHT },
    );
  }
  if (head.publishedTime) {
    tags.push({ attr: "property", key: "article:published_time", content: head.publishedTime });
  }
  if (head.modifiedTime) {
    tags.push({ attr: "property", key: "article:modified_time", content: head.modifiedTime });
  }
  return tags;
};

// Tags that only some pages carry; removed on client navigation when the next page has none.
const OPTIONAL_META = ["og:image:width", "og:image:height", "article:published_time", "article:modified_time"];

const escapeHtml = (value: string) =>
  value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

// "<" is escaped so page text can never close the script element early.
const serializeJsonLd = (jsonLd: JsonLd) => JSON.stringify(jsonLd).replace(/</g, "\\u003c");

/** Head markup for one page, written into the static HTML at build time. */
export function renderHeadHtml(head: HeadData): string {
  return [
    `<title>${escapeHtml(head.title)}</title>`,
    `<link rel="canonical" href="${escapeHtml(head.canonical)}" />`,
    ...metaTags(head).map((tag) => `<meta ${tag.attr}="${tag.key}" content="${escapeHtml(tag.content)}" />`),
    `<script type="application/ld+json" id="${STRUCTURED_DATA_ID}">${serializeJsonLd(head.jsonLd)}</script>`,
  ].join("\n    ");
}

/** Keeps the document head in step with client-side navigation. */
export function applyHead(head: HeadData): void {
  document.title = head.title;

  let canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!canonical) {
    canonical = document.createElement("link");
    canonical.rel = "canonical";
    document.head.appendChild(canonical);
  }
  canonical.href = head.canonical;

  const tags = metaTags(head);
  for (const tag of tags) {
    let el = document.querySelector<HTMLMetaElement>(`meta[${tag.attr}="${tag.key}"]`);
    if (!el) {
      el = document.createElement("meta");
      el.setAttribute(tag.attr, tag.key);
      document.head.appendChild(el);
    }
    el.content = tag.content;
  }
  for (const key of OPTIONAL_META) {
    if (!tags.some((tag) => tag.key === key)) {
      document.querySelector(`meta[property="${key}"]`)?.remove();
    }
  }

  let script = document.getElementById(STRUCTURED_DATA_ID) as HTMLScriptElement | null;
  if (!script) {
    script = document.createElement("script");
    script.id = STRUCTURED_DATA_ID;
    script.type = "application/ld+json";
    document.head.appendChild(script);
  }
  script.textContent = serializeJsonLd(head.jsonLd);
}

// During the build-time render there is no document to write to, so each page's
// head is handed to whoever is prerendering instead.
let serverHeadCollector: ((head: HeadData) => void) | null = null;

export function setServerHeadCollector(collector: ((head: HeadData) => void) | null): void {
  serverHeadCollector = collector;
}

export function collectServerHead(head: HeadData): void {
  serverHeadCollector?.(head);
}
