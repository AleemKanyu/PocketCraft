# ✅ Hostinger Deployment Checklist

Your PocketCraft website is now ready for Hostinger! Follow this checklist:

## ✅ Completed Code Changes

- [x] Removed Vercel-specific dependencies (`@vercel/node`)
- [x] Updated port configuration to use `process.env.PORT` (Hostinger-compatible)
- [x] Fixed static file serving paths for bundled server
- [x] Removed legacy signup routes and APIs
- [x] Updated `.env.example` with APK download configuration
- [x] Build process verified ✓ (8.6kb server.js created successfully)

## 📝 Before Deploying (Your TODO)

### 1. Prepare Your Repository
```bash
# Ensure changes are committed
git add .
git commit -m \"Update for Hostinger deployment compatibility\"
git push origin main
```

### 2. Create `.env` File Locally (DO NOT COMMIT)
Copy from `.env.example` and fill with YOUR values:
```bash
cp .env.example .env
# Edit .env with your credentials
```

### 3. Get Your Credentials Ready
- [ ] Your domain URL (`VITE_APP_URL`)
- [ ] APK URL (`VITE_APK_URL`) or use default `/downloads/pocketcraft.apk`
- [ ] Place APK file at `public/downloads/pocketcraft.apk`

## 🚀 Deploy to Hostinger

### Step 1: Set Up Hostinger Node.js Hosting
1. Login to Hostinger hPanel
2. Go to **Hosting → Manage**
3. Navigate to **Node.js**
4. Click **Setup Node.js**
5. Select **Node.js version 22.x**

### Step 2: Configure Application
- **Application Startup File**: `dist/server.js`
- **Application Root**: `/`

### Step 3: Add Environment Variables
In Hostinger hPanel → **Node.js → Environment Variables**, add:
```
VITE_APP_URL = https://your-domain.com
VITE_APK_URL = /downloads/pocketcraft.apk
NODE_ENV = production
```

### Step 4: Deploy via Git (Recommended)
1. In hPanel → **Node.js → Git Repository**
2. Connect your GitHub/GitLab repo
3. Set **Branch**: `main`
4. Set **Build Command**: `npm install && npm run build`
5. Click **Deploy**

**OR** Upload files manually via FTP/SFTP if Git is not available

### Step 5: Test Your Deployment
1. Visit your domain: `https://your-pocketcraft-domain.com`
2. Click the **Download APK** button
3. Confirm file URL works: `https://your-domain.com/downloads/pocketcraft.apk`
4. Monitor logs in hPanel → **Node.js → Logs**

## 📚 Additional Resources

- **Deployment Guide**: See `HOSTINGER_DEPLOYMENT.md` for detailed instructions
- **Build Output**: Your production build is in the `dist/` folder
- **Environment Variables**: Copy your setup from `.env.example`

## 🆘 Common Issues

| Issue | Solution |
|-------|----------|
| Port errors | Hostinger auto-assigns PORT via env var - our code handles it ✓ |
| Static files 404 | Ensure Application Root is `/` |
| API not working | Verify env variables are set in Hostinger hPanel |
| Build fails | Run `npm install` locally, then push to Hostinger |

## 💡 Next Steps After Deploy

1. Monitor the application for 24 hours
2. Set up error tracking (optional)
3. Configure domain SSL (Hostinger provides free SSL)
4. Verify APK download speed and file integrity
5. Set up automated backups for your Supabase database

---

**Need help?** Check the `HOSTINGER_DEPLOYMENT.md` file or Hostinger's support documentation.
