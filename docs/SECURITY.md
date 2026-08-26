# Security Model

## Threat Model

| Asset | Threat | Mitigation |
|-------|--------|------------|
| Provider API keys | Extraction from device/storage | EncryptedSharedPreferences (Keystore AES256-GCM); keys only injected as process env at spawn; never written to disk by app |
| User code/workspace | Exfiltration by compromised agent | Agent runs in app UID; no network exposure (server on 127.0.0.1); SAF import/export user-controlled |
| Runtime binaries | Supply chain / tampering | SHA-256 pinned in `versions.lock`; verified at build (CI) and runtime (installer) |
| OpenCode server | Unauthorized access | Binds 127.0.0.1 only; cleartext allowed only to loopback via network security config; optional Basic auth via `OPENCODE_SERVER_PASSWORD` (not used by default) |
| PRoot tracees | Escape to host | PRoot ptrace containment; `--kill-on-exit`; no privileged syscalls; seccomp filter (when enabled) |
| Android OS | Privilege escalation | No root required; no `android:sharedUserId`; standard app sandbox |

## Credential Handling

```
User enters key in Settings
        │
        ▼
androidx.security.crypto.EncryptedSharedPreferences
   (MasterKey in Android Keystore → AES256-GCM)
        │
        ▼
SecureCredentials.snapshot() → Map<provider, key>
        │
        ▼
OPENCODE_AUTH_CONTENT = JSON string injected into PRoot env
        │
        ▼
OpenCode server reads env at startup → auth.json in guest ~/.local/share/opencode/
```

- **Never** logged (LogRingBuffer redacts `sk-`, `Bearer`, `api_key`, `ghp_` patterns).
- **Never** in APK, never in backup, never in plaintext `SharedPreferences`.
- **Only** in memory of app process and child PRoot process tree.

## Network Security

```xml
<!-- network_security_config.xml -->
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">127.0.0.1</domain>
    <domain includeSubdomains="false">localhost</domain>
</domain-config>
<base-config cleartextTrafficPermitted="false" />
```

- OpenCode server binds `127.0.0.1` exclusively (`--hostname 127.0.0.1`).
- No mDNS advertisement (`--mdns false` by default).
- CORS restricted to loopback origins + `oc://renderer`, `tauri://localhost`.

## Process Isolation

| Boundary | Mechanism |
|----------|-----------|
| App ↔ PRoot | Separate process tree; PRoot started via `ProcessBuilder` from foreground service |
| PRoot ↔ Guest binaries | ptrace syscall interception; `--kill-on-exit` ensures tracee cleanup |
| Guest ↔ Host FS | Bind mounts only (`/root`, `/projects`); rest of host FS invisible |
| Guest ↔ Network | Host kernel network stack; outbound allowed, inbound only via loopback |

## Runtime Integrity

1. **Build-time**: `versions.lock` pins every artifact SHA-256. CI verifies downloads.
2. **APK packaging**: `rootfs.tar.gz` + `rootfs.sha256` in assets. `verifyRuntimeAsset` task fails build if missing/mismatched.
3. **Install-time**: `RuntimeInstaller` recomputes SHA-256 of asset stream; compares to `.installed-sha256` marker.
4. **Upgrade**: Hash mismatch triggers full re-extraction (wipe old rootfs).
5. **Corruption**: Marker missing or binary missing → treated as corruption → re-extract.

## Permissions (Android Manifest)

| Permission | Reason |
|------------|--------|
| `INTERNET` | Model provider HTTPS + loopback to OpenCode server |
| `ACCESS_NETWORK_STATE` | Detect connectivity for UI |
| `FOREGROUND_SERVICE` | RuntimeService keeps agent alive |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |

**Not requested**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `NFC`, `INTERNET` (already listed).

## Known Attack Surface

| Vector | Status |
|--------|--------|
| Malicious OpenCode prompt → shell escape | PRoot contains tracees; guest bash limited to bound dirs |
| Compromised MCP server → host access | MCP stdio servers run as PRoot tracees; same containment |
| Malicious project import (SAF) → path traversal | `DocumentFile` API used; copy validates names; no symlinks followed |
| Side-channel (timing, cache) | Not mitigated; acceptable for local-only threat model |

## Compliance

- No telemetry, no analytics, no crash reporting to third parties.
- No cloud gateway, no remote logging.
- GDPR/CCPA: No personal data collected; all data stays on device.