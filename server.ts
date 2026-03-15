import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { fileURLToPath } from "url";
import { createClient } from "@supabase/supabase-js";
import fs from "fs/promises";
import crypto from "crypto";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function isValidUrl(url: string) {
  try {
    new URL(url);
    return url.startsWith("http");
  } catch (e) {
    return false;
  }
}

const DB_FILE = path.join(__dirname, 'database.json');

async function readDB() {
  try {
    const data = await fs.readFile(DB_FILE, 'utf-8');
    return JSON.parse(data);
  } catch (e) {
    return [];
  }
}

async function writeDB(data: any) {
  await fs.writeFile(DB_FILE, JSON.stringify(data, null, 2));
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Supabase setup
  const rawUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || process.env.VITE_SUPABASE_URL || "";
  const rawKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.VITE_SUPABASE_ANON_KEY || "";
  
  const supabaseUrl = rawUrl.trim();
  const supabaseKey = rawKey.trim();
  
  const hasSupabase = !!(supabaseUrl && supabaseKey && isValidUrl(supabaseUrl) && !supabaseUrl.includes("YOUR_SUPABASE"));
  
  if (supabaseUrl && !isValidUrl(supabaseUrl)) {
    console.warn("Supabase URL is malformed:", supabaseUrl);
  }

  const supabase = hasSupabase ? createClient(supabaseUrl, supabaseKey) : null;

  // API Routes
  app.post("/api/waitlist", async (req, res) => {
    try {
      const { email, username, referred_by } = req.body;
      
      if (!email) {
        return res.status(400).json({ error: "Email is required" });
      }
      if (!username) {
        return res.status(400).json({ error: "Username is required" });
      }

      const referral_code = Math.random().toString(36).substring(2, 10).toUpperCase();

      if (hasSupabase && supabase) {
        // Check if email exists
        const { data: existing, error: checkError } = await supabase
          .from("waitlist")
          .select("id")
          .eq("email", email)
          .maybeSingle();

        if (checkError) {
          if (checkError.code === '42P01' || checkError.message?.includes('schema cache')) {
            return res.status(500).json({ error: "Database table 'waitlist' does not exist in Supabase. Please run the SQL script provided." });
          }
          throw checkError;
        }

        if (existing) {
          return res.status(400).json({ error: "Email already registered" });
        }

        // Insert new user
        const { data: newUser, error: insertError } = await supabase
          .from("waitlist")
          .insert([
            { email, username, referral_code, referred_by, referral_count: 0 }
          ])
          .select()
          .single();

        if (insertError) throw insertError;

        // Increment referrer count if applicable
        if (referred_by) {
          const { data: referrer } = await supabase
            .from("waitlist")
            .select("id, referral_count")
            .eq("referral_code", referred_by)
            .maybeSingle();

          if (referrer) {
            await supabase
              .from("waitlist")
              .update({ referral_count: referrer.referral_count + 1 })
              .eq("id", referrer.id);
          }
        }

        // Get position
        const { count } = await supabase
          .from("waitlist")
          .select("*", { count: "exact", head: true })
          .lt("created_at", newUser.created_at);

        const position = (count || 0) + 1;

        return res.json({ success: true, position, referral_code });
      } else {
        // JSON DB logic
        const db = await readDB();
        const existing = db.find((u: any) => u.email === email);
        
        if (existing) {
          return res.status(400).json({ error: "Email already registered" });
        }

        const newUser = {
          id: crypto.randomUUID(),
          email,
          username,
          created_at: new Date().toISOString(),
          referral_code,
          referred_by,
          referral_count: 0
        };

        if (referred_by) {
          const referrer = db.find((u: any) => u.referral_code === referred_by);
          if (referrer) {
            referrer.referral_count++;
          }
        }

        db.push(newUser);
        await writeDB(db);

        const position = db.filter((u: any) => new Date(u.created_at) < new Date(newUser.created_at)).length + 1;

        return res.json({ success: true, position, referral_code });
      }
    } catch (error: any) {
      console.error("Waitlist error details:", {
        message: error.message,
        details: error.details,
        hint: error.hint,
        code: error.code,
        error
      });

      if (error?.code === '42P01' || error?.message?.includes('schema cache')) {
        return res.status(500).json({ error: "Database table 'waitlist' does not exist in Supabase. Please run the SQL script provided." });
      }

      // Handle common Postgres errors
      if (error?.code === '23514') { // Check constraint violation
        return res.status(400).json({ error: "Invalid data format. Please check your email and username." });
      }

      const errorMessage = error.message || error.details || "Internal server error";
      res.status(500).json({ error: errorMessage });
    }
  });

  app.get("/api/leaderboard", async (req, res) => {
    try {
      if (hasSupabase && supabase) {
        const { data: leaderboard, error } = await supabase
          .from("waitlist")
          .select("username, referral_count, created_at")
          .order("referral_count", { ascending: false })
          .order("created_at", { ascending: true })
          .limit(100);

        if (error) {
          if (error.code === '42P01' || error.message?.includes('schema cache')) {
            return res.json({ leaderboard: [] }); // Graceful fallback if table doesn't exist yet
          }
          throw error;
        }

        return res.json({ leaderboard });
      } else {
        const db = await readDB();
        const leaderboard = [...db]
          .sort((a: any, b: any) => {
            if (b.referral_count !== a.referral_count) {
              return b.referral_count - a.referral_count;
            }
            return new Date(a.created_at).getTime() - new Date(b.created_at).getTime();
          })
          .slice(0, 100)
          .map((u: any) => ({
            username: u.username || 'Anonymous',
            referral_count: u.referral_count,
            created_at: u.created_at
          }));

        return res.json({ leaderboard });
      }
    } catch (error: any) {
      console.error("Leaderboard error:", error);
      res.status(500).json({ error: "Internal server error" });
    }
  });

  app.get("/api/admin/stats", async (req, res) => {
    const authHeader = req.headers.authorization;
    const adminPassword = process.env.ADMIN_PASSWORD || "admin123";

    if (!authHeader || authHeader !== `Bearer ${adminPassword}`) {
      return res.status(401).json({ error: "Unauthorized" });
    }

    try {
      if (hasSupabase && supabase) {
        const { data: allUsers, error } = await supabase
          .from("waitlist")
          .select("*")
          .order("created_at", { ascending: false });

        if (error) {
          if (error.code === '42P01' || error.message?.includes('schema cache')) {
            return res.status(500).json({ error: "Database table 'waitlist' does not exist in Supabase. Please run the SQL script provided." });
          }
          throw error;
        }

        const total = allUsers.length;
        const last24h = allUsers.filter(u => new Date(u.created_at) > new Date(Date.now() - 24 * 60 * 60 * 1000)).length;
        const topReferrers = [...allUsers].sort((a, b) => b.referral_count - a.referral_count).slice(0, 10);

        return res.json({ total, last24h, topReferrers, allUsers });
      } else {
        const db = await readDB();
        const allUsers = [...db].sort((a: any, b: any) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
        
        const total = allUsers.length;
        const last24h = allUsers.filter(u => new Date(u.created_at) > new Date(Date.now() - 24 * 60 * 60 * 1000)).length;
        const topReferrers = [...allUsers].sort((a, b) => b.referral_count - a.referral_count).slice(0, 10);

        return res.json({ total, last24h, topReferrers, allUsers });
      }
    } catch (error: any) {
      console.error("Admin stats error:", error);
      if (error?.code === '42P01' || error?.message?.includes('schema cache')) {
        return res.status(500).json({ error: "Database table 'waitlist' does not exist in Supabase. Please run the SQL script provided." });
      }
      res.status(500).json({ error: error.message || "Internal server error" });
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
    const distPath = path.join(__dirname, '..', 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
