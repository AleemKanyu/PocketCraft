// Tells IndexNow (Bing, and through Bing's index ChatGPT search and Copilot, plus
// Yandex, Naver and Seznam) which URLs to recrawl. Run after a deploy:
//   npm run indexnow
// The key is not a secret: the matching public/<key>.txt file proves the site is ours.
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HOST = "pockethost.online";
const KEY = "a13123e795bf463b6f464777e813e3b8";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sitemap = await fs.readFile(path.join(root, "dist", "sitemap.xml"), "utf8");
const urlList = [...sitemap.matchAll(/<loc>(.*?)<\/loc>/g)].map((match) => match[1]);

const response = await fetch("https://api.indexnow.org/indexnow", {
  method: "POST",
  headers: { "Content-Type": "application/json; charset=utf-8" },
  body: JSON.stringify({ host: HOST, key: KEY, keyLocation: `https://${HOST}/${KEY}.txt`, urlList }),
});

console.log(`IndexNow: submitted ${urlList.length} URLs, status ${response.status}`);
if (!response.ok) {
  process.exitCode = 1;
}
