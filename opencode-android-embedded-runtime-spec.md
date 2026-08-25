# STOP the Gateway/Cloud Architecture Work — Embedded Local Runtime Directive

STOP the gateway/cloud architecture work.

You misunderstood the fundamental product requirement.

I do NOT want:
- a cloud gateway
- a managed backend
- per-user Linux containers
- a VPS
- a remote OpenCode server
- a PC running OpenCode
- Termux
- any external runtime installation

I want the following:

When a user currently installs OpenCode through Termux, Termux provides the Unix-like runtime/environment that allows OpenCode and its dependencies to run.

I want to build an Android application that provides that experience INSIDE THE APPLICATION ITSELF.

The final product should effectively be:

Android APK
→ embedded/local runtime environment
→ real OpenCode
→ OpenCode's own server/API
→ Bash/shell
→ Git
→ filesystem
→ MCP
→ project workspace

The user installs ONE APK.

No Termux.
No second app.
No manual package installation.
No `pkg install`.
No Bun installation.
No Node installation.
No Git installation.
No `opencode` installation.
No `opencode serve`.
No SSH.
No PC.
No VPS.
No cloud gateway.

The application UI is a native ChatGPT/Claude-style Android interface, but underneath it is the REAL OpenCode runtime.

---

## DO NOT ASSUME THIS IS IMPOSSIBLE

Your previous report rejected embedded execution because:

- Android W^X
- Bun has no official Android target
- stock Android has no bash/git

Those are constraints, not the end of the investigation.

Termux itself demonstrates that Android can provide a substantial Unix-like environment and execute native binaries.

Therefore investigate HOW Termux actually makes this possible.

Study the relevant architecture of:

- Termux app
- Termux execution model
- Termux filesystem layout
- Termux native binaries
- Termux dynamic linker/runtime approach
- Termux packages
- shell execution
- process spawning
- executable placement
- Android sandbox constraints
- JNI/native execution
- bundled native binaries
- Android ABI support
- arm64-v8a
- subprocess management

Do not merely say "Android does not have Bash."

The question is:

"How can an application bundle/provide the necessary runtime and native components so the user does not need Termux?"

---

## INVESTIGATE OPENCODE'S ACTUAL DEPENDENCIES

Inspect the CURRENT OpenCode source and package configuration.

Determine exactly:

1. What runtime executes OpenCode?
2. What files are required?
3. What native dependencies exist?
4. What binaries are required?
5. Does OpenCode require Bun specifically?
6. Which parts of Bun are actually required at runtime?
7. Can the required runtime be compiled for Android?
8. If Bun itself cannot target Android, can the required OpenCode runtime be adapted?
9. Can OpenCode be built/packaged for an Android-compatible runtime?
10. Does OpenCode depend on APIs unavailable on Android?
11. Which dependencies can be replaced with Android-compatible native components without changing OpenCode's agent behavior?
12. Which dependencies absolutely cannot?
13. Can the OpenCode server run as a local process inside the Android app?
14. Can the OpenCode API remain unchanged?
15. Can Bash execute inside an app-owned workspace?
16. Can Git execute using a bundled native Git implementation?
17. Can MCP servers run?
18. Can child processes be created and managed?
19. What limitations are imposed by modern Android versions?

Do actual source-level investigation.

---

## TARGET ARCHITECTURE

The desired architecture is:

```
┌──────────────────────────────────────────┐
│             OpenCode Android             │
│                                          │
│  Native Compose UI                       │
│          │                               │
│          ▼                               │
│  Local OpenCode Client                   │
│          │                               │
│          ▼                               │
│  Local OpenCode Server                   │
│          │                               │
│          ▼                               │
│  Real OpenCode Agent                     │
│          │                               │
│     ┌────┼───────────┬────────────┐      │
│     ▼    ▼           ▼            ▼      │
│   Bash  Files       Git          MCP     │
│                                          │
│  Embedded runtime/native components      │
│                                          │
│  App-owned project workspace              │
└──────────────────────────────────────────┘
```

The OpenCode server should run LOCALLY on the Android device.

The Android UI should communicate with it using the same API/event model as OpenCode clients wherever possible.

This is NOT a remote client.

---

## PRESERVE OPENCODE

Do not rewrite OpenCode's agent into Kotlin.

Do not create a fake Android agent.

Do not replace the OpenCode agent loop.

Do not call model APIs directly from the UI as a replacement.

The goal is to RUN REAL OPENCODE.

The native Android application is the shell/host around it.

---

## TERMUX AS REFERENCE IMPLEMENTATION

Use Termux as a technical reference for solving the Android runtime problem.

Investigate how it handles:

- executable binaries
- dynamic libraries
- shell
- filesystem
- package layout
- process execution
- native code
- Android compatibility

