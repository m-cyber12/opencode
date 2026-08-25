# OpenCode Android — Zero-Setup Production Transformation

You are continuing work on an existing Android project called OpenCodeGUI.

You previously implemented the first version of the application.

Now the product requirement is being upgraded significantly.

## THE CORE PRODUCT REQUIREMENT

The final application must provide a true ChatGPT-like installation experience.

The end user must be able to:

**Install APK → Open app → Start using OpenCode**

There must be NO requirement for the end user to:

- install Termux
- install OpenCode separately
- install Node.js
- install Bun
- install Python
- install Git manually
- run "opencode serve"
- run shell commands
- configure localhost
- find an IP address
- configure a port manually
- use SSH
- use ADB
- configure a Linux environment
- manually start a background server
- manually configure environment variables
- manually install dependencies
- understand what a CLI is

The application must NOT be:

> "Termux wearing a nice UI."

It must be a real standalone Android product.

The desired experience is:

> "Install → Open → Connect/Login if necessary → Chat → Done."

The user should not need to know that OpenCode is internally a server/CLI/agent runtime.

---

## 1. IMPORTANT: AUDIT THE EXISTING PROJECT FIRST

Before changing architecture or deleting anything:

Perform a complete audit of the existing OpenCodeGUI project.

Inspect:

- every Kotlin source file
- Gradle configuration
- AndroidManifest
- networking layer
- OpenCode API integration
- SSE implementation
- session management
- tool rendering
- permissions
- questions
- file browser
- diff viewer
- settings
- authentication
- secure storage
- demo mode
- tests
- README
- documentation
- all TODOs
- all temporary/mock implementations

Do not trust your previous completion report.

Inspect the actual source code.

Run every build/test that the environment allows.

If something cannot be verified because the current environment lacks Android SDK/JDK/tooling, explicitly state that instead of pretending it passed.

---

## 2. FIRST PRINCIPLE

The existing architecture was designed around:

```
Android App
→ OpenCode Server
→ OpenCode Runtime
```

That is acceptable as an internal architecture.

It is NOT acceptable as the user's installation experience.

The user must not be responsible for starting or managing the OpenCode server.

Therefore redesign the product so that the OpenCode runtime becomes an implementation detail.

The user should only interact with the Android application.

---

## 3. DO NOT CHEAT

Do NOT solve this by replacing OpenCode with a fake implementation.

- Do NOT create a simplified custom agent and call it OpenCode.
- Do NOT remove Bash.
- Do NOT remove filesystem tools.
- Do NOT remove Git.
- Do NOT remove MCP.
- Do NOT remove permissions.
- Do NOT remove agents.
- Do NOT remove model/provider support.
- Do NOT replace OpenCode's agent loop.
- Do NOT fake tool calls.
- Do NOT create mock responses in production.
- Do NOT hard-code fake AI behavior.

The objective remains:

> "Use the real OpenCode agent and preserve its real capabilities."

The UI may change completely.

The underlying agent must not be secretly replaced with a toy implementation.

---

## 4. DETERMINE THE REALISTIC RUNTIME ARCHITECTURE

Before implementation, investigate the current OpenCode source and determine exactly what is required to run the real OpenCode runtime.

Inspect:

- current OpenCode runtime requirements
- Bun/Node/runtime dependencies
- filesystem requirements
- subprocess requirements
- shell execution requirements
- Git requirements
- MCP requirements
- native dependencies
- networking
- process management
- environment variables
- configuration
- authentication
- model/provider APIs
- plugin system
- sandbox assumptions
- operating-system assumptions

Do NOT assume that OpenCode can simply be copied into an Android APK.

Do not assume it cannot either.

Actually investigate.

---

## 5. CHOOSE THE BEST ZERO-SETUP ARCHITECTURE

After auditing the runtime, choose the architecture that best satisfies ALL of these:

1. Real OpenCode agent.
2. Maximum OpenCode feature compatibility.
3. No Termux.
4. No manual CLI installation.
5. No manual server startup.
6. No manual runtime installation.
7. No manual IP/port configuration for normal users.
8. Secure.
9. Maintainable.
10. Production deployable.
11. Good Android UX.
12. Capable of running long agent tasks.
13. Capable of using Bash/filesystem/Git/MCP where technically supported.

Consider these architecture options objectively:

### Option A — Embedded OpenCode runtime

Package the necessary OpenCode runtime and dependencies inside the Android application.

The Android app launches/manages the runtime internally.

The user never sees the runtime.

Investigate whether this is technically feasible on current Android versions.

If it is feasible, prefer this when it provides the most complete OpenCode compatibility.

### Option B — Managed backend

