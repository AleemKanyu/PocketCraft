#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME=/home/aleemkanyu/.jdk21 \
PATH=/home/aleemkanyu/.jdk21/bin:$PATH \
GRADLE_USER_HOME=/tmp/pocketcraft-gradle \
./gradlew --no-daemon :app:compileDebugKotlin --console=plain "$@"
