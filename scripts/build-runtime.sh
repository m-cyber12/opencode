#!/usr/bin/env bash
# scripts/build-runtime.sh
# Canonical runtime artifact builder. Runs on ubuntu-24.04-arm (GitHub hosted).
# Produces: rootfs.tar.gz + rootfs.sha256 + rootfs.manifest.json in $OUTDIR.
# All versions pinned from versions.lock.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSIONS_LOCK="$REPO_ROOT/versions.lock"
OUTDIR="${OUTDIR:-$REPO_ROOT/runtime/artifacts}"
mkdir -p "$OUTDIR"

# --- parse versions.lock (simple grep/sed; for production use a proper parser) ---
ALPINE_VERSION=$(grep -A1 '\[alpine\]' "$VERSIONS_LOCK" | grep '^version' | sed -E 's/.*= *"(.*)"/\1/')
ALPINE_SHA=$(grep -A3 '\[alpine\]' "$VERSIONS_LOCK" | grep '^sha256' | sed -E 's/.*= *"(.*)"/\1/')
OPENCODE_VERSION=$(grep -A1 '\[opencode\]' "$VERSIONS_LOCK" | grep '^version' | sed -E 's/.*= *"(.*)"/\1/')
OPENCODE_SHA=$(grep -A3 '\[opencode\]' "$VERSIONS_LOCK" | grep '^sha256' | sed -E 's/.*= *"(.*)"/\1/')
PROOT_REF=$(grep -A3 '\[proot\]' "$VERSIONS_LOCK" | grep '^source-ref' | sed -E 's/.*= *"(.*)"/\1/')

# --- fetch Alpine minirootfs ---
MINIROOTFS="alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"
MINIROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION%.*}/releases/aarch64/${MINIROOTFS}"
echo "Fetching $MINIROOTFS_URL"
curl -fsSL -o "$OUTDIR/$MINIROOTFS" "$MINIROOTFS_URL"
echo "$ALPINE_SHA  $OUTDIR/$MINIROOTFS" | sha256sum -c -

# --- fetch OpenCode binary ---
OCODE_TAR="opencode-linux-arm64-musl.tar.gz"
OCODE_URL="https://github.com/anomalyco/opencode/releases/download/v${OPENCODE_VERSION}/${OCODE_TAR}"
echo "Fetching $OCODE_URL"
curl -fsSL -o "$OUTDIR/$OCODE_TAR" "$OCODE_URL"
echo "$OPENCODE_SHA  $OUTDIR/$OCODE_TAR" | sha256sum -c -

# --- build proot from termux fork (static, aarch64) ---
# Assumes build-proot.sh has been run and placed libproot.so in $OUTDIR
if [[ ! -f "$OUTDIR/libproot.so" ]]; then
    echo "ERROR: libproot.so not found in $OUTDIR. Run scripts/build-proot.sh first."
    exit 1
fi

# --- staging directory ---
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

echo "Extracting Alpine minirootfs…"
tar -xzf "$OUTDIR/$MINIROOTFS" -C "$STAGE"

echo "Extracting OpenCode binary…"
tar -xzf "$OUTDIR/$OCODE_TAR" -C "$STAGE"
# opencode binary is at ./opencode → move to usr/local/bin
mv "$STAGE/opencode" "$STAGE/usr/local/bin/opencode"
chmod +x "$STAGE/usr/local/bin/opencode"

echo "Installing pinned packages via apk (cross-arch, no emulation)…"
APK_ROOT="$STAGE"
APK_ARCH="aarch64"
APK_REPOS="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION%.*}/main https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION%.*}/community"

# apk.static is x86_64 host binary that can install foreign arch packages.
# Download apk-tools-static from alpine if not present.
APK_STATIC="/tmp/apk.static"
if [[ ! -x "$APK_STATIC" ]]; then
    curl -fsSL -o /tmp/apk-tools-static.apk \
        "https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION%.*}/main/x86_64/apk-tools-static-3.20.2-r0.apk"
    tar -xzf /tmp/apk-tools-static.apk -C /tmp sbin/apk.static 2>/dev/null || \
        tar -xzf /tmp/apk-tools-static.apk -C /tmp ./sbin/apk.static 2>/dev/null
    mv /tmp/sbin/apk.static "$APK_STATIC"
    chmod +x "$APK_STATIC"
fi

"$APK_STATIC" \
    --root "$APK_ROOT" \
    --arch "$APK_ARCH" \
    --repositories "$APK_REPOS" \
    --keys-dir "$APK_ROOT/etc/apk/keys" \
    update

PACKAGES=(
    git
    bash
    ripgrep
    nodejs
    npm
    openssh-client
    ca-certificates
    tzdata
    coreutils
    tar
    gzip
    xz
    zstd
)

"$APK_STATIC" \
    --root "$APK_ROOT" \
    --arch "$APK_ARCH" \
    --repositories "$APK_REPOS" \
    --keys-dir "$APK_ROOT/etc/apk/keys" \
    add --initdb --no-chown "${PACKAGES[@]}"

# Resolve installed versions for manifest
declare -A INST_VER
for pkg in "${PACKAGES[@]}"; do
    ver=$("$APK_STATIC" --root "$APK_ROOT" --arch "$APK_ARCH" info -q "$pkg" 2>/dev/null | head -n1 || echo "unknown")
    INST_VER["$pkg"]="$ver"
done

# Create resolv.conf (will be overwritten by app at install time; placeholder here)
mkdir -p "$STAGE/etc"
cat > "$STAGE/etc/resolv.conf" <<'EOF'
# Placeholder; app writes real resolv.conf at extraction.
nameserver 8.8.8.8
nameserver 1.1.1.1
EOF

# Create bundle metadata
cat > "$STAGE/etc/opencode-android-release" <<EOF
LAYOUT_VERSION=1
OPENCODE_VERSION=${OPENCODE_VERSION}
ALPINE_VERSION=${ALPINE_VERSION}
EOF

# Compute file count + uncompressed size for progress reporting
FILE_COUNT=$(find "$STAGE" -type f | wc -l)
UNCOMP_BYTES=$(du -sb "$STAGE" | cut -f1)

# Create manifest
cat > "$OUTDIR/rootfs.manifest.json" <<EOF
{
  "layoutVersion": 1,
  "fileCount": $FILE_COUNT,
  "uncompressedBytes": $UNCOMP_BYTES,
  "opencodeVersion": "${OPENCODE_VERSION}",
  "alpineVersion": "${ALPINE_VERSION}",
  "packages": $(jq -n --argjson v "$(printf '%s\n' "${!INST_VER[@]}" | jq -R . | jq -s .)" '
    $v | map({key: ., value: "\(.)"}) | from_entries
  ' --arg ver "$(printf '%s\n' "${INST_VER[@]}" | jq -R . | jq -s .)")
}
EOF

# Create final tar.gz with deterministic ordering
echo "Creating deterministic rootfs.tar.gz…"
cd "$STAGE"
find . -type f -print0 | sort -z | tar --null -T - -czf "$OUTDIR/rootfs.tar.gz" --mtime='2026-01-01T00:00:00Z' --owner=0 --group=0 --numeric-owner

SHA256=$(sha256sum "$OUTDIR/rootfs.tar.gz" | cut -d' ' -f1)
echo "$SHA256  rootfs.tar.gz" > "$OUTDIR/rootfs.sha256"
echo "rootfs.tar.gz: $SHA256"
echo "Manifest written to $OUTDIR/rootfs.manifest.json"
echo "Done."