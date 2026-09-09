#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/home/aleemkanyu/.jdk21}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
GRADLE_USER_HOME=/tmp/pocketcraft-gradle \
./gradlew --no-daemon :app:compileDebugKotlin --console=plain "$@"
