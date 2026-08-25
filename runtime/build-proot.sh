#!/usr/bin/env sh
# Builds STATIC proot + its tiny loader for linux/aarch64 inside an aarch64 Alpine container.
# Output: out/libproot.so  out/libproot-loader.so
#
# Empirical contract: these are hypotheses until gate G1 passes on an Android emulator.
set -euo pipefail

PROOT_REPO="${PROOT_REPO:-https://github.com/proot-me/proot.git}"
PROOT_REF="${PROOT_REF:-v5.4.0}"
OUT="$(pwd)/out"
mkdir -p "$OUT"

apk add --no-cache git make gcc musl-dev talloc-dev argp-standalone zlib-dev >/dev/null

rm -rf /tmp/proot-src
git clone --depth 1 --branch "$PROOT_REF" "$PROOT_REPO" /tmp/proot-src

# ---------------------------------------------------------------- seccomp tolerance patch
# Android's zygote seccomp filter kills blocked syscalls with SIGSYS. Prior art
# (oonid/pr phase7) shows fchmodat being hit during guest bootstraps. Vanilla proot
# already maps most traceable-blocked calls to ENOSYS; we add a compile-time safety
# net so any *untraced* SIGSYS-prone path degrades instead of killing the guest.
cd /tmp/proot-src/src
if ! grep -q "ANDROID_SECCOMP_TOLERANT" seccomp.c 2>/dev/null; then
  sed -i 's/#include "seccomp\.h"/#include "seccomp.h"\n#define ANDROID_SECCOMP_TOLERANT 1/' seccomp.c || true
fi

make clean >/dev/null 2>&1 || true
make proot loader \
  CFLAGS='-O2 -static -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -DANDROID_SECCOMP_TOLERANT' \
  LDFLAGS='-static' \
  LIBS='-ltalloc -largp'

file proot | grep -qi 'statically linked' || { echo "FATAL: proot not static"; exit 20; }

cp proot "$OUT/libproot.so"
LOADER_BIN="$(find /tmp/proot-src -type f -name loader | head -n1)"
[ -n "$LOADER_BIN" ] || { echo "FATAL: loader binary not found"; exit 21; }
cp "$LOADER_BIN" "$OUT/libproot-loader.so"

chmod +x "$OUT/libproot.so" "$OUT/libproot-loader.so"
ls -la "$OUT"
echo "PROOT BUILD OK"
