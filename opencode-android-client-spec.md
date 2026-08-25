# Build a Production-Quality Native Android Client for OpenCode

You are an expert Android engineer, product designer, distributed-systems engineer, and AI-agent UX designer.

Your task is to build a complete, polished, production-quality native Android application that acts as a full-featured graphical client for OpenCode.

This is NOT a new AI coding agent.

This is NOT a simplified OpenCode clone.

This is NOT a remote terminal wrapper.

The application must be a native Android UI for the existing OpenCode agent/server, preserving OpenCode's capabilities while replacing its terminal/TUI experience with a beautiful, modern, mobile-first interface similar in quality and interaction model to ChatGPT, Claude, and Gemini.

The guiding principle is:

«Change the interface. Do not change the agent.»

The user should feel like they are still using the exact same OpenCode agent, with the same tools, sessions, permissions, models, configuration, MCP integrations, filesystem access, shell execution, Git capabilities, and agent behavior — but through an excellent Android application.

---

## 1. First: Understand the Real OpenCode Architecture

Before writing application code, inspect the CURRENT official OpenCode documentation, source code, OpenAPI specification, and official SDK.

Do NOT rely on outdated knowledge.

OpenCode currently exposes a server/API architecture intended to allow external clients to interact with OpenCode programmatically.

The official documentation and SDK must be treated as the source of truth.

Relevant concepts include:

- OpenCode server
- HTTP API
- OpenAPI specification
- official TypeScript SDK
- sessions
- prompts/messages
- event streaming
- tool execution
- permissions
- questions/user interaction
- files
- Git/VCS
- projects
- configuration
- providers/models
- agents
- MCP
- TUI/server separation

The current official documentation indicates that OpenCode can run as a server and expose an HTTP/OpenAPI interface, with real-time server events through SSE.

Use the CURRENT API/schema rather than inventing an API.

If the API has changed since your training data, adapt to the currently documented implementation.

---

## 2. Product Vision

Build an Android application that feels like:

ChatGPT UX
+
Claude-quality conversation UX
+
Claude Code / OpenCode agentic behavior
+
Native Android design
+
Full OpenCode capability visibility

The application should hide the complexity of the underlying CLI when appropriate, but NEVER remove functionality.

The user should be able to:

- create and switch OpenCode sessions
- select projects
- send prompts
- see streaming assistant responses
- observe agent reasoning/progress where OpenCode exposes it
- observe tool calls
- inspect tool input
- inspect tool output
- approve/reject permissions
- answer agent questions
- cancel/abort running operations
- inspect changed files
- inspect diffs
- browse project files
- inspect Git/VCS information
- select/configure models when supported
- access agents
- access MCP-related functionality where exposed
- continue existing OpenCode sessions
- resume interrupted work
- manage multiple projects
- see connection/server status
- handle errors and reconnect gracefully

Do not invent fake AI behavior.

Everything involving the agent must be driven by actual OpenCode state/events/API responses.

---

## 3. Critical Architectural Rule

Do NOT reimplement the OpenCode agent.

Do NOT create a second agent loop.

Do NOT create a separate bash implementation.

Do NOT directly call OpenAI/Anthropic/Gemini from the Android client as a replacement for OpenCode.

Do NOT duplicate OpenCode's filesystem, Git, MCP, permissions, or tool systems.

OpenCode remains the source of truth.

The Android application is a client/presentation layer.

Preferred conceptual architecture:

Android UI
↓
OpenCode Client / Transport Layer
↓
OpenCode Server
↓
OpenCode Agent + Tools + Models + Project Environment

The UI must communicate with OpenCode through its supported server/API interface.

---

## 4. Android Technology

Use a modern native Android stack.

Preferred:

- Kotlin
- Jetpack Compose
- Material 3
- Kotlin Coroutines
- Flow / StateFlow
- Navigation Compose
- ViewModel
- Repository pattern
- clean modular architecture where justified
- Gradle Kotlin DSL

Do NOT use old XML-based Android UI unless there is a compelling technical reason.

