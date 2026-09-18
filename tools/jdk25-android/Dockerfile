# Reproducible build environment for PocketCraft's OpenJDK 25 Android runtime.
#
#   docker build -t pocketcraft-jdk25 tools/jdk25-android
#   docker run --rm -v "$PWD/out:/out" pocketcraft-jdk25 arm64
#
# Needs ~16 GB of free space in the Docker storage driver.

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
      autoconf python3 python-is-python3 unzip zip xz-utils file cmake \
      build-essential git curl ca-certificates \
      systemtap-sdt-dev libxtst-dev libasound2-dev libelf-dev \
      libfontconfig1-dev libx11-dev libxext-dev libxrandr-dev \
      libxrender-dev libxt-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . /src/tools/jdk25-android

# Host packages are already installed above; skip the sudo apt step in build.sh.
ENV SKIP_APT=1 \
    WORK_DIR=/src/jdk25-build \
    OUT_DIR=/out

ENTRYPOINT ["/bin/bash", "-c", \
  "set -e; \
   chmod +x tools/jdk25-android/build.sh tools/jdk25-android/package.sh; \
   tools/jdk25-android/build.sh \"${1:-arm64}\"; \
   tools/jdk25-android/package.sh \"${1:-arm64}\"", "--"]