The Android app connects to a managed backend where OpenCode runs.

The user does not manage the server.

The backend owns:

- OpenCode runtime
- workspace
- agent
- tools
- model connections
- sessions

The Android app becomes a native client.

If this is the only architecture that can realistically preserve full OpenCode functionality, use it.

However, do NOT introduce an unnecessary proprietary cloud dependency if a fully local architecture is realistically achievable.

### Option C — Hybrid

Use a local Android runtime when possible and a managed/remote runtime when necessary.

This can be considered if it provides meaningful advantages.

---

## 6. PRODUCT REQUIREMENT HAS PRIORITY OVER IMPLEMENTATION DETAILS

Do not force the product into an architecture just because the current implementation already uses an OpenCode server.

The current architecture is not sacred.

The product requirement is:

> "The user installs one Android application and does not need to manage infrastructure."

Refactor the existing application if necessary.

---

## 7. IF A BACKEND IS REQUIRED

If a managed backend is required for production:

The user experience must still be:

```
Install
→ Open
→ Sign in/create account
→ Chat
```

No manual server setup.

The app should automatically establish the required connection.

Do not expose:

- server URL
- port
- hostname
- SSH
- runtime configuration

to ordinary users.

Advanced developer settings may exist behind a developer mode, but normal users must never need them.

---

## 8. USER IDENTITY AND SESSIONS

If a managed backend is used, design proper account/session architecture.

Support:

- authentication
- secure token storage
- session persistence
- logout
- account recovery strategy where applicable
- multiple projects/workspaces
- secure communication
- server-side session isolation

Never put privileged backend credentials inside the APK.

Never ship provider API keys inside the Android application.

Never embed OpenAI/Anthropic/Gemini secret keys in the APK.

---

## 9. PROJECT/WORKSPACE MODEL

The application is fundamentally an agentic coding environment.

Therefore determine where the user's actual project exists.

Possible models:

**Local project**
Project exists on the Android device. OpenCode operates on it locally.

**Remote project**
Project exists in a managed workspace/server. OpenCode operates there.

**Connected development machine**
A developer can optionally connect a computer. But this must be OPTIONAL. The normal user must not need a computer.

Choose the architecture that provides the most complete and secure experience.

Document the decision.

---

## 10. LOCAL FILESYSTEM SUPPORT

If local Android projects are supported:

Investigate Android Storage Access Framework and modern Android filesystem restrictions.

Do not request unnecessary broad storage permissions.

Use appropriate scoped access.

If OpenCode requires capabilities that Android's normal sandbox does not provide, solve that at the runtime architecture level rather than pretending filesystem access is equivalent to desktop Linux.

---

## 11. BASH IS NOT OPTIONAL

The user explicitly wants the actual OpenCode experience.

Therefore Bash/tool execution must remain available wherever the chosen runtime architecture permits it.

Do not replace:

- bash
- filesystem
- git
- mcp

with fake Android-only approximations merely because they are easier.

If full Bash execution is impossible inside Android's normal sandbox, document the actual limitation and select the architecture that best preserves it.

---

## 12. GIT

Preserve real Git functionality.

Do not create a toy Git abstraction.

If the OpenCode runtime requires actual Git binaries/processes, investigate how to provide them securely in the chosen architecture.

The user should not manually install Git.

---

## 13. MCP

Preserve OpenCode's actual MCP capabilities wherever the selected runtime architecture supports them.

Do not replace MCP with a custom unrelated plugin system.

Do not pretend MCP works if the runtime cannot actually execute it.

---

## 14. MODEL PROVIDERS

Do not hard-code API keys.

The architecture should support real provider configuration.

Depending on the chosen architecture, credentials may be:

- user-provided and securely stored
- managed by the backend
- OAuth-based
- configured through OpenCode's existing configuration

Choose the safest production approach.

The user should not need to configure a dozen environment variables.

---

## 15. OPEN CODE SERVER MUST BECOME AN INTERNAL DETAIL

If OpenCode still runs as a server internally, the Android user must not have to know.

The application should manage the lifecycle automatically.

Conceptually:

```
Android App
↓
Internal OpenCode Runtime / Managed OpenCode Runtime
↓
OpenCode Agent
↓
Tools / Models / Project
```

If a local runtime is feasible:

- app starts it automatically
- app monitors it
- app reconnects automatically
- app stops/cleans it when appropriate
- app handles crashes
- app handles process death
- app restores sessions

The user sees none of this.

---

## 16. ZERO-CONFIG FIRST RUN

The first launch must NOT look like a developer setup wizard.

Do not show:

> "Enter OpenCode server URL"

unless the application is explicitly in advanced/developer mode.

Instead show a polished consumer-style onboarding.

