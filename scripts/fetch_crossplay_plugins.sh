#!/usr/bin/env bash
# Fetches the official crossplay plugin builds into the crossplay asset pack, so the Play
# build ships them instead of downloading JARs at runtime (which Google Play forbids).
#
# These are upstream release artifacts, shipped under their own names:
#   Geyser, Floodgate      MIT      https://download.geysermc.org
#   ViaVersion, ViaBackwards GPL-3.0 https://modrinth.com/plugin/viaversion (and /viabackwards)
# Each file is checked against the hash its source publishes, and what was fetched is
# written to crossplay.provenance.txt next to the JARs.
#
# Usage: ./scripts/fetch_crossplay_plugins.sh   (needs curl and python3)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/crossplay_pack/src/main/assets/plugins"
UA="PocketHost-build/1.0"
mkdir -p "$OUT"
PROVENANCE="$OUT/crossplay.provenance.txt"
: > "$PROVENANCE"

fetch_geysermc() {  # project, output file name
  local project="$1" name="$2" meta sha version
  meta="$(curl -fsSL -A "$UA" "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest")"
  sha="$(printf '%s' "$meta" | python3 -c 'import sys,json; print(json.load(sys.stdin)["downloads"]["spigot"]["sha256"])')"
  version="$(printf '%s' "$meta" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["version"], "build", d["build"])')"
  curl -fsSL -A "$UA" -o "$OUT/$name" \
    "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest/downloads/spigot"
  echo "$sha  $OUT/$name" | sha256sum -c --quiet
  echo "$name: $project $version, sha256 $sha, from download.geysermc.org" >> "$PROVENANCE"
}

fetch_modrinth() {  # project, output file name
  local project="$1" name="$2" meta url sha version
  meta="$(curl -fsSL -A "$UA" "https://api.modrinth.com/v2/project/$project/version?loaders=%5B%22paper%22%5D")"
  url="$(printf '%s' "$meta" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["files"][0]["url"])')"
  sha="$(printf '%s' "$meta" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["files"][0]["hashes"]["sha512"])')"
  version="$(printf '%s' "$meta" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["version_number"])')"
  curl -fsSL -A "$UA" -o "$OUT/$name" "$url"
  echo "$sha  $OUT/$name" | sha512sum -c --quiet
  echo "$name: $project $version, sha512 ${sha:0:32}..., from modrinth.com" >> "$PROVENANCE"
}

fetch_geysermc geyser Geyser-Spigot.jar
fetch_geysermc floodgate floodgate-spigot.jar
fetch_modrinth viaversion ViaVersion.jar
fetch_modrinth viabackwards ViaBackwards.jar

ls -l "$OUT"
cat "$PROVENANCE"
