# Security Review

Date: 2026-07-07

Scope: harness invocation, shell observers/acts, git rollback, config loading/hot-reload, notifier transport, operator controls, release artifact handling.

## Findings

| Area | Result | Notes |
|---|---|---|
| Config execution | Acceptable for trusted local configs | EDN is parsed as data and predicates are interpreted; no `eval`. Shell observers/acts are intentionally executable and must be treated as trusted local automation. |
| Harness invocation | Acceptable | Built-in harnesses render argv vectors and `opencode` is not invoked through shell interpolation. `:shell-argv` explicitly runs `bash -lc` and is documented as a local command harness. |
| Harness self-report | Good | Tests prove harness stdout claiming success is advisory; Axiom only trusts re-observed world state. |
| Git rollback | Good with caveat | In git-backed workspaces rollback uses `git reset --hard <tag>` and disposable dogfood proved poisoned file restoration. Non-git workspaces degrade to bounded retry. |
| Hot-reload | Good | Changed configs are re-read and validated before swap; loop-owned fields remain fixed. |
| Notifier | Hardened | HTTP notifier config now requires `https://` URLs. Custom `:transport` remains test/programmatic extension and is trusted code. |
| Operator controls | Acceptable | Pause/resume/stop are simple files under `<workdir>/.axiom-control`; no daemon socket or remote control surface. |
| Release artifact | Good | `.sha256` verified and package smoke tested. Checksum must be verified from artifact directory because the file stores a basename. |

## Residual risks

- Axiom runs shell commands from config by design. Do not run untrusted configs.
- `:shell-argv` and shell acts are powerful local execution surfaces; use disposable/git-backed workspaces for dogfood.
- HTTP notifications can exfiltrate halt metadata by design; only configure trusted HTTPS endpoints.
- Rollback is only meaningful inside git workspaces with committed baselines.

## Action taken

- Added config validation rejecting `{:notify {:type :http :url "http://..."}}`.
- Added regression coverage for HTTPS notifier validation.
- Documented the security boundary and residual risks here and in `CONFIG.md`.
