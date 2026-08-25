# OpenCode for Android — Zero-Setup Architecture

Status: **implemented as described** · Last updated: 2026-08-25

---

## 1. Where we started (audit of v1)

The previous version (`opencode-android-client-spec.md`) shipped a complete native client that
assumes a user-run `opencode serve` somewhere reachable. Audit result of the actual source tree:

| Area | State after v1 audit |
|---|---|
| Transport (OkHttp + basic auth + SSE w/ backoff) | solid |
| Event pipeline (parser → reducer → StateFlow) | solid, idempotent, tested |
| Sessions / chat / tools / diffs / files / settings | complete |
| Permissions & question-style gates | complete (native UI over `permission.updated` → `once/always/reject`) |
| Secure storage | Keystore-backed EncryptedSharedPreferences |
| Demo mode | isolated behind `EventTransport` interface |
| **Install experience** | **developer-oriented: URL + optional password entry required — fails §16/§17** |

Verification honesty: this environment (an Android device at `/mnt/sdcard`) has **no JDK,
Gradle or Android SDK**, so compilation/unit tests could not be executed here. The v1 tree
received a full manual cross-consistency pass instead; all builds/tests must be run on a dev
machine (`./gradlew :app:testDebugUnitTest :app:assembleDebug`).

## 2. Why "just embed OpenCode" was rejected

OpenCode's runtime is Bun-compiled TypeScript that expects desktop-Linux userspace.
Concrete blockers on current Android:

1. **W^X execution policy** — apps targeting API 30+ may only execute ELFs packaged as native
   libraries; `exec()` from app data (the Termux model) is unavailable, and Termux itself only
   works by targeting SDK 28, which Play Store no longer allows for new apps.
2. **No supported Bun/Node Android target** — running the JS runtime under bionic libc is
   unsupported and unstable territory; OpenCode also spawns `bash`, `git`, ripgrep, LSP/MCP
   subprocesses, none present on stock Android.
3. Even if it booted, local MCP stdio servers and toolchains would silently break — violating
   the "do not fake support" rule.

Conclusion: embedding would secretly amputate the agent we promise to preserve.

## 3. Chosen architecture: gateway-managed workspaces (+ optional self-host)

```
┌────────────────────────── Android app (this repo) ──────────────────────────┐
│ Compose UI ─ ChatEngine reducers ─ Repositories                              │
│        │ basic/bearer-auth HTTP + SSE (unchanged core layer)                 │
└────────┼─────────────────────────────────────────────────────────────────────┘
         │ TLS
┌────────▼──────────────────────── Gateway (open contract, docs/GATEWAY.md) ──┐
│ accounts, tokens, workspace CRUD, per-user isolation                          │
│        │ provisions & proxies                                                 │
│ ┌──────▼─────────────────────────────────────────────┐                        │
│ │ Workspace container = real Linux + real opencode    │                       │
│ │   bash · filesystem · git · LSP · MCP · permissions │                       │
│ └─────────────────────────────────────────────────────┘                       │
└───────────────────────────────────────────────────────────────────────────────┘
```

- **Normal users**: sign in → tap “New workspace” → chat. Runtime, ports, IPs are invisible.
- **Self-hosters**: run the same open gateway spec anywhere; sign into *their* URL.
- **Developers** (Settings → Developer): direct `opencode serve` connection kept from v1.
- **Demo mode**: unchanged, clearly labeled, never in production paths.

Nothing about OpenCode is reimplemented: workspaces run the genuine agent; the app speaks the
same official HTTP/SSE API as before, now pointed at workspace endpoints automatically.

## 4. Identity, credentials, security

- Gateway auth: `POST /auth/login {email,password}` → opaque bearer token; stored in
  Keystore-encrypted prefs alongside existing secrets; sent as `Authorization: Bearer …`.
- Workspace traffic carries the same bearer token; per-workspace isolation is the gateway's job
  (contract requires one OpenCode instance per workspace, no cross-workspace routing).
- No provider keys ship in the APK; models are configured inside each workspace via OpenCode's
  own auth (users run `/models`-style login once per workspace, exactly like desktop OpenCode).
- Cloud connections require https (enforced in-app); developer http:// allowed with explicit
  warning, as before.

## 5. Process/background lifecycle

- App owns zero runtime processes locally — nothing to babysit.
- While any session is busy and notifications are granted, an optional foreground service
  (`dataSync`) keeps the SSE stream alive longer so “…finished” notifications actually arrive;
  it stops when idle. This is the correct Android mechanism, not a background-execution promise.
- Notifications deep-link back to the exact session.
- Process death/reconnect handling unchanged from v1 (resync ticks + idempotent reducers).

## 6. Workspaces vs projects

A **workspace** = gateway-managed Linux environment containing one OpenCode project root
(bootstrapped empty or cloned from git via the agent itself). Local projects on-device are not
claimed because real tooling can't run in the Android sandbox (see §2). Connecting your own
computer remains available under Developer settings.

## 7. Feature compatibility matrix

| Capability | Status |
|---|---|
| Agent loop, prompts, streaming | ✅ full (real server) |
| Bash / file tools / edits / search | ✅ full (workspace Linux) |
| Git | ✅ full |
| MCP | ✅ remote servers fully; stdio servers run inside the workspace |
| Permissions / questions | ✅ native UI over real events |
| Models/providers | ✅ configured per-workspace through OpenCode auth; no keys in APK |
| On-device project files | ❌ impossible in sandbox — documented, not faked |
| Offline agent use | ❌ requires network (cloud runtime); demo mode simulates UI only |

## 8. Build/distribution

Single standard APK/AAB, arm64-v8a neutral (no native libs required by this architecture).
Gateway base URL injected at build time: `OPCODE_GATEWAY_URL=https://… ./gradlew assembleRelease`.
