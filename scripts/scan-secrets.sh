#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

pattern='(^[[:space:]]*[A-Z0-9_]*((API|ACCESS)?_?KEY|TOKEN|SECRET|PASSWORD|PASSWD|CLIENT_SECRET|PRIVATE_KEY|SERVICE_ROLE_KEY|PAT)[A-Z0-9_]*[[:space:]]*=[[:space:]]*.+$|eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}|-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----|Authorization:[[:space:]]*Bearer[[:space:]]+.+)'
mapfile -t tracked_files < <(git ls-files | grep -vE '^(package-lock\.json|README\.md|.*\.zip)$' || true)

if (( ${#tracked_files[@]} == 0 )); then
  echo "No tracked files to scan."
  exit 0
fi

if rg -n -e "$pattern" -- "${tracked_files[@]}"; then
  echo
  echo "Potential secret-like strings were found in tracked files. Review the matches above."
  exit 1
fi

echo "No secret-like strings found in tracked files."
