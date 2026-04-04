#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ZIP_NAME="pocketcraft-deploy.zip"

cd "$PROJECT_ROOT"

echo "Building production assets..."
npm run build

echo "Creating $ZIP_NAME in Hostinger upload format..."
rm -f "$ZIP_NAME"
zip -r "$ZIP_NAME" package.json package-lock.json index.html vite.config.ts src server.ts public dist > /dev/null

echo "Done: $PROJECT_ROOT/$ZIP_NAME"
echo "Package includes: package.json, package-lock.json, index.html, vite.config.ts, src/, server.ts, public/, dist/"