#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")" || exit 1
exec ./build_with_studio_jdk.sh apk "$@"
