import { useEffect } from "react";

interface SEOProps {
  title?: string;
  description?: string;
  keywords?: string;
  path?: string;
  image?: string;
  type?: string;
  schema?: Record<string, any> | Record<string, any>[];
}

const DEFAULT_TITLE = "PocketHost - 100% Free Minecraft Server Hosting (Java & Bedrock on Android)";
const DEFAULT_DESCRIPTION =
  "PocketHost is 100% free Minecraft server hosting directly on your Android phone. Host 24/7 PaperMC Java & Bedrock crossplay servers with zero queues, zero port forwarding, and full plugin support.";
const DEFAULT_KEYWORDS =
  "free minecraft hosting, free minecraft server hosting, pockethost, pocket host, pocketcraft, free 24/7 minecraft server, host minecraft server android, papermc mobile hosting, geysermc crossplay, free aternos alternative";
const SITE_URL = "https://pockethost.online";
const DEFAULT_IMAGE = `${SITE_URL}/app-icon-circle-hd.png`;

export function SEO({
  title = DEFAULT_TITLE,
  description = DEFAULT_DESCRIPTION,
  keywords = DEFAULT_KEYWORDS,
  path = "/",
  image = DEFAULT_IMAGE,
  type = "website",
  schema,
}: SEOProps) {
  useEffect(() => {
    // 1. Update Title
    document.title = title;

    // Helper to update or create a meta tag
    const setMeta = (attr: "name" | "property", key: string, content: string) => {
      let el = document.querySelector(`meta[${attr}="${key}"]`) as HTMLMetaElement | null;
      if (!el) {
        el = document.createElement("meta");
        el.setAttribute(attr, key);
        document.head.appendChild(el);
      }
      el.setAttribute("content", content);
    };

    // 2. Standard Meta
    setMeta("name", "description", description);
    setMeta("name", "keywords", keywords);

    // 3. Canonical URL
    const cleanPath = path ? (path.startsWith("/") ? path : `/${path}`) : "/";
    const canonicalUrl = cleanPath === "/" ? `${SITE_URL}/` : `${SITE_URL}${cleanPath.replace(/\/+$/, "")}`;

    let canonicalLink = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null;
    if (!canonicalLink) {
      canonicalLink = document.createElement("link");
      canonicalLink.setAttribute("rel", "canonical");
      document.head.appendChild(canonicalLink);
    }
    canonicalLink.setAttribute("href", canonicalUrl);

    let alternateLink = document.querySelector('link[rel="alternate"][hreflang="x-default"]') as HTMLLinkElement | null;
    if (alternateLink) {
      alternateLink.setAttribute("href", canonicalUrl);
    }

    // 4. Open Graph
    setMeta("property", "og:title", title);
    setMeta("property", "og:description", description);
    setMeta("property", "og:url", canonicalUrl);
    setMeta("property", "og:image", image);
    setMeta("property", "og:type", type);
    setMeta("property", "og:site_name", "PocketHost");

    // 5. Twitter
    setMeta("name", "twitter:title", title);
    setMeta("name", "twitter:description", description);
    setMeta("name", "twitter:image", image);
    setMeta("name", "twitter:card", "summary_large_image");

    // 6. JSON-LD Structured Data
    const scriptId = "page-structured-data";
    let scriptTag = document.getElementById(scriptId) as HTMLScriptElement | null;
    if (schema) {
      if (!scriptTag) {
        scriptTag = document.createElement("script");
        scriptTag.id = scriptId;
        scriptTag.type = "application/ld+json";
        document.head.appendChild(scriptTag);
      }
      scriptTag.textContent = JSON.stringify(schema);
    } else if (scriptTag) {
      scriptTag.remove();
    }

    return () => {
      if (scriptTag && document.head.contains(scriptTag)) {
        scriptTag.remove();
      }
    };
  }, [title, description, path, image, type, schema]);

  return null;
}
