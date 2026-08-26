# OpenCode for Android — Embedded Local Agent Runtime

![Status](https://img.shields.io/badge/status-alpha-orange)
![Architecture](https://img.shields.io/badge/arch-arm64--v8a-blue)
![Target](https://img.shields.io/badge/targetSdk-28-lightgrey)

A native Android application that provides the **full OpenCode agent experience locally on the Android device**, inside ONE APK, without requiring Termux, a PC, a remote server, a cloud gateway, or any separately installed runtime.

> **Install APK → Open app → choose/create project → chat with OpenCode.**

Under the UI, however, this is **REAL OpenCode** (v1.18.x) running locally on the Android device.

---

## Architecture Overview

```
Android APK
│
├── Native Android UI (Kotlin + Jetpack Compose)
│
├── Local OpenCode Client (REST + SSE)
│
├── Local OpenCode Server (on 127.0.0.1:<port>)
│
├── Embedded Linux-compatible execution environment
│   ├── PRoot (termux/proot fork, static aarch64)
│   ├── Alpine Linux 3.24 minirootfs (musl)
│   ├── shell (bash), git, ripgrep, nodejs, coreutils
│   └── OpenCode v1.18.23 (musl static binary)
│
└── App-owned project/workspace storage
```

Everything runs on-device. No Termux. No external dependencies.

---

## Requirements

- Android 8.0+ (API 26), arm64-v8a devices only.
- ~120 MB APK (bundled runtime).
- Internet access for model providers (Anthropic, OpenAI, etc.).

---

## Building

### Prerequisites
- JDK 17
- Android SDK (API 35, NDK r27c)
- `gradle` wrapper included

### Build the APK (with prebuilt runtime)
```bash
# Download runtime artifacts from a CI run or build them (see below)
./gradlew assembleDebug -Popencode.runtime.file=/path/to/rootfs.tar.gz
```

### Build Runtime Artifacts (Linux arm64 host)
```bash
cd scripts
./build-proot.sh      # builds libproot.so
./build-runtime.sh    # builds rootfs.tar.gz + manifest
```
Artifacts appear in `runtime/artifacts/`.

### CI
- `.github/workflows/ci.yml` — unit tests + APK build
- `.github/workflows/runtime.yml` — builds runtime on ubuntu-24.04-arm
- `.github/workflows/gates.yml` — runs G1–G15 on arm64 emulator

---

## Project Structure

```
app/                    # Android app module
├── src/main/
│   ├── java/dev/opencode/android/
│   │   ├── runtime/    # RuntimeManager, installer, proot launcher
│   │   ├── opencode/   # OpenCode REST/SSE client
│   │   ├── data/       # Settings, credentials, projects, chat repo
│   │   └── ui/         # Compose screens (chat, projects, settings, diagnostics)
│   └── assets/runtime/ # rootfs.tar.gz + sha256 (populated at build)
├── jniLibs/arm64-v8a/  # libproot.so (executable)
scripts/                # Runtime build scripts
runtime/                # versions.lock + artifacts/
docs/                   # ARCHITECTURE.md, RUNTIME.md, SECURITY.md, TESTING.md
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **targetSdk 28** | SELinux `untrusted_app_27` grants `execute_no_trans` on app_data_file, enabling PRoot to exec guest binaries from app-private storage. This is the same model Termux/UserLAnd use. |
| **PRoot from termux/proot fork** | Carries years of Android-specific fixes (seccomp/ptrace crash workaround, netlink emulation). |
| **Alpine 3.24 minirootfs + apk.static cross-install** | Reproducible, no emulation needed; builds fast on arm64 CI runners. |
| **OpenCode musl static binary** | Single self-contained ELF; no Bun/Node required for core agent. |
| **OPENCODE_AUTH_CONTENT / OPENCODE_CONFIG_CONTENT env injection** | Credentials never touch disk in plaintext; Keystore → env at spawn time. |
| **Foreground service** | Prevents mid-generation kills; owns exactly one runtime process. |
| **SSE for streaming, POST for prompts** | Matches OpenCode v1.18 server contract exactly. |

---

## Security

- Credentials encrypted with Android Keystore (`androidx.security:security-crypto`).
- OpenCode server binds **127.0.0.1 only**; never exposed to LAN.
- Cleartext traffic permitted only to loopback via `network_security_config.xml`.
- Runtime extracted to app-private storage; no world-readable files.
- No hardcoded keys, no telemetry, no cloud gateway.

See [SECURITY.md](docs/SECURITY.md) for details.

---

## Testing

| Layer | Command |
|-------|---------|
| Unit (JVM) | `./gradlew test` |
| Instrumented (G1–G15) | Run `gates.yml` workflow or `./gradlew connectedAndroidTest` on emulator |

See [TESTING.md](docs/TESTING.md).

---

## License

Apache-2.0 — matches OpenCode upstream.

---

## Acknowledgements

- [OpenCode](https://github.com/anomalyco/opencode) — the real agent.
- [Termux](https://github.com/termux/termux-packages) — execution model reference.
- [PRoot](https://github.com/proot-me/proot) — userspace chroot.
- [Alpine Linux](https://alpinelinux.org/) — minimal musl rootfs.