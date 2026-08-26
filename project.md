# PROJECT SPECIFICATION
# OpenCode for Android — Embedded Local Agent Runtime
#
# IMPORTANT:
# This is a fresh implementation task.
# Do not rely on any previous conversation, previous plan, or previous architectural decision.
# Treat this document as the authoritative product specification.

============================================================
1. PRODUCT IN ONE SENTENCE
============================================================

Build a native Android application that provides the full OpenCode agent experience locally on the Android device, inside ONE APK, without requiring Termux, a PC, a remote server, a cloud gateway, or any separately installed runtime.

The user should experience it like ChatGPT/Claude:

Install APK → Open app → choose/create project → chat with OpenCode.

Under the UI, however, this must be REAL OpenCode running locally on the Android device.

The goal is NOT to create an OpenCode-like agent.

The goal is to run OpenCode itself.

============================================================
2. ABSOLUTE PRODUCT REQUIREMENT
============================================================

The final architecture should conceptually be:

Android APK
│
├── Native Android UI
│
├── Local OpenCode client
│
├── Local OpenCode server
│
├── Real OpenCode agent/runtime
│
├── Embedded Linux-compatible execution environment
│   ├── shell
│   ├── filesystem
│   ├── git
│   ├── core utilities
│   ├── runtime dependencies
│   └── MCP process support
│
└── App-owned project/workspace storage

Everything runs on-device.

There must NOT be:

- Termux
- Termux:API
- user-installed Bash
- user-installed Git
- user-installed Bun
- user-installed Node
- user-installed OpenCode
- SSH
- PC dependency
- VPS dependency
- cloud gateway
- managed Linux container
- remote OpenCode server
- external backend required for normal operation

The user installs ONE APK.

============================================================
3. WHAT THIS PRODUCT IS NOT
============================================================

Do NOT build:

- a cloud SaaS
- a gateway service
- a remote OpenCode client
- a web wrapper around ChatGPT
- a fake OpenCode implementation
- a Kotlin rewrite of the OpenCode agent
- a UI that directly calls LLM APIs while pretending to be OpenCode
- a Termux launcher
- a Termux wrapper
- a PC companion application
- a requirement for the user to manually run `opencode serve`

Do NOT solve Android execution problems by moving OpenCode to a remote server.

Do NOT remove OpenCode capabilities merely because they are inconvenient to run on Android.

============================================================
4. CORE TECHNICAL PRINCIPLE
============================================================

On desktop, OpenCode runs inside an operating-system environment that provides:

- process execution
- shell
- filesystem
- networking
- native binaries
- runtime dependencies
- Git
- temporary directories
- child processes
- MCP servers

Termux demonstrates that Android can provide a substantial Unix-like environment capable of running real Linux-oriented tooling.

This project should reproduce the REQUIRED capabilities of that environment internally.

Termux itself must NOT be a dependency.

The internal implementation may use technically appropriate mechanisms such as:

- Android native libraries
- bundled native binaries
- JNI
- a proot-based userspace
- an Alpine/minimal Linux userspace
- bundled runtime binaries
- Android-compatible native builds
- app-private filesystem
- subprocess management

But these are implementation choices, not assumptions.

Determine the technically correct architecture through investigation and empirical testing.

============================================================
5. REAL OPENCODE REQUIREMENT
============================================================

The agent behavior must come from the real OpenCode implementation.

Do NOT reimplement:

- agent loop
- tool execution logic
- model routing
- permissions
- session semantics
- OpenCode server API
- OpenCode event system
- OpenCode tool definitions

unless a tiny platform adaptation is absolutely required.

Prefer:

REAL OPENCODE
+
Android host/runtime layer

rather than:

ANDROID APP
+
FAKE OPENCODE CLONE

The Android application is primarily the host, runtime manager, and native UI/client.

============================================================
6. RUNTIME FEASIBILITY MUST BE PROVEN FIRST
============================================================

Before implementing the full product, perform a source-level investigation of the CURRENT OpenCode codebase and its actual runtime dependencies.

Determine precisely:

