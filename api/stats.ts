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

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  if (!supabase) {
    return res.status(500).json({ error: "Supabase not configured" });
  }

  try {
    const { count, error } = await supabase
      .from("waitlist")
      .select("*", { count: "exact", head: true });

    const { data: users, error: usersError } = await supabase
      .from("waitlist")
      .select("username")
      .order("created_at", { ascending: false })
      .limit(10);

    if (error || usersError) throw error || usersError;

    return res.json({ 
      success: true, 
      count: count || 0,
      users: users || []
    });
  } catch (error: any) {
    console.error("Stats API Error:", error);
    return res.status(500).json({ error: error.message || "Internal server error" });
  }
}
