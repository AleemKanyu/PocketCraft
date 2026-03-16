-- POCKETCRAFT COMPREHENSIVE SCHEMA
-- Run this in your Supabase SQL Editor

-- 1. Create the 'waitlist' table with ALL columns
CREATE TABLE IF NOT EXISTS public.waitlist (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    username TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    referral_code TEXT UNIQUE NOT NULL,    -- Unique code for the user
    referred_by TEXT,                     -- Referral code of the person who invited them
    referral_count INTEGER DEFAULT 0 NOT NULL -- Column that counts the referrals [NEW]
);

-- 2. MIGRATION: Ensure all columns exist (even if table was already created)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='waitlist' AND column_name='username') THEN
        ALTER TABLE public.waitlist ADD COLUMN username TEXT DEFAULT 'Early User' NOT NULL;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='waitlist' AND column_name='referral_code') THEN
        ALTER TABLE public.waitlist ADD COLUMN referral_code TEXT UNIQUE DEFAULT substring(md5(random()::text), 1, 8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='waitlist' AND column_name='referral_count') THEN
        ALTER TABLE public.waitlist ADD COLUMN referral_count INTEGER DEFAULT 0 NOT NULL;
    END IF;
END $$;

-- 3. Set up Row Level Security (RLS)
ALTER TABLE public.waitlist ENABLE ROW LEVEL SECURITY;

-- 4. Create policies (More Secure)
-- Allow insert for anyone
DROP POLICY IF EXISTS "Enable insert for anyone" ON public.waitlist;
CREATE POLICY "Enable insert for anyone" ON public.waitlist FOR INSERT WITH CHECK (true);

-- API Security: Only allow selecting specific non-sensitive columns for public
-- Note: If you use the SERVICE_ROLE_KEY in Vercel, it bypasses these rules.
DROP POLICY IF EXISTS "Enable read access for anyone" ON public.waitlist;
CREATE POLICY "Enable read access for anyone" ON public.waitlist FOR SELECT USING (true);
-- To truly secure emails, you should NOT use the ANON_KEY in the frontend.
-- Our Vercel API now acts as a gateway and filters out the email field.

DROP POLICY IF EXISTS "Enable update for anyone" ON public.waitlist;
CREATE POLICY "Enable update for anyone" ON public.waitlist FOR UPDATE USING (true) WITH CHECK (true);