The goal is NOT to depend on Termux.

The goal is to reproduce only the necessary runtime capabilities inside our own application.

Do NOT add Termux as a dependency.

Do NOT launch Termux.

Do NOT require Termux:API.

Do NOT tell users to install Termux.

---

## BASH

OpenCode's ability to execute shell commands is fundamental.

Investigate how to provide a real shell environment inside the application.

Potential approaches may include:

- bundled native shell
- Android-compatible shell binary
- bundled supporting Unix utilities
- app-private executable environment
- native process execution
- JNI bridge

Do not fake shell output.

If `bash` itself is technically inappropriate, determine what OpenCode actually requires and provide the closest real execution environment possible.

Document the difference if any.

---

## GIT

The user must not install Git.

Investigate how to provide real Git functionality.

Potential approaches:

- bundled Android-compatible Git binary
- native Git implementation
- compiled Git for Android
- other technically equivalent real Git runtime

Do not fake Git.

---

## MCP

Investigate how OpenCode's MCP functionality works.

If MCP servers require child processes, determine how to run them inside Android.

Do not simply remove MCP.

If some MCP servers are impossible because they require unsupported runtimes, support compatible MCP servers and clearly document the runtime constraints.

---

## FILESYSTEM

Provide OpenCode with a controlled workspace.

Use Android's storage mechanisms appropriately.

Do not expose the entire Android filesystem.

The user should explicitly choose/import/create a project/workspace.

Inside that workspace OpenCode should have the filesystem access it expects.

---

## PROCESS MANAGEMENT

The application must manage the local OpenCode process.

It should:

- start OpenCode automatically
- detect whether it is already running
- monitor health
- restart after crashes where safe
- stop it cleanly
- restore sessions after app restart
- handle Android lifecycle
- handle background restrictions
- avoid duplicate processes

The user should never see process management.

---

## LOCAL SERVER

The OpenCode server should bind locally.

Do not expose it unnecessarily to the LAN.

Prefer localhost/app-private communication.

The Android UI communicates with the local OpenCode server.

No internet-facing server is required.

---

## APK EXPERIENCE

The final user experience must be:

Install APK
↓
Open
↓
Create/open workspace
↓
Chat

That is all.

No technical setup.

---

## IMPORTANT: IF DIRECT BUNDLING IS IMPOSSIBLE

Do NOT immediately switch to a cloud gateway.

Instead determine the exact blocking dependency.

Then investigate whether that dependency can be:

- cross-compiled for Android
- bundled as a native executable
- wrapped with JNI
- replaced with a compatible runtime component
- adapted without modifying OpenCode's core agent behavior

Only if a specific OpenCode dependency is fundamentally impossible to run on Android should you report that capability as blocked.

For every blocker, provide:

1. Exact dependency.
2. Why Android prevents it.
3. Whether Termux solves it.
4. Whether we can reproduce the required capability ourselves.
5. Whether a native Android build is possible.
6. What OpenCode functionality would be lost.
7. Whether there is a technically sound workaround.

Do not use "Bun does not officially support Android" as the end of the investigation.

The question is whether WE can build/package the required runtime.

---

## ARCHITECTURE DOCUMENT

Rewrite:

`docs/ARCHITECTURE.md`

to describe the real local architecture.

Include:

- Android host
- embedded runtime
- OpenCode process
- local OpenCode server
- shell
- Git
- MCP
- filesystem
- process lifecycle
- security
- workspace isolation
- Android restrictions
- ABI support

---

## IMPLEMENTATION

After investigation, implement the technically best local architecture.

Do not implement a fake proof of concept.

The first release should be a real Android application capable of running the real OpenCode runtime locally.

---

## VALIDATION

The final acceptance test is:

Fresh Android device.

Install ONE APK.

No Termux installed.

No OpenCode installed.

No Bun installed.

No Node installed.

No Git installed.

No shell installed separately.

Open application.

Create workspace.

Send:

"Inspect this project and tell me what it does."

OpenCode must actually run locally.

Then test:

- file reading
- file editing
- shell command
- Git
- streaming
- session persistence
- permission handling
- MCP where supported
- app restart
- process restart

The result must demonstrate that the application itself provides the environment OpenCode needs.

---

## MOST IMPORTANT

Do not build a cloud SaaS.

Do not build a remote OpenCode client.

Do not build a Termux wrapper.

Build:

**OpenCode itself, running locally inside a native Android application, with a ChatGPT/Claude-style UI.**

The user should experience it as one application.

The infrastructure that Termux normally provides should become an internal implementation detail of our APK.

Start by investigating Termux's execution architecture and OpenCode's actual runtime dependencies before making any architectural decision.
