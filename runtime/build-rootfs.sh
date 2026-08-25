#!/bin/sh
# Assembles the embedded OpenCode rootfs (aarch64) INSIDE an aarch64 Alpine container.
# Runs under qemu-emulated arm64 on a Linux CI runner.
#
# Output: out/opencode-rootfs.tar.gz   out/versions.lock   out/gates-manifest.txt
#
# EVERYTHING produced here is a hypothesis until gates G1..G8 PASS on the emulator.
set -euo pipefail

ROOT="$(pwd)"
OUT="$ROOT/out"
mkdir -p "$OUT"
. "$ROOT/versions.lock"

echo "== [1/6] guest toolchain for assembly"
apk add --no-cache bash curl unzip tar gzip ca-certificates git >/dev/null

echo "== [2/6] create rootfs via apk --root (same arch, no chroot needed yet)"
ROOTFS="$ROOT/rootfs"
rm -rf "$ROOTFS"
mkdir -p "$ROOTFS/etc/apk" "$ROOTFS/home/opencode/project" "$ROOTFS/tmp" "$ROOTFS/dev" \
         "$ROOTFS/proc" "$ROOTFS/sys"

# Repos pinned to $ALPINE_BRANCH
mkdir -p "$ROOTFS/etc/apk/repositories.d"
cat > "$ROOTFS/etc/apk/repositories" <<EOF
$ALPINE_MIRROR/$ALPINE_BRANCH/main
$ALPINE_MIRROR/$ALPINE_BRANCH/community
EOF

apk add --no-cache --initdb --root "$ROOTFS" --arch aarch64 \
    alpine-baselayout alpine-keys apk-tools busybox musl-utils tzdata $ALPINE_PACKAGES >/dev/null

# resolv.conf: Android provides no /etc/resolv.conf to guests; WE own this file.
cp /etc/resolv.conf "$ROOTFS/etc/resolv.conf" 2>/dev/null || \
  printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOTFS/etc/resolv.conf"

echo "== [3/6] install Bun (STATIC musl aarch64) into guest"
BUN_URL="https://github.com/oven-sh/bun/releases/download/bun-v${BUN_VERSION}/${BUN_ASSET}"
curl -fsSL -o /tmp/bun.zip "$BUN_URL"
mkdir -p /tmp/bunx && cd /tmp/bunx && unzip -q ../bun.zip
install -m0755 "bun-linux-arm64-musl/bun" "$ROOTFS/usr/local/bin/bun"
ln -sf /usr/local/bin/bun "$ROOTFS/usr/local/bin/bunx"

echo "== [4/6] install OpenCode INTO the rootfs (chroot, same arch)"
mount -t proc proc "$ROOTFS/proc" 2>/dev/null || true
chroot "$ROOTFS" /usr/local/bin/bun --version
chroot "$ROOTFS" /bin/sh -ec '
  export HOME=/home/opencode BUN_INSTALL=/usr/local PATH=/usr/local/bin:$PATH
  bun install -g '"${OPENCODE_PACKAGE}@${OPENCODE_VERSION}"'
  ln -sf "$(bun pm -g bin)/opencode" /usr/local/bin/opencode || true
'
umount "$ROOTFS/proc" 2>/dev/null || true

# opencode launcher sanity inside guest
printf '#!/bin/sh\nexport HOME=/home/opencode\nexport PATH=/usr/local/bin:$PATH\nexec /usr/local/bin/opencode "$@"\n' \
  > "$ROOTFS/usr/local/bin/opencode-serve"
chmod +x "$ROOTFS/usr/local/bin/opencode-serve"

echo "== [5/6] gate fixtures (G4/G5) + profile"
GATES_DIR="$ROOTFS/home/opencode/gates"
mkdir -p "$GATES_DIR"

# G4 fixture expectations are behavioral (bash/git/file), no file needed.

# G5: real MCP stdio server (JSON-RPC over stdin/stdout) run by node — child-process proof.
cat > "$GATES_DIR/mcp-server.mjs" <<'EOF'
// Minimal MCP-ish stdio server: answers initialize + tools/list JSON-RPC.
import { createInterface } from 'node:readline';
const rl = createInterface({ input: process.stdin });
rl.on('line', (line) => {
  let msg; try { msg = JSON.parse(line); } catch { return; }
  if (msg.method === 'initialize') {
    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: msg.id, result: {
      protocolVersion: msg.params?.protocolVersion ?? '2024-11-05',
      capabilities: { tools: {} }, serverInfo: { name: 'gate5-stdio', version: '0.0.1' }
    }}) + '\n');
  } else if (msg.method === 'tools/list') {
    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: msg.id, result: {
      tools: [{ name: 'ping', description: 'returns pong', inputSchema: { type: 'object', properties: {} } }]
    }}) + '\n');
  } else if (msg.method === 'tools/call') {
    process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: msg.id, result: {
      content: [{ type: 'text', text: 'pong' }]
    }}) + '\n');
  }
});
EOF

# Global opencode config registering the stdio MCP server (hypothesis under test in G5).
mkdir -p "$ROOTFS/home/opencode/.config/opencode"
cat > "$ROOTFS/home/opencode/.config/opencode/opencode.json" <<'EOF'
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "gate5": {
      "type": "local",
      "command": ["node", "/home/opencode/gates/mcp-server.mjs"],
      "enabled": true,
      "timeout": 15000
    }
  }
}
EOF

chown -R 1000:1000 "$ROOTFS/home/opencode" 2>/dev/null || true

# Guest profile used by every `sh -lc`
mkdir -p "$ROOTFS/etc/profile.d"
cat > "$ROOTFS/etc/profile.d/opencode-gates.sh" <<'EOF'
export HOME=/home/opencode
export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
export TMPDIR=/tmp
export SHELL=/bin/bash
cd /home/opencode/project
EOF

echo "== [6/6] pack (gzip so the Android host can stream-extract without zstd)"
GATE_MANIFEST="$OUT/gates-manifest.txt"
{
  echo "bun $(chroot "$ROOTFS" /usr/local/bin/bun --version)"
  echo "opencode ${OPENCODE_VERSION}"
} > "$GATE_MANIFEST"

tar --format=ustar -C "$ROOTFS" -czf "$OUT/opencode-rootfs.tar.gz" .
cp "$ROOT/versions.lock" "$OUT/versions.lock"
ls -la "$OUT"
echo "ROOTFS BUILD OK ($(du -h "$OUT/opencode-rootfs.tar.gz" | cut -f1))"
