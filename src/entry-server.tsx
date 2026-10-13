import { StrictMode } from "react";
import { prerender } from "react-dom/static";
import { StaticRouter } from "react-router-dom";
import App, { pageRoutes } from "./App";
import { blogPosts } from "./data/blogData";
import { renderHeadHtml, setServerHeadCollector, type HeadData } from "./seo/head";
import { toIsoDate } from "./seo/site";

export { renderHeadHtml };
export { SITE_NAME, SITE_TAGLINE, SITE_URL, PLAY_STORE_URL, APK_URL, DISCORD_URL, SUPPORT_EMAIL } from "./seo/site";

export interface PrerenderRoute {
  path: string;
  /** ISO date of the last meaningful content change, when it is known. */
  lastmod?: string;
}

// Every URL that is written to disk as static HTML. Anything else is answered by
// the web server with the 404 page.
export const routes: PrerenderRoute[] = pageRoutes.flatMap((route): PrerenderRoute[] => {
  if (route.path === "/blog/:id") {
    return blogPosts.map((post) => ({ path: `/blog/${post.id}`, lastmod: toIsoDate(post.date) }));
  }
  if (route.path.includes(":")) {
    throw new Error(`${route.path} has a parameter: list its concrete URLs here so they can be prerendered`);
  }
  return [{ path: route.path }];
});

export const NOT_FOUND_PATH = "/404";

export async function render(path: string): Promise<{ html: string; head: HeadData }> {
  let head: HeadData | null = null;
  setServerHeadCollector((collected) => {
    head = collected;
  });

  try {
    const { prelude } = await prerender(
      <StrictMode>
        <StaticRouter location={path}>
          <App />
        </StaticRouter>
      </StrictMode>,
      // React normally streams large Suspense boundaries as hidden chunks that a script
      // swaps in. These files are static, so every boundary is written in place instead.
      { progressiveChunkSize: Number.MAX_SAFE_INTEGER },
    );
    const html = await new Response(prelude).text();
    if (!head) {
      throw new Error(`No <SEO> component rendered for ${path}`);
    }
    return { html, head };
  } finally {
    setServerHeadCollector(null);
  }
}
