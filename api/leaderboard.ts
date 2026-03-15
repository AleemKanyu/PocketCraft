import type { VercelRequest, VercelResponse } from '@vercel/node';
import { createClient } from "@supabase/supabase-js";

const getEnv = (keys: string[]) => {
  for (const key of keys) {
    const val = process.env[key];
    if (val && val.trim() && !val.includes("YOUR_SUPABASE")) {
      return val.trim();
    }
  }
  return "";
};

const supabaseUrl = getEnv(["SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_URL", "VITE_SUPABASE_URL"]);
const supabaseKey = getEnv(["SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_ANON_KEY", "NEXT_PUBLIC_SUPABASE_ANON_KEY", "VITE_SUPABASE_ANON_KEY"]);

function isValidUrl(url: string) {
  try {
    if (!url) return false;
    new URL(url);
    return url.startsWith("http");
  } catch (e) {
    return false;
  }
}

const supabase = (supabaseUrl && supabaseKey && isValidUrl(supabaseUrl)) 
  ? createClient(supabaseUrl, supabaseKey) 
  : null;

const getDiagnostics = () => ({
  hasUrl: !!supabaseUrl,
  hasKey: !!supabaseKey,
  isValidUrl: isValidUrl(supabaseUrl),
  envKeysPresent: Object.keys(process.env).filter(k => k.includes("SUPABASE"))
});

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    if (supabase) {
      const { data: leaderboard, error } = await supabase
        .from("waitlist")
        .select("username, referral_count, created_at")
        .order("referral_count", { ascending: false })
        .order("created_at", { ascending: true })
        .limit(100);

      if (error) throw error;
      return res.json({ leaderboard });
    } else {
      return res.status(500).json({ 
        error: "Supabase connection not configured. Please add your credentials to Vercel environment variables.",
        diagnostics: getDiagnostics()
      });
    }
  } catch (error: any) {
    console.error("Leaderboard Error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
}
