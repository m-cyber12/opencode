# OpenCode API contract notes

What this client assumes about the OpenCode server, verified against the official docs
(`opencode.ai/docs/server`, `/docs/sdk`) and the generated SDK types
(`packages/sdk/js/src/gen/types.gen.ts`, branch `dev`) as of 2026-08.

## Transport & auth

| Concern | Assumption |
|---|---|
| Base | `opencode serve` exposes HTTP at `http://host:4096` by default |
| Auth | HTTP **basic** when `OPENCODE_SERVER_PASSWORD` is set; username defaults to `opencode` (`OPENCODE_SERVER_USERNAME` overrides) |
| Events | `GET /global/event` → SSE, frames are `{"directory": "...", "payload": {type, properties}}`; `GET /event` is the instance-scoped variant (we use global so multi-project works) |
| Instance scoping | Most endpoints accept `?directory=<worktree>` to address a project |
| Health | `GET /global/health` → `{healthy, version}` — used as the connect gate |

## Key calls used

- Sessions: list/create/delete/rename/statuses/todo/abort/share/unshare/diff/revert/unrevert,
  `POST /session/:id/prompt_async` (204; streaming arrives via events),
  `POST /session/:id/command` `{command, arguments, agent?}`,
  `POST /session/:id/shell` `{agent, command}`,
  **`POST /session/:id/permissions/:permissionID` body `{"response":"once"|"always"|"reject"}`**
- Messages: `GET /session/:id/message[?limit]` → `{info, parts}[]`
- Config/models: `/config/providers`, `/agent`, `/command`
- Files: `/file?path=`, `/file/content?path=`, `/file/status`, `/find?pattern=`, `/find/file?query=`
- Integrations: `/mcp`, `/lsp`, `/formatter`
- Projects/path/VCS: `/project*`, `/path`, `/vcs`

## Event taxonomy consumed

`server.connected`, `session.created|updated|deleted|status|idle|compacted|diff|error`,
`message.updated|removed`, `message.part.updated(+delta)|part.removed`,
`permission.updated|replied`, `todo.updated`, `file.edited`, `vcs.branch.updated`,
`command.executed`, `installation.*`, `pty.*`, `tui.*`, `lsp.*`.

Unknown event types map to a generic `Unknown` case: logged in the activity view, never fatal.
Adding support for a new server event = one new case in `opencode/event/OpenCodeEvent.kt`.

## Deliberate non-assumptions

1. **No polling.** Prompts go out via `prompt_async`; every token/tool arrives as events.
2. **No local conversation store.** The server's session list/messages are re-fetched on demand;
   the app keeps only UI state plus "last opened" pointers.
3. **No hard-coded providers/models/agents** anywhere in the UI — all from live payloads.
4. **Questions**: the public API has no dedicated question endpoint. Interactive asks surface as
   `permission.updated`; when its metadata contains option labels we render explicit choice
   buttons mapped onto the `once|always|reject` response enum. If upstream adds structured
   questions, they will slot into the same pinned-interaction slot above the composer.
5. **PTY** (`/pty*`) is experimental and unused; the advanced view uses supported `/shell`.
6. **Provider OAuth** (`/provider/*oauth/*`) is CLI/TUI-managed; not surfaced.

## Future-proofing hooks

- All DTOs tolerate unknown fields; missing fields default.
- Tool rendering: specialized cards for known tools + `GenericToolCard` fallback that shows raw
  input/output/metadata for anything new.
- Capability flags (`domain/Capabilities`) gate UI entry points so older/newer servers degrade
  gracefully rather than erroring.