The UI must be genuinely native Android, not a WebView pretending to be an Android application.

Avoid unnecessary dependencies.

Every dependency must have a reason.

---

## 5. Connection Architecture

The Android app needs a robust OpenCode connection layer.

Implement a clean abstraction such as:

OpenCodeClient
OpenCodeRepository
SessionRepository
EventRepository
ProjectRepository

The exact structure is your architectural decision.

The client must support:

- HTTP requests
- SSE/event streaming
- authentication where required
- reconnect
- connection state
- timeout handling
- cancellation
- malformed event handling
- server version/health information
- graceful degradation

Do not hard-code assumptions about localhost.

An Android phone cannot normally access a desktop's "127.0.0.1".

The application must therefore be designed around a configurable remote OpenCode server endpoint.

Support at minimum:

- server URL
- authentication credentials/token/password as supported by the current OpenCode API
- connection testing
- secure credential storage

Use Android Keystore / secure storage for secrets.

Never log credentials.

Never expose API keys in UI logs.

---

## 6. Event-Driven Architecture

This is one of the most important parts of the project.

Do NOT build the chat UI around polling.

OpenCode exposes real-time events.

Build an event-driven state layer.

Conceptually:

OpenCode SSE
↓
Event parser
↓
Event mapper
↓
Session state reducer
↓
StateFlow
↓
Compose UI

The UI should react to actual OpenCode events.

Handle all relevant event categories exposed by the current API.

At minimum, investigate and support events related to:

- server connection
- session creation/update/status
- message creation/update
- streaming message parts
- tool execution
- tool output
- file changes
- permissions
- questions
- errors
- completion
- abort/cancellation

Do not create a fake event taxonomy if the OpenCode API already provides structured events.

Map the real OpenCode event model into a clean internal UI state model.

Unknown future events should NOT crash the application.

Unknown events should be safely ignored or surfaced through a generic event representation.

---

## 7. Chat UI

The primary screen must feel like a premium AI chat application.

It should NOT look like a terminal emulator.

However, it must expose the underlying agent activity elegantly.

Design for:

- excellent typography
- generous spacing
- dark/light themes
- smooth streaming
- markdown rendering
- code blocks
- syntax highlighting
- copy button
- message actions
- retry
- regenerate/resend where meaningful
- scroll-to-bottom
- unread/new-output indicator
- auto-scroll while appropriate
- manual scroll preservation
- long conversation performance

The input area should support:

- multiline input
- send
- stop/cancel
- attachments if realistically supported
- project/file references if supported
- model/agent selection where appropriate
- command shortcuts if appropriate

Do not clutter the composer.

---

## 8. Agent Activity UI

This is the most important UX differentiation.

When OpenCode performs actions, render them as elegant expandable activity cards.

Examples:

**File search**

Search codebase
"src/**/*.kt"
12 results

**File read**

Reading:
"src/main/.../AuthRepository.kt"

**Bash**

Running:
"./gradlew test"
Output available

**Edit**

Modified:
"AuthRepository.kt"
+18 / -7

**Git**

"git diff"
3 files changed

The cards should be:

- compact by default
- expandable
- copyable
- scrollable
- syntax-aware where appropriate
- visually distinct from normal chat messages

Do NOT hide tool activity completely.

The user must be able to understand what the agent is doing.

---

## 9. Full Tool Transparency

The application must preserve OpenCode's agentic nature.

For every tool execution where OpenCode exposes structured information, provide access to:

- tool name
- tool status
- arguments/input
- output
- errors
- duration if available
- affected files if available

Never fabricate output.

If structured information is unavailable, display the raw information safely.

The UI should make it feel like:

"the agent is operating the environment"

rather than:

"the chatbot magically generated some text."

---

## 10. Permissions

Permissions are a first-class feature.

If OpenCode asks for permission, interrupt the visual flow with a clear native approval UI.

Example:

Agent wants to run:
"git reset --hard HEAD"

Buttons:
[Reject] [Allow]

For destructive or sensitive operations, make the consequences obvious.

