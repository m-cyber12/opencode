# Architecture

## High-Level Components

```
┌─────────────────────────────────────────────────────────────────┐
│ Android Application Process (dev.opencode.android)              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   UI Layer   │  │  Data Layer  │  │  OpenCode Client     │  │
│  │  (Compose)   │◄─┤ (DataStore,  │◄─┤  (REST + SSE)        │  │
│  └──────┬───────┘  │  Keystore)   │  └──────────┬───────────┘  │
│         │          └──────┬───────┘             │              │
│         ▼                 │                      │              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ RuntimeManager (foreground service, state machine)       │   │
│  │  • Extraction / verification                             │   │
│  │  • Port allocation / health polling                      │   │
│  │  • Process spawn / crash classification / backoff        │   │
│  └────────────────────────────┬─────────────────────────────┘   │
│                               │                                 │
│                    ProcessBuilder (spawn)                      │
│                               ▼                                 │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ PRoot Process (libproot.so from jniLibs, executable)           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Alpine rootfs (app-private storage)                     │   │
│  │   /usr/local/bin/opencode  →  opencode serve --port N   │   │
│  │   /bin/bash, /usr/bin/git, /usr/bin/rg, /usr/bin/node   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

1. **App launch** → `RuntimeService` (foreground) starts → `RuntimeManager.ensureStarted()`
2. **Extraction** (first run/upgrade) → `RuntimeInstaller` extracts `rootfs.tar.gz` from assets → verifies SHA-256 → writes marker
3. **Port allocation** → `PortAllocator` binds ephemeral loopback port → passes `--port` to OpenCode
4. **Spawn** → `ProotLauncher` builds argv/env → `ProcessBuilder` starts PRoot → OpenCode server binds 127.0.0.1:port
5. **Health** → `HealthChecker` polls `/global/health` → `HEALTHY` state
6. **UI** → `ChatRepository` opens SSE `/event?directory=` → streams `message.part.updated` → renders in Compose
7. **Prompt** → `OpenCodeClient.prompt()` POSTs to `/session/:id/message` → final response + live SSE deltas
8. **Permissions** → `permission.asked` event → `PermissionCard` → `POST /permission/:id/reply`

## Execution Model (targetSdk 28)

- **Why targetSdk 28?** Android 10+ removes `execute_no_trans` on `app_data_file` for `targetSdk ≥ 29`. With `targetSdk = 28`, the app runs in `untrusted_app_27` domain which **retains** this grant (AOSP `untrusted_app_27.te`).
- **PRoot binary** lives in `nativeLibraryDir` (`libproot.so`) — `apk_data_file` label, executable at **any** targetSdk (via `useLegacyPackaging=true`).
- **Guest binaries** (bash, git, opencode) live in extracted rootfs under `filesDir/runtime/rootfs` — `app_data_file` label, executable **only** because targetSdk 28.
- **Android 15/16**: No new exec/ptrace restrictions for apps.

## Lifecycle Guarantees

| Guarantee | Mechanism |
|-----------|-----------|
| Single runtime process | Mutex in `RuntimeManager` + port health probe before spawn |
| No zombie tracees | `proot --kill-on-exit` + `Process.destroyForcibly()` grace period |
| Crash auto-restart | `RuntimeManager` watches process exit → classifies (seccomp, OOM, crash) → exponential backoff (max 5) |
| App restart reconnect | `lastKnownPort` persisted; new process probes health before fresh start |
| Corruption detection | SHA-256 of bundle vs `.installed-sha256` marker on every start |

## Network

- OpenCode server: `127.0.0.1:<ephemeral>` (never `0.0.0.0`)
- Model providers: Outbound HTTPS from guest (resolv.conf → 8.8.8.8/1.1.1.1)
- Android cleartext: Only loopback via `network_security_config.xml`

## Storage

| Path | Purpose |
|------|---------|
| `filesDir/runtime/rootfs/` | Extracted Alpine + OpenCode |
| `filesDir/runtime/.installed-sha256` | Integrity marker |
| `filesDir/home/` | Guest `/root` (bind) — OpenCode auth.db, sessions |
| `filesDir/projects/<id>/` | Per-project workspace (bind → `/projects/<id>`) |
| `EncryptedSharedPreferences` | Provider API keys (Keystore-backed) |
| `DataStore` | UI settings (theme, defaults) |

---

## Component Responsibilities

| Component | File | Responsibility |
|-----------|------|----------------|
| `RuntimeManager` | `RuntimeManager.kt` | State machine, spawn, health, restart, backoff |
| `RuntimeInstaller` | `RuntimeInstaller.kt` | Asset extraction, SHA-256 verify, resolv.conf, marker |
| `ProotLauncher` | `ProotLauncher.kt` | argv/env construction, `ProcessBuilder` spawn |
| `HealthChecker` | `HealthChecker.kt` | `/global/health` polling |
| `OpenCodeClient` | `OpenCodeClient.kt` | REST API (sessions, messages, permissions, config) |
| `OpenCodeEventStream` | `OpenCodeEventStream.kt` | SSE `/event` consumer, reconnection |
| `ChatRepository` | `ChatRepository.kt` | Merges REST+SSE → `UiMessage` StateFlow |
| `SecureCredentials` | `SecureCredentials.kt` | Keystore-encrypted API keys |
| `ProjectRepository` | `ProjectRepository.kt` | Workspace CRUD, SAF import/export |

---

## Build-Time Artifact Pipeline

1. `scripts/build-proot.sh` → `libproot.so` (termux/proot static aarch64) → copied to `app/src/main/jniLibs/arm64-v8a/`
2. `scripts/build-runtime.sh` (ubuntu-24.04-arm):
   - Fetch Alpine minirootfs (pinned SHA-256)
   - Fetch OpenCode musl binary (pinned SHA-256)
   - `apk.static --arch aarch64 --root` install pinned packages
   - Write `rootfs.manifest.json` (fileCount, uncompressedBytes, versions)
   - Deterministic `tar.gz` (mtime=epoch, owner=0)
   - Output: `rootfs.tar.gz`, `rootfs.sha256`, `rootfs.manifest.json`
3. Gradle task `verifyRuntimeAsset` asserts assets present + checksum match before `assembleDebug`.
4. `gates.yml` downloads artifacts → builds APK → runs G1–G15 on arm64 emulator.

---

## OpenCode Server Contract (v1.18.x)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/global/health` | GET | Liveness |
| `/doc` | GET | OpenAPI spec |
| `/event?directory=` | GET (SSE) | Event stream |
| `/session` | GET/POST | List/create sessions |
| `/session/:id/message` | GET/POST | History / prompt |
| `/session/:id/abort` | POST | Cancel generation |
| `/permission` | GET | Pending requests |
| `/permission/:id/reply` | POST | Allow/deny |
| `/config` | GET/PATCH | Instance config |
| `/provider` | GET | Providers + auth |
| `/project/git/init` | POST | Initialize git repo |

All instance-scoped routes accept `x-opencode-directory` header for workspace routing.