1. What runtime executes OpenCode?
2. Is Bun required?
3. Which Bun features are actually used?
4. Which OpenCode dependencies are native?
5. Which binaries are required?
6. Which dependencies require Linux behavior?
7. Which dependencies require glibc or musl?
8. Which dependencies require child processes?
9. Which dependencies require ptrace?
10. Which dependencies interact with seccomp?
11. Which dependencies require filesystem features unavailable on Android?
12. Which MCP transports/process models are used?
13. Which parts can run unchanged?
14. Which parts require Android-specific adaptation?

Do not accept statements such as:

"Bun does not officially support Android, therefore impossible."

That is not sufficient.

Determine whether the required runtime can be:

- cross-compiled
- bundled
- executed through a userspace compatibility layer
- adapted
- replaced with a compatible component without changing OpenCode's core behavior

Likewise, do not assume that PRoot works simply because somebody has demonstrated it elsewhere.

Every critical assumption must be empirically tested.

============================================================
7. CANDIDATE RUNTIME ARCHITECTURE
============================================================

A likely architecture is:

Native Android app
        │
        ▼
Android runtime host
        │
        ▼
PRoot / compatible execution layer
        │
        ▼
Minimal Alpine Linux userspace
        │
        ├── shell
        ├── git
        ├── coreutils
        ├── ripgrep
        ├── Node.js if required
        ├── Bun if required
        └── OpenCode
                │
                ▼
        Local OpenCode server
                │
                ▼
        Android UI/client

This is a CANDIDATE architecture only.

Validate it.

If another architecture is technically superior, use it.

Do not select an architecture because it is convenient to describe.

Select it because it actually runs.

============================================================
8. CRITICAL RUNTIME GATES
============================================================

Create automated Android emulator tests that empirically prove the runtime.

At minimum:

G1:
Android native host can launch the execution layer.

G2:
Execution layer can boot the minimal userspace.

G3:
Real shell executes commands.

G4:
Real Bun/runtime executes successfully, if Bun is required.

G5:
Real OpenCode starts locally.

G6:
OpenCode server health endpoint responds successfully.

G7:
OpenCode can execute a shell command.

G8:
OpenCode can read/write project files.

G9:
Real Git works.

G10:
MCP stdio child process works.

G11:
OpenCode streaming/SSE/event flow works.

G12:
Permissions/tool approval work.

G13:
OpenCode process can be stopped and restarted.

G14:
Android app can restart and reconnect to the existing/recovered session.

G15:
A real end-to-end task succeeds:

"Inspect this project and explain what it does."

The test must involve actual OpenCode execution.

No mocks.

No fake shell.

No fake Git.

No fake OpenCode.

No remote fallback.

============================================================
9. HARD GATE RULE
============================================================

Do NOT proceed to full product implementation if the runtime gates have not been empirically validated.

If a gate fails:

1. Stop.
2. Capture exact logs.
3. Identify the failing component.
4. Determine the root cause.
5. Investigate technically valid fixes.
6. Implement the fix.
7. Re-run the failed gate.
8. Repeat.

Never hide a failure by:

- mocking it
- skipping it
- replacing it with a remote service
- returning hardcoded output
- pretending a static analysis result is an execution result

If a genuine blocker remains, report it explicitly.

============================================================
10. TERMUX AS A TECHNICAL REFERENCE
============================================================

Study how Termux achieves:

- native executable execution
- filesystem layout
- dynamic linking
- shell execution
- process creation
- package management
- native libraries
- Android compatibility

The purpose is to understand the problem.

Do NOT install or depend on Termux in the final product.

Do NOT launch Termux.

Do NOT require Termux.

Do NOT require Termux:API.

Reproduce only the capabilities actually required by OpenCode.

============================================================
11. ANDROID PROCESS EXECUTION
============================================================

Investigate modern Android restrictions carefully.

Do not assume:

"APK assets can simply be chmod +x and executed."

Determine the correct Android-supported mechanism for executing bundled native code.

Investigate:

- jniLibs
- nativeLibraryDir
- JNI
- ProcessBuilder
- execve
- Android linker behavior
- targetSdk restrictions
- W^X
- ART
- ABI compatibility
- Android 13+
- Android 14+
- Android 15+
- Android 16 if relevant

Any claimed execution mechanism must be validated on the target Android API level.

============================================================
12. ABI
============================================================

Version 1 should prioritize:

