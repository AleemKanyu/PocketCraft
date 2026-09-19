# PocketHost Development & Deployment Guidelines

## Repository Structure & Context

1. **Public Website Repository (`PocketCraft`)**:
   - **Local path:** `/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft_website`
   - **Remote:** `https://github.com/AleemKanyu/PocketCraft.git` (branch: `main`)
   - **Contents:** Official website (`pockethost.online`), landing pages, documentation, web dashboard, and deployment workflows.

2. **Private Android App Repository (`PocketCraft_`)**:
   - **Local path:** `/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft`
   - **Remote:** `https://github.com/AleemKanyu/PocketCraft_.git`
   - **Contents:** Android application source code (Kotlin, Jetpack Compose, PaperMC engine, relay client).

---

## Hostinger Deployment Workflow

- **Automated FTP Deployment**:
  - The public repository has a GitHub Actions workflow configured in `.github/workflows/deploy.yml` (*Deploy PocketHost Website to Hostinger*).
  - Pushing commits to branch `main` on `https://github.com/AleemKanyu/PocketCraft.git` automatically triggers this workflow.
  - The workflow runs `npm ci`, builds production assets via `npm run build`, and uploads the compiled `dist/` directory directly to Hostinger using FTP (`SamKirkland/FTP-Deploy-Action`).
- **To Deploy Website Changes to Hostinger**:
  1. Make website changes in `PocketCraft_website`.
  2. Verify build: `npm run build`.
  3. Commit and push to `origin main`:
     ```bash
     git add .
     git commit -m "..."
     git push origin main
     ```
  4. GitHub Actions will automatically handle the FTP upload to Hostinger in ~20–40 seconds.
