# PocketCraft - Hostinger Deployment Guide

This guide explains how to deploy PocketCraft as an APK download website on Hostinger Node.js hosting.

## Prerequisites

- Hostinger account with Node.js hosting (v18.x or v22.x)
- Git repository with this code pushed to GitHub/GitLab
- Android APK file (`pocketcraft.apk`)

## Step 1: Prepare Locally

1. Create local env file in project root:
   ```bash
   cp .env.example .env
   ```

2. Set env values in `.env`:
   ```env
   VITE_APP_URL=https://your-pocketcraft-domain.com
   VITE_APK_URL=/downloads/pocketcraft.apk
   PORT=3000
   ```

3. Put your APK file here:
   - `public/downloads/pocketcraft.apk`

4. Build locally:
   ```bash
   npm install
   npm run build
   ```

## Step 2: Configure Hostinger Node.js

In hPanel:

1. Go to **Hosting -> Manage**
2. Open **Node.js**
3. Click **Setup Node.js**
4. Select Node.js `22.x`
5. Set **Application Startup File** to `dist/server.js`
6. Set **Application Root** to `/`

## Step 3: Add Hostinger Environment Variables

In **Node.js -> Environment Variables**, add:

- `VITE_APP_URL` = `https://your-pocketcraft-domain.com`
- `VITE_APK_URL` = `/downloads/pocketcraft.apk`
- `NODE_ENV` = `production`

Important:

- Hostinger does not read your local `.env` automatically
- Add variables manually in hPanel
- Restart app after changing variables

## Step 4: Deploy

### Option A: Git Deployment (Recommended)

1. Push code:
   ```bash
   git add .
   git commit -m "Deploy APK download site"
   git push origin main
   ```

2. In hPanel:
   - Go to **Node.js -> Git Repository**
   - Connect repo and branch
   - Set build command: `npm install && npm run build`
   - Click **Deploy**

### Option B: Manual Upload (FTP/SFTP)

1. Upload project files
2. Ensure APK is included at `public/downloads/pocketcraft.apk`
3. In hPanel Node.js:
   - Install dependencies
   - Start application

## Step 5: Verify Deployment

1. Open website: `https://your-pocketcraft-domain.com`
2. Click **Download APK** button
3. Test direct APK URL:
   - `https://your-pocketcraft-domain.com/downloads/pocketcraft.apk`
4. Check logs in **Node.js -> Logs**

## Troubleshooting

### APK does not download

- Confirm file exists at `public/downloads/pocketcraft.apk`
- Confirm `VITE_APK_URL` matches your actual file path
- Open direct URL in browser to verify file is publicly reachable

### Env variables not applying

- Make sure they are added in hPanel, not only in local `.env`
- Check exact key names (case-sensitive)
- Restart Node.js app after updates

### App not starting

- Verify startup file is `dist/server.js`
- Check build command in Hostinger
- Review logs in hPanel

## Security Notes

- Do not commit `.env` to Git
- Keep production values in Hostinger environment settings