arm64-v8a

Do not spend time supporting every ABI unless it is straightforward.

Document unsupported ABIs clearly.

The runtime must detect incompatible devices gracefully.

============================================================
13. FILESYSTEM
============================================================

The user should have a project/workspace concept.

The app should provide:

- create project
- import/open project
- rename project
- delete project
- project history
- workspace isolation

The OpenCode runtime should receive a controlled workspace.

Prefer app-private storage for internal runtime files.

Do not expose the entire Android filesystem to the agent by default.

If Android Storage Access Framework is required for importing/exporting projects, implement it cleanly.

The agent should be able to work naturally inside its project workspace.

============================================================
14. SHELL
============================================================

Shell execution is a core OpenCode capability.

Provide a REAL shell environment.

The user should not need to install anything.

The shell should support the commands required by normal OpenCode operation.

At minimum investigate:

- sh
- bash if required
- coreutils
- grep
- sed
- awk
- find
- cat
- mkdir
- cp
- mv
- rm
- chmod
- env
- pwd
- uname
- ps
- tar
- gzip/zstd where needed

Do not fake command output.

============================================================
15. GIT
============================================================

Git is a first-class requirement.

Provide real Git functionality.

The user must not install Git separately.

Investigate the best Android-compatible approach:

- native Git binary
- statically linked Git
- compatible build
- other real implementation

Test:

- git init
- git status
- git add
- git commit
- git diff
- branch operations
- repository inspection

============================================================
16. MCP
============================================================

MCP is part of OpenCode's agentic functionality.

Do not remove MCP merely because it is inconvenient.

Investigate:

- stdio MCP servers
- HTTP MCP
- SSE where applicable
- child process spawning
- Node-based MCP servers
- environment variables
- filesystem access
- network access

Support all MCP mechanisms that can technically operate in the Android runtime.

If a particular MCP server cannot run because it requires an unavailable runtime, document the exact limitation.

Do not cripple OpenCode globally.

============================================================
17. NETWORK
============================================================

The OpenCode runtime needs network access for model providers and supported MCP/network tools.

The runtime must be able to make outbound HTTPS connections.

The local OpenCode server should bind to loopback only.

Prefer:

127.0.0.1

Do not expose the OpenCode server to the LAN unless explicitly enabled by the user.

No cloud gateway is required.

============================================================
18. API KEYS / MODEL PROVIDERS
============================================================

OpenCode still needs access to model providers.

The user should be able to configure provider credentials through the Android UI.

Store secrets securely using Android Keystore-backed storage where appropriate.

Never hardcode API keys.

Never put private keys into the APK.

Never send provider credentials to a project-specific remote backend.

OpenCode should continue to use its normal provider/model configuration as much as technically possible.

============================================================
19. LOCAL OPEN CODE SERVER
============================================================

The OpenCode server should run locally on-device.

The Android UI should communicate with it through localhost.

Do not expose it publicly.

Do not create a cloud proxy.

Do not modify OpenCode's API unnecessarily.

Preserve:

- sessions
- events
- streaming
- tool calls
- permissions
- file operations
- server state

The UI should behave as a client of the local OpenCode server.

============================================================
20. PROCESS LIFECYCLE
============================================================

The Android host must manage the runtime reliably.

Implement:

- startup
- health check
- graceful shutdown
- crash detection
- restart
- exponential backoff
- duplicate-process prevention
- runtime version validation
- runtime extraction
- corruption detection
- recovery
- logs
- diagnostics

Do not start multiple OpenCode servers accidentally.

Do not leave zombie processes.

Handle Android lifecycle correctly.

If long-running foreground execution is required, use an appropriate Android foreground service and document why.

============================================================
21. RUNTIME PACKAGING
============================================================

The runtime should be bundled with the application or otherwise delivered as part of the application's installation process.

The user must NOT have to manually download runtimes.

If APK/AAB size becomes large, that is an accepted tradeoff.

Do not sacrifice OpenCode functionality merely to reduce APK size.

However, optimize compression and packaging intelligently.

Target expectation:

A large APK is acceptable.

"Install once and everything works" is more important than a tiny APK.

============================================================
22. FIRST-RUN EXPERIENCE
============================================================

