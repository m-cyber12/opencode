# OpenCode Gateway — open contract

The gateway is the thin service that turns `opencode serve` into a zero-setup product. It is a
**specification, not proprietary infrastructure**: anyone can implement it (a weekend of nginx +
containers) and point this app at it via *Settings → Developer → Gateway* or
`OPCODE_GATEWAY_URL` at build time.

## Responsibilities

1. **Accounts** — email/password auth; opaque bearer tokens.
2. **Workspace CRUD** — each workspace is an isolated Linux environment running one real
   `opencode serve` instance for exactly one user.
3. **Proxying** — every workspace exposes its OpenCode HTTP/SSE API at an https endpoint;
   the gateway forwards traffic 1:1 with the user's bearer token attached/validated.

## Endpoints

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/auth/register` | – | `{email, password}` | `{token, email}` |
| POST | `/auth/login` | – | `{email, password}` | `{token, email}` |
| GET | `/workspaces` | Bearer | – | `[{id, name, endpoint, status, createdAt}]` (`status`: creating\|running\|stopped\|error) |
| POST | `/workspaces` | Bearer | `{name}` | `{id, name, endpoint, status, createdAt}` |
| DELETE | `/workspaces/{id}` | Bearer | – | any 2xx |

Errors: conventional status codes (`401` invalid/expired token → app signs the user out).

## Workspace contract (the part that preserves "real OpenCode")

- `endpoint` MUST serve the standard OpenCode API (`/global/health`, `/session`, `/event`, …)
  over HTTPS.
- The environment MUST provide bash, git and normal POSIX tooling so agent tools behave exactly
  as on a developer machine.
- Workspaces are single-tenant: no cross-workspace filesystem/network access.
- The gateway MUST NOT inject model provider keys; users configure providers inside their
  workspace through OpenCode's own auth flows.

## Reference implementation sketch

```
auth service (JWT/opaque tokens)
        │
router ──┼──► container pool (e.g. Firecracker/gVisor/Docker, per-user)
         │       └─ image: debian + node/bun + opencode + git + ripgrep
         └──► websocket-free reverse proxy /ws/{id}/* → container:4096
```

Idle policy: containers may stop after inactivity; `GET /workspaces` reports `stopped` and the
first request wakes them (`creating` → `running`).

## Why not embed OpenCode in the APK?

Documented with evidence in `docs/ARCHITECTURE.md` §2 (Android W^X execution policy, unsupported
Bun-on-Android targets, missing coreutils). The gateway keeps the agent 100% real instead of
shipping a silently crippled imitation.
