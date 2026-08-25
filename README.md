# OpenCode for Android

A production-quality **native Android client for OpenCode** — the full power of the OpenCode
agent (sessions, tools, permissions, files, diffs, models, MCP) behind a consumer-simple,
**zero-setup** experience:

> **Install APK → Open → Sign in → New workspace → Chat.**

No Termux. No Node/Bun. No `opencode serve`. No IPs, ports or SSH. The runtime is an
implementation detail — see `docs/ARCHITECTURE.md` for the full decision record and
`docs/GATEWAY.md` for the open, self-hostable gateway contract that makes it possible.

> «Change the interface. Do not change the agent.»

This app is a client/presentation layer only. Every capability is driven by the real OpenCode
server: no second agent loop, no direct provider calls, no local conversation database as the
source of truth.

---

## What you get

- **ChatGPT-simple onboarding**: welcome → sign in → create a workspace → chat. Workspaces are
  private Linux environments running the **real OpenCode agent** (bash, git, filesystem, MCP),
  provisioned for you by the gateway.
- **Chat-first workspace** with streaming responses, markdown rendering, syntax-highlighted code
  blocks, copy buttons, retry-on-error and scroll preservation.
- **Agent activity cards** — every tool call (bash, read, edit, search, webfetch, task, todo…) is
  visible as an expandable card with input/output/error/duration; unknown future tools fall back to
  a fully transparent generic renderer.
- **Permissions as first-class UI** — when OpenCode asks, the agent visibly pauses and a native
  approval card appears (`Reject` / `Always allow` / `Allow once`, mapped to the server's
  `once|always|reject` enum). Destructive commands are visually flagged.
- **Sessions** — ChatGPT-style drawer grouped by day, live busy/idle dots, rename/delete, abort,
  instant switching. OpenCode remains authoritative.
- **Files & diffs** — remote project browser + fuzzy find + content search, syntax-aware viewer,
  and a mobile unified diff viewer that live-refreshes from `session.diff`/`file.edited` events.
- **Models & agents** — pickers built from live `/config/providers` and `/agent` payloads;
  slash-command palette; per-turn model/agent selection. Provider keys never ship in the APK —
  they're configured inside your workspace via OpenCode itself.
- **Background done right** — optional foreground service while tasks run, completion &
  permission notifications with deep-links back to the exact session; automatic SSE reconnect
  with snapshot resync on any drop.
- **Security** — bearer/basic credentials in Keystore-encrypted storage, https enforced for
  cloud workspaces, TLS validation untouched, secrets never logged or backed up.
- **Demo mode** — one tap explores the entire UI against a simulated server (real parser, real
  reducer), clearly labeled, never part of production paths.
- **Developer mode** — hidden by default; reveals manual `opencode serve` connections, gateway
  override and raw diagnostics for power users.

---

## Architecture

```
┌──────────────── Android app ────────────────┐
│ Compose UI ─ ChatEngine ─ Repositories       │
│   basic/bearer HTTP + auto-reconnect SSE     │
└──────────────┬───────────────────────────────┘
               │ TLS
┌──────────────▼── Gateway (open contract) ────┐
│ accounts · workspace CRUD · isolation        │
│   └─ Workspace: real Linux + opencode serve  │
│        bash · git · fs · LSP · MCP           │
└───────────────────────────────────────────────┘
```

Why not embed the runtime in the APK? Android's W^X execution policy plus unsupported
Bun-on-Android targets make that a silently crippled imitation — the opposite of the goal.
Full reasoning: `docs/ARCHITECTURE.md` §2.

Package map:

```
com.opencode.client
├ core/          AppError/Outcome, JSON config, OkHttp+auth(basic/bearer), SSE, Keystore store
├ opencode/      DTOs, OpenCodeApi (+Http impl), event model & parser, DTO→domain mappers
├ domain/        stable UI-facing models
├ data/          repositories, DataStore settings, gateway API client (open contract)
├ engine/        ServerController, GatewayController, ChatEngine reducer, DiffEngine
├ demo/          DemoApi + DemoEventTransport (fully separated)
├ service/       optional keep-alive foreground service
└ ui/            theme, components, screens (onboarding/workspaces/workspace/files/
                 viewer/diff/settings/activity/projects/connect)
```

