import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { Readable } from "stream";
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
    res.json({ downloadUrl: "/api/apk/download" });
  });

  app.head("/api/apk/download", (_req, res) => {
    res.status(200);
    res.setHeader("Content-Type", "application/vnd.android.package-archive");
    res.setHeader("Content-Disposition", 'attachment; filename="PocketCraft.apk"');
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Accept-Ranges", "bytes");
    res.end();
  });

  // Stream APK from GitHub so users download from this domain without redirect.
  app.get("/api/apk/download", async (req, res) => {

    const downloadUrl = process.env.GITHUB_APK_URL || apkUrl || defaultGithubApkUrl;
    const abortController = new AbortController();

    req.on("close", () => {
      if (!res.writableEnded) abortController.abort();
    });

    try {
      const requestHeaders: Record<string, string> = {};
      if (typeof req.headers.range === "string") {
        requestHeaders.Range = req.headers.range;
      }

      const upstreamResponse = await fetch(downloadUrl, {
        method: "GET",
        headers: requestHeaders,
        redirect: "follow",
        signal: abortController.signal,
      });

      if (!upstreamResponse.ok && upstreamResponse.status !== 206) {
        res.status(502).json({ ok: false, error: "Unable to fetch APK from upstream release." });
        return;
      }

      if (!upstreamResponse.body) {
        res.status(502).json({ ok: false, error: "Upstream APK response had no body." });
        return;
      }

      const upstreamType = upstreamResponse.headers.get("content-type");
      const upstreamLength = upstreamResponse.headers.get("content-length");
      const upstreamRange = upstreamResponse.headers.get("content-range");
      const upstreamAcceptRanges = upstreamResponse.headers.get("accept-ranges");
      const upstreamLastModified = upstreamResponse.headers.get("last-modified");
      const upstreamEtag = upstreamResponse.headers.get("etag");

      res.status(upstreamResponse.status === 206 ? 206 : 200);
      res.setHeader("Content-Type", upstreamType || "application/vnd.android.package-archive");
      res.setHeader("Content-Disposition", 'attachment; filename="PocketCraft.apk"');
      res.setHeader("Cache-Control", "no-store");
      res.setHeader("Accept-Ranges", upstreamAcceptRanges || "bytes");

      if (upstreamLength) res.setHeader("Content-Length", upstreamLength);
      if (upstreamRange) res.setHeader("Content-Range", upstreamRange);
      if (upstreamLastModified) res.setHeader("Last-Modified", upstreamLastModified);
      if (upstreamEtag) res.setHeader("ETag", upstreamEtag);

      const stream = Readable.fromWeb(upstreamResponse.body as any);
      stream.on("error", () => {
        if (!res.headersSent) {
          res.status(502).json({ ok: false, error: "APK stream interrupted." });
        } else {
          res.destroy();
        }
      });

      stream.pipe(res);
    } catch {
      if (!res.headersSent) {
        res.status(502).json({ ok: false, error: "Failed to stream APK from upstream release." });
      }
    }
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
