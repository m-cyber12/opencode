# Runtime gates — empirical validation protocol

Every claim about the embedded runtime is a **hypothesis until a gate prints PASS on an Android
emulator at the target API level** (CI job `runtime-gates` / `emulator-gates`, arm64 image,
API 34). Prior art is treated as a hint, never as proof.

## Gates

| ID | Chain under test | What PASS means |
|---|---|---|
| G1 | Android → proot → Alpine shell | jniLibs exec + proot/loader + rootfs boot + guest shell works at target API level |
| G2 | Android → proot → Bun | static musl Bun runs, evaluates JS (JIT) inside the guest |
| G3 | Android → proot → OpenCode server | `opencode serve` boots under Bun; `/global/health` = healthy |
| G4 | OpenCode → Bash/Git/filesystem | real bash executes, real git reports version, file write/read via tools |
| G5 | OpenCode → MCP stdio child process | OpenCode spawns `node mcp-server.mjs` and completes a stdio handshake |
| G6 | streaming/SSE | `/global/event` delivers `session.created` frames to the client |
| G7 | crash/restart | SIGKILL of the runtime → auto-restart → healthy again, sessions intact |
| G8 | app restart/session recovery | clean stop + fresh runtime instance → sessions recoverable |

## Failure protocol (per directive)

1. The workflow fails on ANY `result=FAIL` line — no partial greens.
2. Investigation targets the exact failing hop using:
   - gate `detail=` message (first 400 chars of the thrown error),
   - `runtime/logs/opencode-stderr.log` from the device (uploaded with `gate-logs` artifact),
   - re-running the single gate locally via
     `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.gate=Gx`
     is intentionally NOT supported in v1: gates share ordered state (rootfs, server, session).
     Re-run `allGates`; individual reproduction happens in an interactive shell via the same
     `RuntimeEnv.build` argv printed by `RuntimeEnvTest`.
3. Forbidden: swapping a failing component for a mock/remote/fake. Fix the component or document
   the blocker per the spec's 7-point blocker template.

## Artifacts & placement

CI builds (`runtime/build-proot.sh`, `runtime/build-rootfs.sh` inside an aarch64 Alpine
container under qemu):

```
out/libproot.so              → app/src/main/jniLibs/arm64-v8a/libopx-proot.so
out/libproot-loader.so       → app/src/main/jniLibs/arm64-v8a/libopx-proot-loader.so
out/opencode-rootfs.tar.gz   → app/src/main/assets/runtime/opencode-rootfs-arm64.tar.gz
out/gates-manifest.txt       → app/src/main/assets/runtime/gates-manifest.txt
out/versions.lock            → app/src/main/assets/runtime/versions.lock
```

The APK requires `android:extractNativeLibs="true"` so those libraries land in
`nativeLibraryDir` — the ONLY location where execve is permitted for untrusted apps.

## Version bumps

Edit `runtime/versions.lock`. Every bump re-runs all gates; the lockfile hash is embedded in
the rootfs marker so devices re-extract on upgrade.
