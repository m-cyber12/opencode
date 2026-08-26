# Runtime — Embedded Linux Userspace

This document describes the on-device Linux-compatible execution environment that powers OpenCode on Android.

## Composition

| Layer | Version | Source | Notes |
|-------|---------|--------|-------|
| PRoot | termux/proot@7266fb3 | GitHub (fork) | Static aarch64, musl, Android-hardened |
| Alpine minirootfs | 3.24.1 | dl-cdn.alpinelinux.org | musl libc, busybox, apk-tools |
| Packages (baked in) | pinned at build | Alpine v3.24 repos | git, bash, ripgrep, nodejs, npm, openssh-client, ca-certificates, tzdata, coreutils, tar, gzip, xz, zstd |
| OpenCode | 1.18.23 | anomalyco/opencode releases | Single musl static binary (`opencode-linux-arm64-musl`) |

Total extracted size: ~350 MB. Compressed bundle: ~90 MB.

## Build Process (scripts/build-runtime.sh)

Runs on **ubuntu-24.04-arm** (GitHub hosted arm64 runner).

```mermaid
graph TD
  A[Fetch Alpine minirootfs] --> B[Extract to staging]
  B --> C[Fetch OpenCode binary]
  C --> D[Move to usr/local/bin/opencode]
  D --> E[apk.static --arch aarch64 --root install packages]
  E --> F[Write resolv.conf placeholder]
  F --> G[Write opencode-android-release marker]
  G --> H[Deterministic tar.gz (mtime=epoch, uid=0)]
  H --> I[SHA-256 + manifest.json]
```

**Why apk.static cross-arch?**  
`apk-tools-static` is an x86_64 host binary that can install foreign-architecture packages into a `--root` directory **without emulation**. This is the same technique Alpine's own `mkimage.sh` uses. No QEMU/binfmt required.

## Verification

Every artifact is pinned in `versions.lock`:
- Alpine minirootfs SHA-256
- OpenCode binary SHA-256
- PRoot source ref + fallback asset SHA-256

At **build time** (Gradle `verifyRuntimeAsset`):
- `rootfs.tar.gz` SHA-256 matches `rootfs.sha256` sidecar
- File size > 1 MB (sanity)

At **runtime** (`RuntimeInstaller.installIfNeeded`):
- Re-computes SHA-256 of asset stream
- Compares to `.installed-sha256` marker
- Mismatch → wipe + re-extract

## PRoot Invocation

```bash
libproot.so \
  --kill-on-exit --link2symlink -0 \
  -r <rootfs> -w <cwd> \
  -b /dev -b /proc -b /sys \
  -b <host_home>:/root \
  -b <host_projects>:/projects \
  /usr/local/bin/opencode serve --hostname 127.0.0.1 --port <N>
```

- `-0` : fake root (uid 0 inside guest)
- `--link2symlink` : required for apk hardlinks on F2FS/sdcard
- `--kill-on-exit` : reaps all tracees on PRoot exit
- Binds: `/dev`, `/proc`, `/sys` from host; home + projects from app storage
- **Seccomp fallback**: if tracee crashes with `IS_IN_SYSENTER`/`SIGSYS` signature, `RuntimeManager` auto-retries once with `PROOT_NO_SECCOMP=1` (termux/proot fix for 4.x kernels).

## OpenCode Provisioning

OpenCode is configured **entirely via environment variables** at spawn — no config files written to disk by the app:

| Env Var | Source | Purpose |
|---------|--------|---------|
| `OPENCODE_AUTH_CONTENT` | `SecureCredentials.snapshot()` → JSON | Provider API keys (`{"anthropic":{"type":"api","key":"..."}}`) |
| `OPENCODE_CONFIG_CONTENT` | `Settings.toConfigContentJson()` | Model, permissions, share=disabled, autoupdate=false |
| `OPENCODE_DISABLE_AUTOUPDATE=1` | hardcoded | Disable self-update |
| `OPENCODE_LOG_LEVEL` | settings | DEBUG/INFO |
| `PROOT_TMP_DIR` | app cache dir | PRoot loader temp |
| `PROOT_NO_SECCOMP=1` | conditional | Kernel compat fallback |

The guest OpenCode reads these on startup; `auth.json` is written by the server only when OAuth refresh tokens are received (persisted in bound `/root/.local/share/opencode/`).

## DNS

Alpine minirootfs has **no `/etc/resolv.conf`**. The app generates one at extraction time:

```
nameserver 8.8.8.8
nameserver 8.8.4.4
nameserver 1.1.1.1
options timeout:2 attempts:3
```

This is the same approach used by proot-distro.

## Filesystem Layout (Guest View)

```
/                           ← rootfs (read-only after extract, but writable directories exist)
├── bin/                    ← busybox applets
├── sbin/
├── usr/
│   ├── bin/
│   │   ├── bash, git, rg, node, npm, ssh
│   │   └── opencode        ← our binary
│   └── local/bin/opencode  ← symlink to above
├── etc/
│   ├── resolv.conf         ← generated
│   ├── apk/                ← package DB
│   └── opencode-android-release
├── root/                   ← bind mount → host filesDir/home/
│   └── .local/share/opencode/  ← OpenCode data (auth.db, sessions, logs)
├── projects/               ← bind mount → host filesDir/projects/
│   └── <project-id>/       ← per-project workspace
├── tmp/                    ← writable
└── dev/proc/sys/           ← bind mounts from host
```

## Upgrading

1. Bump versions in `versions.lock`
2. CI `runtime.yml` rebuilds artifacts
3. New APK includes new `rootfs.tar.gz` + sha256
4. On first launch after update: `RuntimeInstaller` detects hash mismatch → re-extracts
5. User data (`filesDir/home/`, `filesDir/projects/`) **preserved** via bind mounts outside rootfs

## ABI Support

- **v1**: arm64-v8a only (see `abi` in `versions.lock`)
- x86_64, armeabi-v7a: documented unsupported; app detects at runtime and shows friendly error.
- 16 KB page size: NDK r27c + modern musl produce compatible binaries; verified on Android 15+.

## Known Limitations

- **No GPU/OpenCL** inside guest (software rendering only).
- **No FUSE** → no sshfs, no FUSE-based tools.
- **No systemd** → services must be launched manually (OpenCode handles its own).
- **SELinux on guest** not enforced (PRoot does not virtualize SELinux).
- **No hardware acceleration** for crypto (musl uses software).