Fresh installation:

1. Install APK.
2. Open app.
3. Welcome screen.
4. Runtime initializes automatically.
5. Runtime extraction/preparation happens automatically.
6. User creates or opens a project.
7. User starts chatting.

No:

- terminal
- command prompt
- URL
- port number
- Termux
- SSH
- manual runtime setup
- manual package installation

The technical complexity must be invisible to the user.

============================================================
23. UI / UX
============================================================

The application should feel like a polished modern AI chat application.

Visual inspiration:

- ChatGPT
- Claude
- Gemini
- Claude Code desktop

But do NOT copy their branding or proprietary UI.

The main screen should be conversation-first.

Required UX:

- chat history
- streaming responses
- markdown
- syntax-highlighted code
- expandable tool calls
- command execution cards
- file edits
- diffs
- permission prompts
- errors
- progress indicators
- session switching
- project switching
- new conversation
- stop generation
- retry
- regenerate where supported
- attachments/files where OpenCode supports them
- runtime status
- settings
- diagnostics

Tool activity should feel native to the chat.

Example:

Assistant
"Let me inspect the project."

[Tool: shell]
$ find . -maxdepth 2 -type f

[12 files found]

[Tool: read file]
README.md

Assistant
"This project is..."

The terminal exists underneath.

The user does NOT have to interact with a terminal to use the agent.

============================================================
24. AGENTIC EXPERIENCE
============================================================

The application must preserve the agentic nature of OpenCode.

The agent should be able to:

- inspect files
- search the project
- edit files
- create files
- execute shell commands
- use Git
- use MCP
- reason over multiple steps
- request permission when appropriate
- stream progress/events
- maintain sessions

Do not reduce the application to:

"send prompt → receive text."

That would defeat the purpose.

============================================================
25. MEMORY / PERSISTENCE
============================================================

Preserve OpenCode session persistence.

Additionally investigate a lightweight persistent memory layer for:

- project-level context
- user preferences
- important project facts
- previous decisions

Do not invent a fake memory system that pollutes prompts unnecessarily.

Memory must be:

- inspectable
- editable
- scoped
- removable
- privacy-conscious

If OpenCode already provides an appropriate mechanism, prefer using it.

============================================================
26. PERMISSIONS
============================================================

Tool permissions are important.

The UI should clearly communicate when OpenCode wants to:

- execute commands
- modify files
- perform potentially destructive operations
- access network resources where relevant

Do not remove OpenCode's permission model merely to simplify the UI.

Make permissions mobile-friendly.

============================================================
27. SECURITY
============================================================

Security requirements:

- app-private runtime storage
- app-private project storage where appropriate
- loopback-only OpenCode server
- secure credential storage
- no hardcoded secrets
- no unnecessary LAN exposure
- no arbitrary root access
- no requirement for Android root
- no silent privilege escalation
- validate extracted runtime artifacts
- integrity/hash checking where practical
- safe workspace boundaries
- clear user permission flows

The agent must not gain unrestricted access to Android's private OS areas.

============================================================
28. PERFORMANCE
============================================================

PRoot or similar compatibility layers may introduce overhead.

Measure it.

Do not optimize prematurely.

Measure:

- runtime startup
- OpenCode startup
- shell command latency
- file operations
- Git operations
- agent response streaming
- memory usage
- CPU usage
- storage usage

Document known overhead.

============================================================
29. OFFLINE BEHAVIOR
============================================================

The runtime itself should work without internet.

Naturally, cloud model providers require internet access.

The application should distinguish:

- runtime unavailable
- runtime healthy
- provider/network unavailable
- model/provider authentication failure

Do not make the entire app appear broken because an LLM provider is unreachable.

============================================================
30. FAILURE HANDLING
============================================================

Errors should be human-readable.

For example:

BAD:

"Process exited 1."

GOOD:

"OpenCode runtime stopped unexpectedly.
Restarting the local agent…"

Advanced diagnostics should still expose:

- runtime logs
- OpenCode logs
- crash information
- runtime version
- device ABI
- Android API level

============================================================
31. DEVELOPMENT ARCHITECTURE
============================================================

Prefer:

Kotlin
Jetpack Compose
AndroidX
Coroutines
Flow