Support the complete permission flow exposed by the current OpenCode API.

Do not bypass permissions.

Do not automatically approve operations unless OpenCode itself has already configured them as allowed.

---

## 11. Agent Questions

OpenCode may need information from the user.

When the agent asks a question:

Show a native, polished question interface.

Examples:

"Which authentication provider should I use?"
[Google] [GitHub] [Email]

or:

"Should I modify the database schema?"
[Yes] [No]

Support whatever structured question mechanism the current OpenCode API exposes.

Do not force the user to return to a terminal.

---

## 12. Session Management

Sessions are fundamental.

Build a session system that supports:

- new session
- existing sessions
- session list
- session switching
- session resume
- session status
- session title/name when available
- session timestamps
- running/idle/error state
- project association
- session deletion where supported
- session abort

The sidebar/session drawer should feel similar to a high-quality ChatGPT/Claude conversation list.

Example:

Recent
- Fix authentication — Today
- Refactor API client — Yesterday
- Implement dark mode — Monday

Do not create a separate local conversation system that becomes the source of truth.

OpenCode sessions remain authoritative.

---

## 13. Projects / Workspaces

Users must be able to work with multiple OpenCode projects.

Provide a project/workspace selector.

Display:

- project name
- directory/path where appropriate
- VCS information where available
- current session
- connection status

Do not assume one project per server unless the current OpenCode architecture requires it.

Use the current official API to understand project semantics.

---

## 14. File Browser

Provide a native file browser for the active project.

Features:

- directory navigation
- file list
- search
- open file
- syntax highlighting
- copy
- file metadata where available
- safe handling of large files
- loading states
- errors

Do not attempt to download the entire project into Android storage just to implement browsing.

Use OpenCode's file APIs where available.

---

## 15. Diff Viewer

Implement a beautiful mobile diff viewer.

It must support:

- modified files
- additions
- deletions
- context
- file names
- expandable hunks
- horizontal scrolling where necessary
- syntax-aware display where possible
- copy

Prefer a unified diff presentation optimized for mobile.

If OpenCode exposes structured file-change information, use it.

---

## 16. Git / VCS

Expose Git/VCS information where OpenCode provides it.

At minimum investigate support for:

- current branch
- changed files
- diff
- status
- repository information

Do NOT reimplement Git operations unnecessarily.

If an operation is supported by OpenCode, use OpenCode.

---

## 17. Models and Agents

The app must not assume a single model.

Where OpenCode exposes model/provider/agent information, surface it.

Support:

- current model
- model selection
- provider
- agent selection
- relevant configuration
- model switching between sessions/turns where supported

The UI should make model selection simple.

Example:

Claude / GPT / Gemini / Local model / Custom provider

But these must be generated from actual OpenCode provider/model data rather than hard-coded fake entries.

---

## 18. MCP and Extensibility

Do not build a custom competing MCP system.

Preserve OpenCode's existing MCP capabilities.

If the current API exposes MCP state/configuration/tools, provide an appropriate UI.

If some MCP functionality is not exposed through the public client API, do not fake it.

Architect the app so future OpenCode API additions can be integrated without rewriting the UI.

---

## 19. OpenCode Commands

OpenCode has concepts beyond plain natural-language prompts.

Inspect the current API and documentation for command support.

Where appropriate, expose commands through a command palette or slash-command UI.

The command UI should feel native.

Do not force users to type obscure CLI syntax when a UI control is more appropriate.

However, provide an advanced command/terminal mode for power users.

---

## 20. Advanced Terminal View

The app is NOT a terminal emulator.

But power users must not lose access to terminal-oriented workflows.

Provide an optional advanced view where OpenCode command/tool activity can be inspected in a terminal-like presentation.

This is a secondary interface, not the primary UI.

The primary experience remains chat + agent activity.

---

## 21. Mobile UX

Design specifically for Android phones.

Do NOT simply shrink a desktop interface.

Support:

- portrait
- landscape where useful
- small phones
- large phones
- tablets where practical
- keyboard appearance/disappearance
- IME insets
- gesture navigation
- back navigation
- rotation
- background/foreground transitions
- network changes

