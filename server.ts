import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { Readable } from "stream";
import { fileURLToPath } from "url";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

type GithubReleaseAsset = {
  name: string;
  browser_download_url: string;
  content_type?: string;
};

type GithubRelease = {
  tag_name: string;
  name: string;
  html_url: string;
  published_at: string;
  draft?: boolean;
  prerelease?: boolean;
  assets?: GithubReleaseAsset[];
};

type ResolvedRelease = {
  tagName: string;
  releaseName: string;
  htmlUrl: string;
  publishedAt: string;
  downloadUrl: string;
};

async function startServer() {
  const app = express();
  const PORT = Number(process.env.PORT) || 3000;
  const owner = process.env.GITHUB_REPO_OWNER || "AleemKanyu";
  const repo = process.env.GITHUB_REPO_NAME || "PocketCraft";
  const releasesApiUrl = `https://api.github.com/repos/${owner}/${repo}/releases?per_page=10`;
  const legacyOverrideApkUrl = process.env.GITHUB_APK_URL || process.env.VITE_APK_URL || "";
  const forceStaticApkUrl = process.env.FORCE_STATIC_APK_URL === "true";
  const cacheTtlMs = Number(process.env.RELEASE_CACHE_TTL_MS || 60 * 1000);

  let releaseCache: { value: ResolvedRelease; expiresAt: number; etag?: string } | null = null;

  const resolveLatestRelease = async (): Promise<ResolvedRelease> => {
    const overrideApkUrl = forceStaticApkUrl ? legacyOverrideApkUrl : "";
    if (overrideApkUrl) {
      return {
        tagName: "manual",
        releaseName: "Manual APK URL",
        htmlUrl: overrideApkUrl,
        publishedAt: new Date(0).toISOString(),
        downloadUrl: overrideApkUrl,
      };
    }

    const now = Date.now();
    if (releaseCache && releaseCache.expiresAt > now) {
      return releaseCache.value;
    }

    const headers: Record<string, string> = {
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "PocketCraft-website",
    };

    if (process.env.GITHUB_TOKEN) {
      headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    }

    if (releaseCache?.etag) {
      headers["If-None-Match"] = releaseCache.etag;
    }

    const response = await fetch(releasesApiUrl, {
      method: "GET",
      headers,
      redirect: "follow",
    });

    if (response.status === 304 && releaseCache) {
      releaseCache.expiresAt = now + cacheTtlMs;
      return releaseCache.value;
    }

    if (!response.ok) {
      if (releaseCache) {
        return releaseCache.value;
      }
      throw new Error(`GitHub latest release request failed with ${response.status}`);
    }

    const releases = (await response.json()) as GithubRelease[];
    const apkPredicate = (asset: GithubReleaseAsset) => asset.name.toLowerCase().endsWith(".apk");
    const hasApkAsset = (release: GithubRelease) => Boolean(release.assets?.some(apkPredicate));

    const selectedRelease =
      releases.find((release) => !release.draft && !release.prerelease && hasApkAsset(release)) ||
      releases.find((release) => !release.draft && hasApkAsset(release)) ||
      releases.find(hasApkAsset);

    if (!selectedRelease) {
      if (legacyOverrideApkUrl) {
        return {
          tagName: "manual",
          releaseName: "Manual APK URL",
          htmlUrl: legacyOverrideApkUrl,
          publishedAt: new Date(0).toISOString(),
          downloadUrl: legacyOverrideApkUrl,
        };
      }

      throw new Error("No APK asset found in recent GitHub releases.");
    }

    const apkAsset = selectedRelease.assets?.find(apkPredicate);
    if (!apkAsset) {
      throw new Error("No APK asset found in selected GitHub release.");
    }

    const resolved: ResolvedRelease = {
      tagName: selectedRelease.tag_name || "unknown",
      releaseName: selectedRelease.name || selectedRelease.tag_name || "Latest",
      htmlUrl: selectedRelease.html_url || `https://github.com/${owner}/${repo}/releases/latest`,
      publishedAt: selectedRelease.published_at || new Date().toISOString(),
      downloadUrl: apkAsset.browser_download_url,
    };

    releaseCache = {
      value: resolved,
      expiresAt: now + cacheTtlMs,
      etag: response.headers.get("etag") || undefined,
    };

    return resolved;
  };

  app.use(express.json());

  // Public endpoints for uptime checks and APK metadata.
  app.get("/api/health", (_req, res) => {
    res.json({ ok: true });
  });

  app.get("/api/apk", async (_req, res) => {
    try {
      const latest = await resolveLatestRelease();
      res.json({
        ok: true,
        tagName: latest.tagName,
        releaseName: latest.releaseName,
        htmlUrl: latest.htmlUrl,
        publishedAt: latest.publishedAt,
        downloadUrl: "/api/apk/download",
      });
    } catch {
      res.status(502).json({ ok: false, error: "Unable to resolve latest GitHub release." });
    }
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
    const abortController = new AbortController();

    req.on("close", () => {
      if (!res.writableEnded) abortController.abort();
    });

    try {
      const latest = await resolveLatestRelease();
      const downloadUrl = latest.downloadUrl;

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