Use clean modular boundaries.

Suggested structure:

app/
  ui/
  data/
  domain/
  runtime/
  opencode/
  filesystem/
  security/
  settings/

runtime/
  artifacts/
  build scripts/
  versions.lock/

Do not blindly follow this structure if the codebase suggests a better architecture.

The important separation is:

Android host
↕
runtime manager
↕
OpenCode server
↕
OpenCode agent

============================================================
32. TESTING
============================================================

Required tests:

Unit tests:

- runtime state machine
- extraction
- checksum verification
- version handling
- environment construction
- process lifecycle
- port selection
- health checks
- restart/backoff
- session persistence

Integration tests:

- runtime boot
- shell
- Git
- filesystem
- OpenCode server
- MCP
- SSE

Instrumentation/emulator tests:

- complete runtime boot
- OpenCode health
- real agent request
- tool execution
- streaming
- permissions
- restart
- session recovery

End-to-end acceptance test:

Fresh emulator/device
+
fresh APK
+
no Termux
+
no OpenCode
+
no Bun
+
no Git
+
no external runtime

Install → Open → Create project → ask OpenCode to inspect project → receive real streamed agent response with real tool execution.

============================================================
33. CI
============================================================

Create CI capable of validating the runtime.

CI should build:

- Android app
- native components
- runtime artifacts
- rootfs
- emulator test environment

The workflow must produce clear gate results.

Example:

G1 PASS
G2 PASS
G3 PASS
...

Do not let CI claim success merely because Gradle compiled.

Runtime gates must actually execute the runtime.

============================================================
34. NO "GREEN BUILD" LIES
============================================================

A successful Kotlin compilation does NOT prove the product works.

A successful unit-test suite does NOT prove OpenCode works.

A successful APK build does NOT prove the runtime works.

Only real execution of:

Android
→ embedded runtime
→ OpenCode
→ tools

proves the core product.

============================================================
35. DOCUMENTATION
============================================================

Create/update:

README.md
docs/ARCHITECTURE.md
docs/RUNTIME.md
docs/SECURITY.md
docs/TESTING.md

Documentation must explain:

- architecture
- runtime packaging
- why Termux is not needed
- OpenCode integration
- shell
- Git
- MCP
- filesystem
- process lifecycle
- security
- supported Android versions
- supported ABI
- known limitations
- APK size
- performance
- troubleshooting

============================================================
36. IMPLEMENTATION PROCESS
============================================================

Follow this sequence.

PHASE 0 — REPOSITORY AUDIT

Inspect the current repository.

Understand:

- existing Android app
- existing OpenCode integration
- existing UI
- existing server/client layer
- existing tests
- existing build configuration

Do not destroy useful existing work.

Remove obsolete cloud/gateway architecture if present.

Do not preserve it merely because it already exists.

PHASE 1 — SOURCE/RUNTIME INVESTIGATION

Investigate current OpenCode dependencies.

Investigate Termux's relevant execution model.

Investigate Android execution constraints.

Produce a concrete dependency map.

PHASE 2 — MINIMUM RUNTIME SPIKE

Before building the whole application, prove:

Android
→ native host
→ execution layer
→ userspace
→ shell
→ runtime
→ OpenCode

Do this on an actual Android emulator/device.

PHASE 3 — RUNTIME GATES

Run G1-G15.

Fix failures.

Do not proceed while critical runtime gates are unknown.

PHASE 4 — EMBEDDED RUNTIME HOST

Implement:

- extraction
- validation
- startup
- health
- shutdown
- restart
- logs
- lifecycle

PHASE 5 — OPEN CODE INTEGRATION

Connect Android UI to local OpenCode server.

Preserve existing API/event behavior.

PHASE 6 — PRODUCT UI

Implement polished ChatGPT/Claude-style interface.

PHASE 7 — FILESYSTEM / PROJECTS / SETTINGS / MEMORY

Implement the complete user experience.

PHASE 8 — HARDENING

Test:

- crashes
- app restart
- process restart
- low storage
- network loss
- provider errors
- corrupted runtime
- large projects
- large conversations
- background/foreground transitions

PHASE 9 — RELEASE BUILD

Produce:

- release APK/AAB
- reproducible runtime artifacts
- documentation
- test report