Avoid tiny buttons.

Touch targets must be accessible.

Bottom sheets are preferred for secondary actions.

Use full-screen views for complex content such as:

- file viewer
- diff viewer
- terminal output
- settings

---

## 22. Offline / Reconnection Behavior

The app cannot assume a perfect network.

Implement:

- connection state indicator
- automatic SSE reconnect
- exponential backoff
- duplicate event protection where necessary
- state resynchronization after reconnect
- graceful request failures
- user-visible connection errors
- retry actions

When reconnecting, do not blindly append duplicate messages/events.

The app should be able to recover from:

- phone sleeping
- Wi-Fi switching
- temporary server outage
- server restart
- process death
- app backgrounding

---

## 23. Security

Treat this as a serious remote-control client.

Never:

- store credentials in plaintext
- print secrets to logs
- expose credentials in crash reports
- disable TLS verification
- silently trust arbitrary certificates
- bypass OpenCode permission systems

Use secure storage.

Warn users when connecting to insecure HTTP servers unless explicitly configured.

Provide a clear server connection/security state.

---

## 24. Performance

The app may receive very large streams of:

- assistant output
- tool output
- terminal output
- file contents
- diffs

Do not render everything naively into one giant Compose tree.

Use:

- lazy lists
- incremental state updates
- stable keys
- efficient diff rendering
- output truncation with "show more"
- memory-conscious parsing
- proper coroutine cancellation

A 30-minute coding session should remain responsive.

---

## 25. State Architecture

Design a proper unidirectional state flow.

A reasonable conceptual model:

ServerConnectionState
ProjectState
SessionState
MessageState
ToolExecutionState
PermissionState
QuestionState
FileState
DiffState
ModelState
UIState

Use immutable UI state where practical.

Do not let random Composables directly perform network requests.

The UI should be a renderer of state.

---

## 26. Error Handling

Every network/API operation needs robust error handling.

Errors should be:

- categorized
- logged safely
- recoverable where possible
- understandable to the user

Example:

Instead of:
"HTTP 500"

show:
"OpenCode server returned an internal error." [Retry]

But retain technical details in an expandable diagnostics section.

---

## 27. Visual Design

The design must be premium.

Do NOT produce generic AI-generated app UI.

Avoid:

- excessive gradients
- meaningless glowing effects
- giant rounded rectangles everywhere
- random purple/blue AI aesthetics
- excessive shadows
- clutter
- desktop-like tiny controls

Aim for:

- calm
- minimal
- professional
- developer-focused
- premium
- excellent typography
- subtle animation
- strong hierarchy

The app should feel like a serious developer product.

Use Material 3 principles but do not make the application look like a default Material template.

---

## 28. Themes

Support:

- dark theme
- light theme
- system theme

The dark theme should be particularly polished because developers will likely use it heavily.

Code blocks, diffs, tool cards, terminal output, and chat content must remain readable in both themes.

---

## 29. Animations

Use subtle animations for:

- streaming responses
- tool start/finish
- expanding tool cards
- navigation
- permission prompts
- new message arrival
- connection state

Do not animate everything.

Animations must communicate state, not decoration.

---

## 30. Accessibility

Support:

- content descriptions
- semantic labels
- screen readers
- sufficient contrast
- scalable text
- keyboard navigation where relevant
- touch targets

Do not sacrifice accessibility for visual design.

---

## 31. Architecture for Future OpenCode Changes

This is critical.

OpenCode is actively evolving.

Do not tightly couple every Compose component to raw OpenCode API types.

Create an internal domain model layer.

Conceptually:

OpenCode API models
↓
API mapper
↓
Domain models
↓
UI state
↓
Compose

This allows OpenCode API changes to be handled primarily in the integration layer.

Unknown API fields/events must not crash the app.

---

## 32. Version Compatibility

At application startup:

