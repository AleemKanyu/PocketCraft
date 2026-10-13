// Turns the built single-page app into static HTML, one file per route, so that
// search engines and AI crawlers (most of which do not run JavaScript) receive
// the real content, title, canonical URL and structured data of every page.
//
// Runs after `vite build` (browser bundle in dist/) and `vite build --ssr`
// (render function in dist-ssr/). Output:
//   dist/index.html            home page
//   dist/_pages/<route>.html   every other page, served at its clean URL by .htaccess
//   dist/404.html              served with a 404 status for unknown URLs
//   dist/sitemap.xml, dist/llms.txt, and the route rules inside dist/.htaccess
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dist = path.join(root, "dist");
const pagesDir = path.join(dist, "_pages");

const HEAD_MARKER = "<!--app-head-->";
const HTML_MARKER = "<!--app-html-->";
const HTACCESS_MARKER = "# @@PRERENDERED_ROUTES@@";

const server = await import(pathToFileURL(path.join(root, "dist-ssr", "entry-server.js")).href);
const { render, routes, renderHeadHtml, NOT_FOUND_PATH, SITE_URL, SITE_NAME } = server;

const template = await fs.readFile(path.join(dist, "index.html"), "utf8");
for (const marker of [HEAD_MARKER, HTML_MARKER]) {
  if (!template.includes(marker)) {
    throw new Error(`index.html is missing the ${marker} marker`);
  }
}

async function renderPage(routePath) {
  const { html, head } = await render(routePath);
  // Replacement functions keep "$" sequences in page text from being read as patterns.
  const page = template.replace(HEAD_MARKER, () => renderHeadHtml(head)).replace(HTML_MARKER, () => html);
  return { page, head };
}

await fs.rm(pagesDir, { recursive: true, force: true });

const rendered = [];
for (const route of routes) {
  const { page, head } = await renderPage(route.path);
  if (head.canonical !== (route.path === "/" ? `${SITE_URL}/` : `${SITE_URL}${route.path}`)) {
    throw new Error(`${route.path} declares canonical ${head.canonical}`);
  }
  rendered.push({ ...route, page, head });
}

// Written only after every route rendered, so a failed build never leaves a half-updated site.
for (const { path: routePath, page } of rendered) {
  const file = routePath === "/" ? path.join(dist, "index.html") : path.join(pagesDir, `${routePath.slice(1)}.html`);
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, page);
}

const notFound = await renderPage(NOT_FOUND_PATH);
await fs.writeFile(path.join(dist, "404.html"), notFound.page);

// --- sitemap.xml -----------------------------------------------------------
// Pages that set `noindex` on their <SEO> stay out of the sitemap and llms.txt.
const indexable = rendered.filter(({ head }) => head.robots.startsWith("index"));

const sitemap = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
  ...indexable.map(({ head, lastmod }) =>
    ["  <url>", `    <loc>${head.canonical}</loc>`, lastmod ? `    <lastmod>${lastmod}</lastmod>` : null, "  </url>"]
      .filter(Boolean)
      .join("\n"),
  ),
  "</urlset>",
  "",
].join("\n");
await fs.writeFile(path.join(dist, "sitemap.xml"), sitemap);

// --- llms.txt --------------------------------------------------------------
// A plain-text summary of the site for AI assistants: https://llmstxt.org
const isPost = (route) => route.path.startsWith("/blog/");
const link = ({ head }) => `- [${head.title.replace(/\s*\|\s*PocketHost$/, "")}](${head.canonical}): ${head.description}`;
const LEGAL_PATHS = ["/privacy", "/terms", "/delete-account"];
const mainPages = indexable.filter((route) => !isPost(route) && !LEGAL_PATHS.includes(route.path));
const legalPages = indexable.filter((route) => LEGAL_PATHS.includes(route.path));

const llms = [
  `# ${SITE_NAME}`,
  "",
  `> ${SITE_NAME} is a free Android app that turns a phone into a Minecraft server. It runs a real PaperMC Java Edition server on the device, lets Bedrock players join through Geyser crossplay, supports plugins, and gives friends a public address to connect to without port forwarding. Official website: ${SITE_URL}`,
  "",
  "Key facts:",
  "",
  `- Product: ${SITE_NAME} (also written "Pocket Host"; listed on Google Play as "Pockethost Minecraft Server"; formerly named PocketCraft)`,
  "- Category: Minecraft server hosting app for Android",
  "- Price: free",
  "- Platform: Android 8.0 or later, ARM64. No PC, VPS or root required",
  "- Server software: PaperMC for Minecraft Java Edition, with Geyser so Bedrock Edition players (Android, iOS, Windows, Xbox, PlayStation, Switch) can join",
  "- Networking: built-in relay gives a public join address, so no router port forwarding is needed",
  "- Also supports: Bukkit, Spigot and Paper plugins, world backups including Google Drive, an in-app console and player management",
  `- Get it: Google Play (${server.PLAY_STORE_URL}) or direct APK (${server.APK_URL})`,
  `- Support: ${server.SUPPORT_EMAIL}, Discord (${server.DISCORD_URL})`,
  `- Not to be confused with pockethost.io, an unrelated service for hosting PocketBase databases`,
  "",
  "## Main pages",
  "",
  ...mainPages.map(link),
  "",
  "## Blog posts",
  "",
  ...indexable.filter(isPost).map(link),
  "",
  "## Legal",
  "",
  ...legalPages.map(link),
  "",
].join("\n");
await fs.writeFile(path.join(dist, "llms.txt"), llms);

// --- .htaccess route rules --------------------------------------------------
const htaccessPath = path.join(dist, ".htaccess");
const htaccess = await fs.readFile(htaccessPath, "utf8");
if (!htaccess.includes(HTACCESS_MARKER)) {
  throw new Error(`public/.htaccess is missing the ${HTACCESS_MARKER} marker`);
}
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const rules = rendered
  .filter((route) => route.path !== "/")
  .map(({ path: routePath }) => {
    const slug = routePath.slice(1);
    return `RewriteRule ^${escapeRegex(slug)}$ _pages/${slug}.html [L]`;
  })
  .join("\n  ");
await fs.writeFile(htaccessPath, htaccess.replace(HTACCESS_MARKER, () => rules));

// The render bundle is a build intermediate; nothing at runtime needs it.
await fs.rm(path.join(root, "dist-ssr"), { recursive: true, force: true });

console.log(`Prerendered ${rendered.length} pages, 404.html, sitemap.xml and llms.txt`);
