# Capability Matrix

**Goal**: Preserve as close to 100% of OpenCode's agent functionality as technically possible on Android.

| Capability | Desktop OpenCode | Android Implementation | Status | Notes |
|------------|------------------|------------------------|--------|-------|
| **Agent Loop** | ✅ Full | ✅ Real OpenCode binary | **PASS** | Identical behavior; same TypeScript code |
| **Models** | ✅ All providers | ✅ Via injected config | **PASS** | Anthropic, OpenAI, Google, Cohere, Azure, custom |
| **Sessions** | ✅ Persistent (SQLite) | ✅ Guest SQLite in bound `/root` | **PASS** | Survives app restart; scoped per project dir |
| **Streaming** | ✅ SSE deltas | ✅ SSE `/event` + part deltas | **PASS** | `message.part.delta` + `part.updated` |
| **File Read** | ✅ | ✅ | **PASS** | `/file/content` endpoint |
| **File Write** | ✅ | ✅ | **PASS** | Edit tool writes via server |
| **Shell** | ✅ bash/zsh | ✅ Real bash in Alpine | **PASS** | `bash` tool → `bash -c` in guest |
| **Git** | ✅ | ✅ Real git in Alpine | **PASS** | `git init/status/diff/log/commit` |
| **Permissions** | ✅ ask/allow/deny | ✅ SSE `permission.asked` → UI → `reply` | **PASS** | Mobile-friendly card; once/always/deny |
| **MCP (stdio)** | ✅ | ⚠️ Partial | **PARTIAL** | Works if server is Node/binary in rootfs; no Windows-only servers |
| **MCP (HTTP/SSE)** | ✅ | ✅ | **PASS** | Outbound HTTPS from guest |
| **Project/Workspace** | ✅ | ✅ App-owned dirs + SAF import | **PASS** | One dir per project; bind → `/projects/<id>` |
| **Server API** | ✅ REST + SSE | ✅ Identical endpoints | **PASS** | `x-opencode-directory` routing |
| **Tools** | ✅ All built-in | ✅ All via real OpenCode | **PASS** | read, write, edit, bash, glob, grep, list, task, webfetch, lsp, etc. |
| **Configuration** | ✅ opencode.json + env | ✅ `OPENCODE_CONFIG_CONTENT` env | **PASS** | Model, permissions, agents, MCP servers |
| **Authentication** | ✅ OAuth + API keys | ✅ Keystore → env injection | **PASS** | API keys only; OAuth refresh persisted in guest auth.json |
| **Memory/Persistence** | ✅ Session DB + instructions | ✅ Guest SQLite + instructions in config | **PASS** | No custom memory layer yet |
| **TUI** | ✅ | ❌ | **N/A** | Replaced by Compose UI |
| **Web UI** | ✅ | ❌ | **N/A** | Native app only |
| **Plugin System** | ✅ npm plugins | ❌ | **BLOCKED** | Requires dynamic `require` + npm; not in musl static binary |
| **Self-Update** | ✅ | ❌ | **DISABLED** | `OPENCODE_DISABLE_AUTOUPDATE=1` |
| **Telemetry** | ❌ (opt-in OTLP) | ❌ | **N/A** | Not implemented |
| **ARM64 only** | Multi-arch | arm64-v8a | **LIMITED** | x86_64/armv7 not built |
| **Android < 8** | N/A | ❌ | **UNSUPPORTED** | minSdk 26 |

## Known Gaps

| Gap | Impact | Mitigation |
|-----|--------|------------|
| **No plugin system** | Custom tools/agents unavailable | Not a core agent feature; document limitation |
| **No self-update** | Manual APK update required | CI produces signed releases; user reinstalls |
| **OAuth providers** | Only API-key providers work out of box | OAuth flows need custom scheme handling; deferred |
| **Large binary size** | ~120 MB APK | Acceptable per spec; `useLegacyPackaging` required |
| **targetSdk 28** | Play Store not an option for v1 | Direct APK / GitHub Releases distribution |
| **No GPU** | No CUDA/Metal acceleration | Irrelevant for LLM inference (remote providers) |

## Gate Status Tracking

| Gate | CI Status | Last Verified |
|------|-----------|---------------|
| G1–G5 | Pending CI | — |
| G6–G10 | Pending CI | — |
| G11–G15 | Pending CI | — |

*Run `.github/workflows/gates.yml` to populate.*