1. Connect to OpenCode.
2. Check server health/version if available.
3. Determine supported API capabilities.
4. Gracefully disable unsupported features.
5. Never pretend a feature exists when the connected OpenCode version does not support it.

If possible, implement lightweight capability detection.

For example:

supportsModels
supportsQuestions
supportsPermissions
supportsFileRead
supportsDiff
supportsMcp
supportsCommands

Do not hard-code version checks when capability detection is possible.

---

## 33. Do Not Make These Mistakes

Absolutely do NOT:

1. Build a fake AI chatbot disconnected from OpenCode.
2. Reimplement the agent loop.
3. Call model APIs directly instead of using OpenCode.
4. Invent fake tool events.
5. Replace OpenCode's permission system.
6. Create a fake local conversation database as the authoritative source.
7. Assume localhost is reachable from Android.
8. Use polling instead of SSE when streaming events are available.
9. Hard-code providers/models.
10. Hard-code OpenCode API responses.
11. Assume the current API will never change.
12. Create a WebView application.
13. Make the UI a terminal emulator as the primary experience.
14. Hide important agent activity.
15. Lose tool output.
16. Silently swallow errors.
17. Store secrets insecurely.

---

## 34. Project Structure

Choose the best structure, but maintain clear separation between:

- UI
- navigation
- presentation/state
- domain
- OpenCode transport
- API models
- mappers
- repositories
- secure storage
- utilities

Avoid overengineering.

The architecture should be sophisticated enough for a real product but not filled with abstractions that have no purpose.

---

## 35. Testing

Do not stop after the UI compiles.

Implement:

**Unit tests** for:

- event parsing
- event mapping
- state reducers
- session state
- connection state
- error handling
- permission handling
- message streaming
- reconnection

**Integration tests**

Test the OpenCode client against realistic API responses/mocks.

**UI tests**

Test:

- sending message
- streaming message
- tool execution rendering
- permission dialog
- question dialog
- session switching
- file browsing
- diff rendering
- connection loss/reconnect

---

## 36. Mock / Demo Mode

Create a development/demo mode.

This must use simulated OpenCode events and allow the entire UI to be previewed without a live server.

Include realistic scenarios:

1. normal chat
2. long streaming response
3. tool execution
4. bash output
5. file modification
6. permission request
7. question
8. error
9. reconnect
10. large diff

But keep mock mode clearly separated from production OpenCode integration.

---

## 37. UX Flow

The ideal first-run flow:

1. Launch app.
2. Beautiful welcome screen.
3. "Connect to OpenCode".
4. Enter server URL.
5. Authenticate if required.
6. Test connection.
7. Load projects.
8. Select project.
9. Show sessions.
10. Create or continue a session.
11. Start chatting.

Returning user:

Launch → last project → last session → immediately continue work.

---

## 38. Main Screens

Implement at least:

1. Home / Projects
2. Session list
3. Chat / Agent workspace
4. Project file browser
5. File viewer
6. Diff viewer
7. Tool detail
8. Permission request
9. Agent question
10. Model/provider selector
11. Settings
12. Server connection management
13. Advanced terminal/activity view

These screens should feel like one coherent product, not unrelated templates.

---

## 39. Settings

Provide:

- OpenCode server connections
- active server
- security state
- theme
- appearance
- notifications where appropriate
- behavior preferences
- developer/debug information
- reconnect behavior
- advanced settings

Do not expose dangerous low-level settings unless they are actually supported by OpenCode.

---

## 40. Notifications

If technically and architecturally appropriate, support notifications for long-running agent tasks.

For example:

"OpenCode finished working on 'Fix authentication'."

But do not claim background execution is guaranteed if Android restrictions prevent it.

Design background behavior realistically.

---

## 41. Deliverables

You must produce a complete Android Studio project.

It must:

- build successfully
- launch successfully
- contain polished UI
- contain real OpenCode integration
- contain SSE/event streaming
- contain session management
- contain tool rendering
- contain permissions
- contain questions
- contain file browsing
- contain diff rendering
- contain connection management
- contain error handling
- contain secure credential handling
- contain tests
- contain demo/mock mode
- contain documentation