============================================================
37. CRITICAL RULE ABOUT ARCHITECTURE CHANGES
============================================================

Do not introduce a cloud gateway simply because embedded execution is difficult.

Do not move the problem to a server.

If embedded execution fails, investigate the exact blocker.

Only change the architecture if there is a genuinely demonstrated technical impossibility.

If you propose an architectural change, explain:

1. Which requirement becomes impossible.
2. Why.
3. Evidence.
4. Alternatives investigated.
5. What functionality would be lost.

Do not make architectural changes silently.

============================================================
38. CRITICAL RULE ABOUT OPEN CODE FEATURES
============================================================

The objective is feature preservation.

Create a capability matrix:

Capability | Desktop OpenCode | Android implementation | Status

Include:

- agent loop
- models
- sessions
- streaming
- file read
- file write
- shell
- Git
- permissions
- MCP
- project/workspace
- server API
- tools
- configuration
- authentication
- memory/persistence

The Android version should preserve as close to 100% of OpenCode's agent functionality as technically possible.

Any missing capability must be explicitly documented.

============================================================
39. SOURCE OF TRUTH
============================================================

Use the CURRENT OpenCode source code and official technical behavior as the source of truth.

Do not rely on outdated assumptions.

If the repository's OpenCode version changes, update the dependency map and runtime packaging accordingly.

Pin versions reproducibly.

Maintain a versions.lock file containing:

- OpenCode version
- runtime version
- Alpine version
- proot version if used
- native component versions
- hashes

============================================================
40. HONESTY REQUIREMENT
============================================================

Never claim:

"implemented"

when it only exists as code.

Never claim:

"tested"

when it was only statically inspected.

Never claim:

"works on Android"

without actual Android execution evidence.

At the end of each phase report:

IMPLEMENTED
TESTED
NOT TESTED
BLOCKED

separately.

============================================================
41. FINAL ACCEPTANCE CRITERIA
============================================================

The project is complete only when all of the following are true:

1. A fresh Android device can install ONE APK.
2. No Termux is installed.
3. No OpenCode is installed separately.
4. No Bun is installed separately.
5. No Node is installed separately.
6. No Git is installed separately.
7. No shell is installed separately.
8. No PC is required.
9. No VPS is required.
10. No cloud gateway is required.
11. Runtime initializes automatically.
12. Real OpenCode runs locally.
13. Local OpenCode server works.
14. Agent can inspect files.
15. Agent can edit files.
16. Agent can execute real shell commands.
17. Real Git works.
18. MCP works where technically supported.
19. Streaming works.
20. Permissions work.
21. Sessions persist.
22. Runtime can recover after crashes.
23. App can recover after restart.
24. Credentials are securely stored.
25. OpenCode server is not unnecessarily exposed to the network.
26. The UI feels like a polished modern AI chat application.
27. The underlying agent remains REAL OpenCode.

The final user experience should be:

INSTALL
↓
OPEN
↓
CREATE/OPEN PROJECT
↓
CHAT
↓
REAL OPENCODE AGENT WORKS

The user should never need to know that a Linux-compatible runtime, shell, Git, Bun, PRoot, Alpine, or any other infrastructure exists underneath.

============================================================
42. EXECUTION INSTRUCTION
============================================================

Do NOT just give me a plan.

Start by auditing the repository and investigating the runtime.

Then implement the project.

When you reach a decision that affects the core architecture, validate it empirically before committing to it.

Do not ask unnecessary questions.

Make reasonable engineering decisions yourself.

Do not stop after scaffolding.

Do not stop after writing documentation.

Do not stop after a successful Gradle build.

Continue until the implementation is genuinely functional or a specific, experimentally demonstrated technical blocker prevents completion.

If a blocker appears, investigate and fix it rather than immediately redesigning the product around the blocker.

At every major milestone, report concise evidence:

- what was implemented
- what was actually executed
- test result
- remaining blocker

The final goal is not a prototype UI.

The final goal is:

**A single Android application that feels like ChatGPT/Claude, but underneath is a real, local, agentic OpenCode runtime with its shell, filesystem, Git, MCP, tools, sessions and server — with no Termux or external infrastructure required from the user.**