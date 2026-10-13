# PocketHost Development & Deployment Guidelines

## Repository Structure & Context

1. **Public Website Repository (`PocketCraft`)**:
   - **Local path:** `/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft_website`
   - **Remote:** `https://github.com/AleemKanyu/PocketCraft.git` (branch: `main`)
   - **Contents:** Official website (`pockethost.online`), landing pages, documentation, web dashboard, and deployment workflows.

2. **Private Android App Repository (`PocketCraft_`)**:
   - **Local path:** `/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft`
   - **Remote:** `https://github.com/AleemKanyu/PocketCraft_.git`
   - **Contents:** Android application source code (Kotlin, Jetpack Compose, PaperMC engine, relay client).

---

## Hostinger Deployment Workflow

- **Automated FTP Deployment**:
  - The public repository has a GitHub Actions workflow configured in `.github/workflows/deploy.yml` (*Deploy PocketHost Website to Hostinger*).
  - Pushing commits to branch `main` on `https://github.com/AleemKanyu/PocketCraft.git` automatically triggers this workflow.
  - The workflow runs `npm ci`, builds production assets via `npm run build`, and uploads the compiled `dist/` directory directly to Hostinger using FTP (`SamKirkland/FTP-Deploy-Action`).
- **To Deploy Website Changes to Hostinger**:
  1. Make website changes in `PocketCraft_website`.
  2. Verify build: `npm run build`.
  3. Commit and push to `origin main`:
     ```bash
     git add .
     git commit -m "..."
     git push origin main
     ```
  4. GitHub Actions will automatically handle the FTP upload to Hostinger in ~20–40 seconds.

---

## SEO & Prerendering

Every page is written to disk as static HTML at build time, so search engines and AI crawlers (which mostly do not run JavaScript) get the real content.

- `npm run build` runs three steps: the browser bundle, a render bundle (`vite build --ssr src/entry-server.tsx`), then `scripts/prerender.mjs`, which writes `dist/index.html`, `dist/_pages/<route>.html`, `dist/404.html`, `dist/sitemap.xml`, `dist/llms.txt` and the per-page rewrite rules inside `dist/.htaccess`.
- **Adding a page:** add it to `pageRoutes` in `src/App.tsx` and give it an `<SEO title description path />`. It is then prerendered and added to the sitemap automatically. Blog posts in `src/data/blogData.ts` are picked up the same way. A URL that is not in `pageRoutes` returns a real 404 on the live site.
- **Head tags:** title, description, canonical, Open Graph and JSON-LD all come from the page's `<SEO>` component (`src/seo/head.ts`). Do not add per-page tags to `index.html`. Sitewide schema lives in `src/seo/site.ts`.
- **Render safety:** page components run in Node during the build. Touch `window`/`document` only inside `useEffect` or event handlers.
- **Structured data must be true.** Do not add ratings, review counts or other numbers that are not shown on the page and backed by a real source.
- `public/sitemap.xml` no longer exists: the sitemap is generated. `public/.htaccess` keeps a `# @@PRERENDERED_ROUTES@@` marker that the build replaces.
- After a deploy the workflow pings IndexNow (`npm run indexnow`) so Bing recrawls the site.
