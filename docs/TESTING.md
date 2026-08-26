# Testing Strategy

## Test Pyramid

```
                    ┌─────────────────┐
                    │  G1–G15 Gates   │  ← Instrumented (emulator/device)
                    │  (no mocks)     │
           ┌────────┴────────┴────────┐
           │   Integration Tests      │  ← Runtime + OpenCode client + SSE
           │   (mockwebserver)        │
    ┌──────┴──────┐           ┌──────┴──────┐
    │  Unit Tests │           │  Unit Tests │
    │  (JVM)      │           │  (Android)  │
    └─────────────┘           └─────────────┘
```

## Unit Tests (JVM) — `./gradlew test`

| Module | Coverage |
|--------|----------|
| `RuntimeStateMachineTest` | Phase transitions, backoff math |
| `ChecksumTest` | SHA-256 streaming, case-insensitive match |
| `PortAllocatorTest` | Free port allocation, used port detection |
| `EventParserTest` | SSE frame parsing (connected, heartbeat, permission.asked, message.part.updated) |
| `DiffParserTest` | Unified diff line classification |
| `MarkdownTest` | Block splitting (code/text), unclosed fence |

Run locally: `./gradlew test --info`

## Integration Tests (mockwebserver) — `./gradlew connectedAndroidTest`

| Test | Validates |
|------|-----------|
| `OpenCodeClientTest` | REST request/response shapes, directory header routing |
| `ChatRepositoryTest` | SSE→UI merge: message.updated, part.updated, permission.asked/replied |
| `RuntimeInstallerTest` | Extraction with real tar.gz fixture, symlink preservation, mode bits |

## Gate Tests (G1–G15) — **No mocks, real execution**

Run via `.github/workflows/gates.yml` on `ubuntu-24.04-arm` + arm64 emulator.

| Gate | Description | Validation |
|------|-------------|------------|
| **G1** | Android native host can launch execution layer | `RuntimeManager` reaches `STARTING` phase |
| **G2** | Execution layer boots minimal userspace | Alpine rootfs extracted, PRoot starts, `HEALTHY` |
| **G3** | Real shell executes commands | `bash -c 'echo test'` via OpenCode shell tool |
| **G4** | Real runtime executes | `opencode` binary runs, serves `/global/health` |
| **G5** | OpenCode starts locally | Server responds with version |
| **G6** | Health endpoint responds | `GET /global/health` → `{"healthy":true}` |
| **G7** | Shell tool works | Agent runs command, output captured |
| **G8** | File read/write works | Agent creates/reads file in workspace |
| **G9** | Real Git works | `git init`, `commit`, `status` via agent |
| **G10** | MCP stdio works | (Optional) spawns configured MCP server |
| **G11** | SSE streaming works | `message.part.updated` deltas received |
| **G12** | Permissions flow works | `permission.asked` → UI → `reply` |
| **G13** | Stop/restart works | `manager.stop()` → `ensureStarted()` → healthy |
| **G14** | App restart reconnect | New process probes `lastKnownPort` or fresh start |
| **G15** | End-to-end task | "Inspect this project and explain" → substantive response |

### Running Gates Locally

```bash
# Requires: arm64 emulator (API 34+), runtime artifacts in app/src/main/assets/runtime/
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.opencode.android.gates.G1_NativeHostLaunch
```

## CI Configuration

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | push/PR | Unit tests + APK build (placeholder runtime) |
| `runtime.yml` | push(main) / dispatch | Build runtime artifacts on arm64 runner |
| `gates.yml` | schedule / dispatch / push(main) | Full G1–G15 on emulator with real artifacts |

## Emulator Notes

- **Runner**: `ubuntu-24.04-arm` (GitHub hosted arm64, QEMU TCG — slow but real arm64)
- **Image**: `system-images;android-35;default;arm64-v8a`
- **Boot time**: ~3–5 minutes cold; use `-no-snapshot -no-boot-anim -memory 2048`
- **Timeout**: 120 min for full gate suite

## Flakiness Mitigation

- Health polling deadline: 120s (cold proot + opencode startup)
- SSE reconnection: exponential backoff (500ms → 30s)
- Runtime crash classifier: auto-retry once with `PROOT_NO_SECCOMP=1`
- Gate tests use `runBlocking` with generous timeouts; fail fast on `RuntimeState.Failed`

## Acceptance Criteria (Spec §41)

The project is complete only when **all** G1–G15 pass on a fresh emulator/device with:
- No Termux, no OpenCode, no Bun, no Node, no Git installed
- Single APK install → open → create project → chat → real agent response with tool execution

## Local Development Testing

```bash
# 1. Build runtime (requires Linux arm64 or WSL)
cd scripts && ./build-runtime.sh

# 2. Build APK with real runtime
./gradlew assembleDebug -Popencode.runtime.file=../runtime/artifacts/rootfs.tar.gz

# 3. Install on device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Run unit tests
./gradlew test

# 5. Run single gate (requires emulator)
adb shell am instrument -w -r -e class dev.opencode.android.gates.G6_HealthEndpointResponds dev.opencode.android.test/androidx.test.runner.AndroidJUnitRunner
```