import type { VercelRequest, VercelResponse } from '@vercel/node';
import { createClient } from "@supabase/supabase-js";
import nodemailer from "nodemailer";

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

const smtpHost = (process.env.SMTP_HOST || "").trim();
const smtpPort = Number(process.env.SMTP_PORT || "587");
const smtpSecure = (process.env.SMTP_SECURE || "false").trim().toLowerCase() === "true";
const smtpUser = (process.env.SMTP_USER || "").trim();
const smtpPass = (process.env.SMTP_PASS || "").trim();
const smtpFrom = (process.env.SMTP_FROM || "").trim();

function getBaseUrl(req: VercelRequest) {
  const envUrl = (process.env.APP_URL || process.env.NEXT_PUBLIC_APP_URL || process.env.VITE_APP_URL || "").trim();
  if (envUrl && isValidUrl(envUrl)) return envUrl.replace(/\/$/, "");

  const host = req.headers["x-forwarded-host"] || req.headers.host;
  const proto = (req.headers["x-forwarded-proto"] as string) || "https";
  if (host) return `${proto}://${host}`.replace(/\/$/, "");
  return "";
}

function canSendEmails() {
  return !!(smtpHost && smtpPort && smtpUser && smtpPass && smtpFrom);
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

async function sendReferralEmail(params: { to: string; username: string; referralLink: string }) {
  if (!canSendEmails()) {
    return { sent: false, reason: "SMTP not configured" };
  }

  const transporter = nodemailer.createTransport({
    host: smtpHost,
    port: smtpPort,
    secure: smtpSecure,
    auth: {
      user: smtpUser,
      pass: smtpPass,
    },
  });

  const safeUsername = escapeHtml(params.username);

  await transporter.sendMail({
    from: smtpFrom,
    to: params.to,
    subject: "Your PocketCraft referral link",
    text: `Hey ${params.username},\n\nYou're on the list.\n\nYour referral link:\n${params.referralLink}\n\nShare it to climb the leaderboard.\n\n- PocketCraft`,
    html: `<p>Hey ${safeUsername},</p><p>You're on the list.</p><p><strong>Your referral link:</strong><br /><a href="${params.referralLink}">${params.referralLink}</a></p><p>Share it to climb the leaderboard.</p><p>- PocketCraft</p>`,
  });

  return { sent: true };
}

const getDiagnostics = () => ({
  hasUrl: !!supabaseUrl,
  hasKey: !!supabaseKey,
  isValidUrl: isValidUrl(supabaseUrl),
  envKeysPresent: Object.keys(process.env).filter(k => k.includes("SUPABASE"))
});

export default async function handler(req: VercelRequest, res: VercelResponse) {
  // Allow GET to check status via ?email=...
  if (req.method === 'GET') {
    const { email } = req.query;
    if (!email || typeof email !== 'string') return res.status(400).json({ error: "Email is required" });
    
    if (!supabase) return res.status(500).json({ error: "Supabase not configured", diagnostics: getDiagnostics() });

    const { data: user, error } = await supabase
      .from("waitlist")
      .select("id, username, referral_code, referral_count, created_at")
      .eq("email", email)
      .maybeSingle();

    if (error) return res.status(500).json({ error: error.message });
    if (!user) return res.status(404).json({ error: "User not found" });

    // Calculate Rank (by referrals)
    const { count: rankCount } = await supabase
      .from("waitlist")
      .select("*", { count: "exact", head: true })
      .or(`referral_count.gt.${user.referral_count},and(referral_count.eq.${user.referral_count},created_at.lt.${user.created_at})`);

    const rank = (rankCount || 0) + 1;

    return res.json({ 
      success: true, 
      username: user.username,
      position: user.id ? 0 : 0, // Placeholder if needed
      referral_code: user.referral_code,
      referral_count: user.referral_count,
      rank
    });
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  try {
    const { email, username, referred_by } = req.body;
    
    if (!email || !username) {
      return res.status(400).json({ error: "Email and Username are required" });
    }

    const referral_code = Math.random().toString(36).substring(2, 10).toUpperCase();

    if (supabase) {
      // Check if email exists
      const { data: existing, error: checkError } = await supabase
        .from("waitlist")
        .select("id, username, referral_code, referral_count, created_at")
        .eq("email", email)
        .maybeSingle();

      if (checkError) throw checkError;
      
      let user = existing;

      if (!existing) {
        // Insert new user
        const { data: newUser, error: insertError } = await supabase
          .from("waitlist")
          .insert([{ email, username, referral_code, referred_by, referral_count: 0 }])
          .select()
          .single();

        if (insertError) throw insertError;
        user = newUser;

        // Increment referrer count
        if (referred_by) {
          const { data: referrer } = await supabase
            .from("waitlist")
            .select("id, referral_count")
            .eq("referral_code", referred_by)
            .maybeSingle();

          if (referrer) {
            await supabase
              .from("waitlist")
              .update({ referral_count: (referrer.referral_count || 0) + 1 })
              .eq("id", referrer.id);
          }
        }
      }

      // Calculate Rank (by referrals)
      const { count: rankCount } = await supabase
        .from("waitlist")
        .select("*", { count: "exact", head: true })
        .or(`referral_count.gt.${user.referral_count},and(referral_count.eq.${user.referral_count},created_at.lt.${user.created_at})`);

      const rank = (rankCount || 0) + 1;

      const baseUrl = getBaseUrl(req);
      const referralLink = baseUrl ? `${baseUrl}/ref/${user.referral_code}` : "";

      let email_sent = false;
      if (referralLink) {
        try {
          const result = await sendReferralEmail({
            to: email,
            username: user.username,
            referralLink,
          });
          email_sent = result.sent;
        } catch (mailError) {
          console.error("Referral email failed:", mailError);
        }
      }

      return res.json({
        success: true, 
        position: 0, // We use rank now
        referral_code: user.referral_code, 
        referral_count: user.referral_count,
        rank,
        email_sent,
      });
    } else {
      return res.status(500).json({ 
        error: "Supabase connection not configured. Please add your credentials to Vercel environment variables.",
        diagnostics: getDiagnostics()
      });
    }
  } catch (error: any) {
    console.error("Vercel Function Error:", error);
    res.status(500).json({ error: error.message || "Internal server error" });
  }
}
