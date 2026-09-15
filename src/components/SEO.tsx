import { useEffect } from "react";

interface SEOProps {
  title?: string;
  description?: string;
  path?: string;
  image?: string;
  type?: string;
  schema?: Record<string, any> | Record<string, any>[];
}

const DEFAULT_TITLE = "PocketHost - Host Minecraft Java & Bedrock Servers on Android";
const DEFAULT_DESCRIPTION =
  "Host Minecraft Java & Bedrock Edition servers directly on your Android phone for free. Zero port forwarding, PaperMC plugins support, and high-performance relay networking.";
const SITE_URL = "https://pockethost.online";
const DEFAULT_IMAGE = `${SITE_URL}/app-icon-circle-hd.png`;

export function SEO({
  title = DEFAULT_TITLE,
  description = DEFAULT_DESCRIPTION,
  path = "",
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

    // 3. Canonical URL
    const canonicalUrl = `${SITE_URL}${path.startsWith("/") ? path : `/${path}`}`.replace(/\/$/, path === "" || path === "/" ? "/" : "");
    let canonicalLink = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null;
    if (!canonicalLink) {
      canonicalLink = document.createElement("link");
      canonicalLink.setAttribute("rel", "canonical");
      document.head.appendChild(canonicalLink);
    }
    canonicalLink.setAttribute("href", canonicalUrl);

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