Do not leave critical functionality as TODO placeholders.

Do not provide pseudocode for core functionality.

Implement it.

---

## 42. Documentation

Create a clear README explaining:

- what the app is
- architecture
- how OpenCode integration works
- supported OpenCode versions/capabilities
- how to run OpenCode server
- how to configure the Android app
- how to connect a phone
- authentication/security considerations
- development setup
- testing
- known limitations

Also document any OpenCode API assumptions.

---

## 43. Implementation Strategy

Before coding:

1. Inspect current official OpenCode docs.
2. Inspect current official OpenCode SDK.
3. Inspect OpenAPI schema.
4. Identify all relevant endpoints.
5. Identify event types.
6. Identify session/message/tool/permission/question flows.
7. Identify authentication requirements.
8. Identify which features are actually exposed publicly.
9. Design the integration layer.
10. Design UI state.
11. Then implement.

Do not start by building random screens.

---

## 44. Important Constraint: Preserve OpenCode

The Android application must remain a thin but powerful client.

If OpenCode adds:

- a new tool
- a new event
- a new model provider
- a new agent
- a new MCP capability
- a new session feature

the architecture should make it possible to support it without rewriting the application.

Prefer generic renderers for unknown/future tool events where appropriate.

For example:

GenericToolCard
- toolName
- status
- input
- output
- metadata

Then add specialized renderers for known tools:

BashToolCard
FileToolCard
EditToolCard
GitToolCard
McpToolCard

Unknown tools should still have a useful generic representation.

---

## 45. Product Quality Bar

Do not optimize for:

"it technically works."

Optimize for:

"I would actually use this every day."

The final app should feel closer to a polished first-party developer product than an AI-generated prototype.

No broken navigation.

No placeholder screens.

No fake buttons.

No dead-end flows.

No crashes when events arrive unexpectedly.

No ugly default components.

No unnecessary setup.

---

## 46. Final Acceptance Criteria

The project is considered complete only if all of these are true:

- [ ] Android app builds successfully.
- [ ] App launches successfully.
- [ ] User can connect to a real OpenCode server.
- [ ] User can authenticate when required.
- [ ] User can select a project.
- [ ] User can create a session.
- [ ] User can resume an existing session.
- [ ] User can send prompts.
- [ ] Responses stream correctly.
- [ ] OpenCode events are consumed through the proper event mechanism.
- [ ] Tool execution is visible.
- [ ] Tool input/output can be inspected.
- [ ] Bash activity is visible.
- [ ] File activity is visible.
- [ ] File changes are visible.
- [ ] Diff viewer works.
- [ ] Permissions work.
- [ ] Agent questions work.
- [ ] Sessions can be switched.
- [ ] Connection loss is handled.
- [ ] Reconnection is handled.
- [ ] Unknown events do not crash the app.
- [ ] Credentials are securely stored.
- [ ] Dark mode is polished.
- [ ] Light mode works.
- [ ] UI works on real Android devices.
- [ ] Large outputs do not freeze the UI.
- [ ] Tests exist for critical integration/state logic.
- [ ] Demo mode works.
- [ ] README is complete.
- [ ] No core functionality is left as a TODO.

---

## 47. Most Important Instruction

You are not being asked to create a mockup.

You are not being asked to create a proof of concept.

You are not being asked to create a simplified chatbot.

You are building the first production-quality version of the application.

Treat this as a real software product.

Make strong engineering decisions yourself when details are unspecified.

Do not ask unnecessary questions.

If there are multiple reasonable implementation choices, choose the most robust, maintainable, Android-native option.

If an OpenCode feature cannot currently be accessed through its public API, document the limitation and architect the application so that it can be added later.

Never fake support for an unavailable feature.

Before declaring completion, build, test, inspect, and fix the application.

The final result should be:

«OpenCode, exactly as powerful as OpenCode, experienced through a beautiful native Android client.»

Begin by inspecting the current OpenCode API, SDK, OpenAPI schema, and event model. Then implement the complete application.