---

## Running it

### Normal users (zero setup)

1. Install the APK and open it.
2. Sign in (or create an account) — this is your **workspace gateway** account.
3. Tap **＋ New workspace**. A private Linux environment with a real OpenCode agent boots for you.
4. Chat: *"Fix the failing test in the auth module."* Watch it read files, run bash, edit code.
5. Come back later — completion notifications deep-link straight into the session.

Provider models are configured once inside a workspace through OpenCode's own auth
(the same `/models` login as desktop OpenCode). No keys ever touch the APK.

### Self-hosting the gateway

The gateway is an open spec (`docs/GATEWAY.md`) — implement it anywhere, then either point users'
builds at it or enter your URL under **Settings → Developer → Gateway** in any installed app.

### Developers: connect your own computer

Enable **Settings → Developer mode**, then *Connect your computer*:

```bash
opencode serve --hostname 0.0.0.0 --port 4096
# optional: OPENCODE_SERVER_PASSWORD=secret opencode serve ...
```

Enter `http://<your-lan-ip>:4096` (+ password) exactly like v1. This path is intentionally
invisible to normal users.

### Building

```bash
# cloud build with your gateway:
OPCODE_GATEWAY_URL=https://api.your-gateway.dev ./gradlew :app:assembleRelease

# local debug (demo + developer modes work without a gateway):
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Requirements: Android Studio Koala+, JDK 17, Gradle 8.9 (Android Studio provisions it).

### Testing

| Suite | Covers |
|---|---|
| `OpenCodeEventParserTest` | both SSE envelope shapes, delta/tool/permission decoding, malformed & future-event safety |
| `ChatEngineReducerTest` | streaming accumulation, tool transitions, duplicate idempotency, optimistic-prompt swap, permission gate, abort semantics |
| `DiffEngineTest` | unified hunk computation, numbering, oversized fallback |
| `HttpOpenCodeApiTest` | OpenCode wire contract via MockWebServer |
| `GatewayApiTest` | gateway wire contract: auth payloads, bearer headers, workspace CRUD |
| `AuthInterceptorTest` | bearer precedence, basic-auth encoding, no-credential pass-through |
| `SyntaxHighlighterTest` | tokenizer invariants |

Manual validation checklist (real device): fresh-install flow, kill/reopen resume, network
switch recovery, long-task completion notification deep-link, permission gate round-trip,
sign-out/sign-in isolation. See §30 of the product spec.

## Security notes

- Gateway tokens and server passwords live only in `EncryptedSharedPreferences`
  (AES256-GCM, Keystore master key), excluded from backups/device transfers. If secure storage
  cannot initialise, the app refuses to save credentials rather than degrade to plaintext.
- Cloud workspaces are https-only, enforced in code. Certificate validation is never disabled.
  Developer LAN connections may use plain HTTP behind an explicit warning.
- Permissions are enforced by the OpenCode server; the UI can only answer them, never bypass.
- The APK contains no provider keys and no privileged gateway credentials.

## Known limitations (by design)

- **On-device projects are not claimed**: real toolchains cannot run inside the Android sandbox;
  agent work happens in your workspace (or your own computer via Developer mode).
  Full reasoning in `docs/ARCHITECTURE.md` §2.
- Background execution follows platform reality: the optional foreground service extends how
  long streams stay attached; nothing survives process death or force-stop.
- Provider OAuth is configured per-workspace through OpenCode itself (not via this client).
- No dedicated "questions" endpoint exists upstream; interactive asks surface as permission
  requests and are rendered natively. PTY endpoints remain intentionally unused (experimental).
- The zero-setup path requires a gateway deployment (open spec + self-host guide included).
  Demo mode and Developer mode work fully without one.
