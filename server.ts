import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { fileURLToPath } from "url";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = process.env.PORT || 3000;
  const apkUrl = process.env.VITE_APK_URL || "/downloads/pocketcraft.apk";

  app.use(express.json());

  // Public endpoints for uptime checks and APK metadata.
  app.get("/api/health", (_req, res) => {
    res.json({ ok: true });
  });

  app.get("/api/apk", (_req, res) => {
    res.json({ downloadUrl: apkUrl });
  });

  // Redirect to GitHub release APK download
  app.get("/api/apk/download", (_req, res) => {
    const githubReleaseUrl = "https://github.com/AleemKanyu/PocketCraft/releases/download/v.0.0.1-Beta/pocketcraft.apk";
    res.redirect(githubReleaseUrl);
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = __dirname.endsWith(`${path.sep}dist`)
      ? __dirname
      : path.join(__dirname, "dist");
    app.use(express.static(distPath));
    
    // Static routes for legal pages
    app.get('/privacy', (req, res) => {
      res.sendFile(path.join(distPath, 'privacy.html'));
    });
    app.get('/terms', (req, res) => {
      res.sendFile(path.join(distPath, 'terms.html'));
    });
    
    // SPA fallback
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