Example:

```
Welcome to OpenCode

Your agentic coding workspace.

[Continue]
```

Then:

```
Sign in
```

or, if local operation is genuinely possible:

```
Create your first workspace
```

The goal is the same experience quality as ChatGPT/Gemini/Claude.

---

## 17. NORMAL USER EXPERIENCE

The normal flow should be:

1. Install APK.
2. Open.
3. Authenticate if required.
4. See home.
5. Create/open workspace.
6. Start chat.
7. Agent works.

No terminal. No setup commands. No shell. No server configuration. No runtime management.

---

## 18. ADVANCED USER EXPERIENCE

Power users may optionally access:

- runtime status
- server diagnostics
- connection details
- OpenCode version
- logs
- advanced configuration
- custom server connection

But these must be hidden behind:

**Settings → Developer / Advanced**

Never expose them in the normal UX.

---

## 19. CHAT EXPERIENCE

Preserve the previous requirements.

The primary interface should feel like a premium AI assistant.

The user sends:

> "Fix the authentication bug."

The agent can internally:

- inspect files
- search
- run Bash
- edit
- run tests
- inspect Git
- use MCP
- ask questions
- request permissions

The user sees this elegantly.

---

## 20. AGENT ACTIVITY

Render real OpenCode activity:

- thinking/progress states where exposed
- file reads
- searches
- Bash
- edits
- Git
- MCP
- tests
- permissions
- questions
- completion

Use expandable cards.

Do not hide the fact that the agent is operating a real environment.

---

## 21. PERMISSIONS

Preserve OpenCode's actual permission model.

When required:

```
OpenCode wants to execute

"npm install"

[Reject]  [Allow]
```

Never bypass this.

Never auto-approve dangerous operations just to make the app feel seamless.

---

## 22. SECURITY MODEL

Because this application can execute code and manipulate files, treat it as a high-risk developer application.

Implement:

- secure authentication
- encrypted credential storage
- TLS
- certificate validation
- no secrets in source
- no secrets in logs
- least privilege
- explicit permissions
- secure backend isolation if remote
- workspace isolation
- secure session handling
- safe process management
- safe file access

If remote execution is used, assume the server is a powerful code execution environment and design isolation accordingly.

---

## 23. BACKGROUND EXECUTION

Agent tasks may run for a long time.

Design for:

- Android background restrictions
- reconnect
- notifications
- foreground service only when genuinely necessary
- process death
- app reopening
- session restoration

The user should be able to leave the app and return later to:

> "Agent finished the task."

Do not pretend Android allows unlimited background execution.

Use the correct Android mechanisms.

---

## 24. NOTIFICATIONS

Provide useful notifications for long-running tasks.

Examples:

```
OpenCode
Authentication refactor completed.

OpenCode
Permission required.

OpenCode
Agent encountered an error.
```

Tapping the notification should return directly to the relevant session.

---

## 25. NO TERMUX EXPERIENCE

This is a hard requirement.

The final product must NOT require:

```
pkg install ...
npm install ...
bun install ...
opencode
opencode serve
chmod ...
export ...
ssh ...
```

The user should never have to see any of this.

If the architecture still requires these commands, the architecture is NOT finished.

---

## 26. NO "TERMUX WITH A GUI"

The application must not merely:

- launch Termux
- send commands to Termux
- wrap Termux
- launch a terminal activity
- require Termux:API
- depend on a user-managed Linux environment

This would violate the product requirement.

---

## 27. BUILD/DISTRIBUTION

The final application must be distributable as a normal Android application.

Prefer:

- standard APK
- standard AAB
- Play Store-compatible architecture where possible
- no special installation environment

If native binaries/runtime components are required, package them appropriately.

Investigate:

- ABI support
- arm64-v8a
- app size
- dynamic delivery if appropriate
- native library packaging
- startup performance
- Android security restrictions

---

## 28. ARCHITECTURE DOCUMENT

Before implementing the new architecture, create:

**`docs/ARCHITECTURE.md`**

Explain:

- current architecture
- problems with current architecture
- chosen zero-setup architecture
- why it was selected
- OpenCode runtime placement
- project/workspace location
- networking
- authentication
- security
- process lifecycle
- background execution
- model/provider handling
- filesystem
- Bash
- Git
- MCP
- sessions
- event streaming
- limitations

This document must reflect the actual implementation.

---

## 29. DO NOT DELETE WORKING FEATURES

Before refactoring, inventory the existing features.

Preserve everything that already works:

- sessions
- chat
- streaming
- tools
- permissions
- questions
- files
- diffs
- Git
- model selection
- MCP
- settings
- notifications
- event handling

