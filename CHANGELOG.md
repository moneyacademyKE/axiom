# Changelog

All notable changes to Axiom are documented here.

## v0.1.0 - 2026-07-07

Developer preview release of Axiom, a Babashka autonomous goal-fulfillment runner that supervises agents/harnesses by observing the real world instead of trusting self-report.

### Added

- Core OBSERVE -> DECIDE -> ACT loop with EDN config.
- Integrity axioms, objective goals, progress signatures, and structured iteration logs.
- Git checkpoint/rollback recovery for git-backed workspaces, with graceful no-op behavior outside git.
- No-progress and no-convergence budgets with escalation rungs: rollback, reseed, reframe, escalate-model, halt.
- Identical retry guard to prevent repeating the same prompt/model/world tuple.
- Live `status` command with halt-bundle summaries.
- Optional halt notifier abstraction (`:noop`, `:http`, test transport override).
- Example config library under `configs/`.
- Composable axiom bundles, including Clojure, Rust, Gleam, Node, Python, Go, and clean-tree checks.
- Opt-in hot-reload for config swaps at iteration boundaries.
- Dogfood failure taxonomy and forensic run artifacts.
- Operator facts/status UX for inspecting recent events.
- External harness orchestration, including argv-safe `opencode` invocation.
- `docs/opencode-dogfood.md` dogfood ladder for increasingly complex supervised `opencode` tasks.
- Release/config documentation: `CONFIG.md`, `RELEASE.md`, and ADRs through Phase 8.

### Validation

- Full suite: 72 tests / 401 assertions / 0 failures / 0 errors.
- Config library: all `configs/*.edn` and `configs/dogfood/*.edn` load through `axiom.config/load-config`.
- Demo exits: `steady=0`, `stall=1`, `corrupt=1`, `recover=0`.
- Live `opencode` dogfood ladder levels 01-08 completed in a disposable git-backed workspace with objective checks green.

### Deferred

- openspeq permanent-record merge remains intentionally deferred pending explicit approval.
- Static Go port deferred until distribution pressure proves Babashka is not enough.
- GUI, distributed runners, and cross-run learning are future work.
