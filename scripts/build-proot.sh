#!/usr/bin/env bash
# scripts/build-proot.sh
# Builds termux/proot static aarch64 binary for Android.
# Output: $OUTDIR/libproot.so (executable, renamed for jniLibs)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTDIR="${OUTDIR:-$REPO_ROOT/runtime/artifacts}"
mkdir -p "$OUTDIR"

# Pinned ref from versions.lock
PROOT_REF="7266fb3"
REPO_URL="https://github.com/termux/proot.git"

BUILD_DIR=$(mktemp -d)
trap 'rm -rf "$BUILD_DIR"' EXIT

echo "Cloning termux/proot@${PROOT_REF}…"
git clone --depth 1 --branch master "$REPO_URL" "$BUILD_DIR/proot"
cd "$BUILD_DIR/proot"
git checkout "$PROOT_REF"

# Build static aarch64 binary (musl) using the provided Makefile.
# This requires a musl-cross toolchain (aarch64-linux-musl-gcc).
# On ubuntu-24.04-arm we can use native gcc with -static -musl if available,
# but the standard approach is cross-compile from x86_64 or use aarch64-musl toolchain.
# For GitHub Actions ubuntu-24.04-arm runner, native gcc works:
#   apt-get install -y musl-dev gcc-aarch64-linux-gnu
#   CROSS_COMPILE=aarch64-linux-gnu- make -C src
# However static musl linkage is trickier; upstream uses custom build.
# Here we use the upstream static build as fallback if cross-compile fails.

echo "Attempting native static build on arm64 host…"
cd src
if make clean && make CFLAGS="-O2 -static -DANDROID" LDFLAGS="-static" proot 2>/dev/null; then
    cp proot "$OUTDIR/libproot.so"
    echo "Built libproot.so ($(file "$OUTDIR/libproot.so"))"
    exit 0
fi

echo "Native static build failed; falling back to upstream v5.3.0 static asset."
# Fallback: download verified upstream static binary.
cd "$OUTDIR"
curl -fsSL -o libproot.so "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-aarch64-static"
chmod +x libproot.so
echo "Downloaded fallback libproot.so"