Refactor rather than blindly rewrite.

---

## 30. TEST THE REAL PRODUCT

Do not merely run unit tests.

The final validation should answer:

**Fresh user test**
If I give the APK to a person who knows nothing about OpenCode: Can they install it and use the agent without installing anything else?

**Restart test**
Can they kill the app and reopen it?

**Network test**
Can they switch networks and recover?

**Long task test**
Can an agent run a long task and finish correctly?

**Tool test**
Can Bash execute? Can files be read? Can files be edited? Can Git work? Can MCP work where supported?

**Permission test**
Can the user approve/reject dangerous actions?

**Session test**
Can the user resume previous work?

**Security test**
Are secrets protected?

**Upgrade test**
Can the application update without destroying user sessions/workspaces?

---

## 31. FINAL ACCEPTANCE CRITERIA

The project is NOT complete until:

- [ ] Android app builds.
- [ ] APK installs normally.
- [ ] No Termux dependency exists.
- [ ] No manual OpenCode installation is required.
- [ ] No manual Node/Bun/Python installation is required.
- [ ] No manual Git installation is required.
- [ ] No manual "opencode serve" command is required.
- [ ] No manual server startup is required.
- [ ] No manual IP/port setup is required for normal users.
- [ ] User can open the app and start using it.
- [ ] Real OpenCode agent is used.
- [ ] OpenCode agent loop is preserved.
- [ ] Bash/tool execution is preserved where supported.
- [ ] Filesystem access is preserved where supported.
- [ ] Git is preserved where supported.
- [ ] MCP is preserved where supported.
- [ ] Sessions work.
- [ ] Streaming works.
- [ ] Permissions work.
- [ ] Agent questions work.
- [ ] Tool activity is visible.
- [ ] Diff viewer works.
- [ ] Long-running tasks work.
- [ ] Background/reconnect behavior is handled.
- [ ] Notifications work where appropriate.
- [ ] Credentials are secure.
- [ ] No production mock AI behavior exists.
- [ ] No fake OpenCode features exist.
- [ ] Architecture is documented.
- [ ] Real-device testing is performed where possible.

---

## 32. IMPORTANT DECISION RULE

If you discover that fully embedding the complete OpenCode runtime inside Android would significantly break or remove OpenCode functionality, DO NOT force it merely to avoid a backend.

Instead choose the architecture that provides the closest possible experience to ChatGPT while preserving the real OpenCode agent.

The user does not care where the runtime lives.

The user cares that:

> "They install one app and OpenCode simply works."

The infrastructure is an implementation detail.

---

## 33. FINAL PRODUCT DEFINITION

The final product should feel like:

**ChatGPT**
- install
- open
- chat

but underneath:

**OpenCode**
- real agent
- real tools
- real Bash
- real filesystem
- real Git
- real MCP
- real permissions
- real sessions
- real models
- real agentic execution

The user should never need to know or care how that infrastructure is implemented.

---

## 34. EXECUTION INSTRUCTIONS

Do this in order:

**Phase 1 — Audit**
Inspect the complete existing project.

**Phase 2 — Runtime investigation**
Inspect current OpenCode source/docs/API/runtime requirements.

**Phase 3 — Architecture decision**
Choose embedded, managed backend, or hybrid based on actual technical feasibility.

**Phase 4 — Architecture documentation**
Write "docs/ARCHITECTURE.md".

**Phase 5 — Refactor**
Change the existing project to support the zero-setup architecture.

**Phase 6 — Implement**
Implement all required runtime lifecycle, connection, security, workspace, background, and UI changes.

**Phase 7 — Test**
Build and run every test available. Perform real-device validation if possible.

**Phase 8 — Final audit**
Audit the final application against every acceptance criterion. Fix all issues found.

Do not declare success merely because compilation succeeds.

---

## 35. FINAL REPORT

At the end, report:

**Architecture chosen**
Explain exactly where the OpenCode runtime runs and why.

**User experience**
Explain exactly what a fresh user does from APK installation to first agent task.

**OpenCode compatibility**
List what remains fully supported.

**Limitations**
List anything that cannot currently be supported and why.

**Security**
Explain credential and execution security.

**Build**
Give exact commands to produce the APK/AAB.

**Test results**
Report actual tests performed.

**Remaining blockers**
List only genuine blockers.

Do not claim "production ready" if important parts could not be verified.

---

## MOST IMPORTANT RULE

Do not confuse:

> "The app can technically connect to OpenCode"

with:

> "The product is finished."

The product is finished only when a normal user can install it and use OpenCode without becoming a Linux administrator.

The desired experience is:

> "Install APK → Open → Chat → OpenCode does the rest."

Build that.
