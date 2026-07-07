# Axiom -- Decision Log

> Architecture Decision Records (ADR-style). Append-only, newest first. Each entry: context -> decision -> consequence.

---
## ADR-0018 -- Phase 10: file-backed operator controls accepted
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** ADR-0015 deliberately deferred pause/resume/stop until they could be represented as explicit run-control marker files with tests. Axiom should remain a boring supervisor, not grow a daemon socket or hidden process-control channel.

**Decision:** Add `axiom.control`, a tiny filesystem control plane under `<workdir>/.axiom-control`. `pause` and `stop` write marker files; `resume` clears the pause marker. The run loop checks control state between iterations and halts with `:operator-paused` or `:operator-stop` before another act runs. `status/operator-facts` includes `:control-state`, and the CLI exposes `bb axiom.clj pause|resume|stop <config.edn>` as operator commands. The taxonomy now classifies operator and explicit budget halts instead of reporting them as unknown.

**Evidence:** Full suite green after Phase 10: **76 tests / 424 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). New namespace: `axiom.phase10-control-test`.

**Consequence:** Operators can intervene with plain files and auditable CLI commands. Pause is resumable, stop is final for that run, and there is still no new remote attack surface.

---
## ADR-0017 -- Phase 8: release-readiness docs accepted
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** After Phase 7, Axiom could supervise external harnesses like `opencode`, but a developer preview still needed a clear install path, config schema notes, validation checklist, and release gate. Shipping code without an operator-facing release contract would turn a good supervisor into a tiny liability factory.

**Decision:** Add `RELEASE.md` as the release-readiness surface: install/run commands, exit-code contract, config schema v1 summary, release-candidate checklist, and security notes. Update `README.md` to reference `CONFIG.md` and `RELEASE.md`, and mark Phases 0-8 as shipped/verified with Phase 5-8 capabilities named explicitly. Clarify `CONFIG.md` as schema v1 documentation.

**Evidence:** Baseline validation before docs: **68 tests / 268 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). The docs now name the exact validation gate to run before any tag: full suite, reset demos, status explanation, dogfood shell+harness acts, and ADR completeness.

**Consequence:** Axiom is now documented as a developer-preview release candidate with explicit limits. The core remains Babashka-first; a static Go port and broader integrations remain deferred until real distribution pressure justifies them.

---
## ADR-0016 -- Phase 7: harness orchestration accepted
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Axiom should not become one more agent. Its job is supervision: goals, integrity, progress, budgets, rollback, escalation, and halt bundles. Concrete agent CLIs such as `opencode` are harnesses below it. Their transcripts and self-reports are useful hints, not truth.

**Decision:** Add `axiom.harness`, a data-first invocation boundary for external harnesses. Built-in `:opencode` maps to argv templates (`opencode run --model {{model}} {{prompt}}`) and configs can override/extend `:harnesses`. Harness acts use `{:harness :opencode :prompt "..."}` and validate at config load. Invocation construction is pure, uses argv vectors rather than shell strings, renders prompt/model/attempt/world placeholders, and supports `:harness-transport` for deterministic tests without requiring opencode to be installed. `core/run-act!` dispatches to the harness path when an act has `:harness`; afterward the loop still re-observes the world and decides from predicates, not from harness stdout.

**Evidence:** Full suite green after Phase 7: **68 tests / 268 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). New namespace: `axiom.phase7-harness-test`, including a lying harness that prints `GOAL FULFILLED` while leaving disk unchanged; Axiom correctly halts for `:stall` because observed world state did not satisfy the goal.

**Consequence:** Axiom can now supervise harnesses like opencode without coupling correctness to their self-report. The runner remains the small, skeptical supervisor; harnesses are replaceable actuators. Future model routing and remote runners should extend the same data boundary, not tunnel shell scripts through the core.

---
## ADR-0015 -- Phase 6: operator UX status surface accepted
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 5 gave halted runs a stable taxonomy, but operators still needed a humane read surface for live and halted runs without scraping prose. Axiom's operational contract is simple: a human should be able to answer what rung it is on, what attempt/model/prompt it is using, whether hot-reload is active, and why it halted from structured data.

**Decision:** Promote `status/operator-facts` as the pure operator data surface and render those facts in `format-summary`. The status view now exposes config path, hot-reload state, last event, current rung, attempt, prompt/model indexes, stall/thrash counters, failure class, severity, retryability, halt iterations, and last world. This keeps CLI/webhook/TUI surfaces downstream of one data map instead of brittle text parsing. Pause/resume/stop are deliberately deferred until they can be represented as explicit run-control marker files with tests; bolting ad-hoc process control onto the loop would be little demon machinery.

**Evidence:** Full suite green after wiring both Phase 6 test namespaces into the canonical runner: **62 tests / 244 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). New/covered namespaces: `axiom.phase6-operator-test` and `axiom.phase6-operator-ux-test`.

**Consequence:** Operators now get richer status with no new trust boundary. The loop remains data-first and read-only status stays safe against partially broken configs. Future intervention commands should land as small, observable marker semantics rather than hidden process magic.

---
## ADR-0014 -- Phase 5: dogfood hardening taxonomy accepted
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phases 0-4 made the loop capable, but real dogfood work needs stable operator language. Raw halt reasons like `:stall` and `:integrity` are implementation facts; humans need a durable taxonomy that answers what failed, whether retrying is sane, and where the forensic artifacts live.

**Decision:** Add `axiom.taxonomy` as a pure, data-first classification layer. `classify-halt` maps halt actions/bundles to stable classes: no progress, repeated identical actions/no convergence, budget exhausted, dirty-tree unsafe, invalid hot-reload config, model/tool failure, and rollback-limit hit. Unknown reasons classify as `:unknown` while preserving the original reason, so future failure modes stay visible. `artifact-summary` names the config, log dir, events log, halt bundle, config path, and hot-reload state without scraping prose logs. Add Phase 5 tests proving taxonomy stability, unknown-reason visibility, dogfood config scenario shape, config library validation, and artifact surface naming.

**Evidence:** Full suite green after Phase 5: **59 tests / 234 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). New namespace: `axiom.phase5-dogfood-test`.

**Consequence:** Dogfood runs now have a common vocabulary for failure analysis without coupling operators to internal log strings. The next UX phase can render richer summaries from stable data instead of inventing text parsing garbage. Openspeq permanent-record merge remains deferred for explicit approval.

---
## ADR-0013 -- Phase 4: example config library + hot-reload complete
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 4 had two jobs: make Axiom easier to apply to real projects, and let a long-running loop adapt when its EDN config changes. Phase 4a needed reusable example configs plus composable axiom bundles so users don't hand-roll boilerplate. Phase 4b needed safe hot-reload: swap the config brain at iteration boundaries without losing run-state such as attempts, stall/thrash counters, rollback count, prompt/model indexes, and identical-retry history.

**Decision:**
1. **Composable bundle library:** `:axiom-bundle` now accepts either a single keyword or a vector of keywords. Vectors are flattened in order before explicit `:integrity` checks are appended. Unknown bundle names still throw loudly. The bundle registry now includes `:node-build`, `:python-build`, and `:go-build` alongside the existing Clojure/Rust/Gleam/clean-tree bundles.
2. **Example configs:** added `configs/dogfood-level-gen.edn`, `configs/test-convergence.edn`, and `configs/benchmark-chase.edn`. `benchmark-chase` demonstrates real composition via `:axiom-bundle [:rust-build :clean-tree]`.
3. **Hot-reload:** added `config/file-mtime` + `config/maybe-reload [cfg path last-mtime]`. When the config file changes, the new EDN is re-read through `load-config`, so required keys, predicate shape, act shape, and bundle expansion are all validated before use. Invalid changed config throws rather than silently corrupting the run.
4. **Safe config swap in `run!`:** normal CLI runs now pass `:config-path` into `core/run!`. When `:hot-reload true` is set, the loop checks for config changes at iteration boundaries before observing. `swap-cfg` preserves process-owned context (`:workdir`, `:lock`, `:log-dir`, `:checkpoint`, `:config-path`, `:hot-reload`), while run-state remains entirely in the loop state map, so attempts/counters/seen-tuples survive the swap. A successful swap logs a `hot-reload` event.
5. **Go port remains deferred:** Babashka remains the implementation language for now. A static Go port is still not justified until distribution constraints demand it.

**Evidence (all green):**
- Unit/regression suite: **56 tests / 201 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`). New namespaces: `axiom.phase4-bundles-test` and `axiom.phase4-hotreload-test`.
- Hot-reload integration: real `core/run!` starts with an unreachable goal, a background future rewrites the config goal mid-run, the loop logs `hot-reload`, preserves state, re-observes, and exits `:done`.
- Demo gate unchanged: steady=0, stall=1, corrupt=1, recover=0.
- Example config library validates: `dogfood-level-gen.edn`, `test-convergence.edn`, and `benchmark-chase.edn` all load through `config/load-config`.

**Consequence:** Phase 4 closes with reusable config starting points, composable integrity bundles, and a bounded hot-reload mechanism that treats config as data but never trusts unvalidated data. Long-running Axiom loops can now adapt their goal/action/integrity/observer definitions without losing run progress. The irreversible openspeq permanent-record merge remains deferred until the MAIN session gives explicit approval.

---
## ADR-0012 -- Phase 3: Observability & Ops complete
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 3 removed the last blindness failure mode — before it, a run could halt and a human could not say *why* without re-running it. The ROADMAP's "done-when" gate was: "from a halt bundle alone, a human can name the blocker in under 2 minutes." Several Phase 3 deliverables were already implemented and tested in prior sessions (rich per-iteration EDN records, the diagnostic halt bundle `log/halt-bundle!`, and config validation at load time via `validate-config`/`valid-pred?`/`load-config` throws). Two remained: the live `status` view and the notifier abstraction.

**Decision:**
1. **Live status view (`src/axiom/status.clj`):** a PURE `format-summary [cfg bundle iterations]` (no disk) + a `summarize!` that reads `<log-dir>/halt-bundle.edn` when present (halted) or tails `events.log` + `log/read-iterations` last N (running). `axiom.clj` gained a read-only `status` subcommand: `bb axiom.clj status <config.edn> [--last N]` — read+EDN-parse only (no validation) so a partially-broken or stale config can still report its last-known state. The summary names: config name, state (`HALTED: <reason>` or `RUNNING`), iteration count, halt iterations, last world, and the 5 most-recent events.
2. **Notifier abstraction (`src/axiom/notify.clj`):** opt-in via `:notify {:type :http :url "..."}`. A PURE `build-message [cfg halt-action bundle]` builds the message map (reason, name, iteration count, last world, bundle path, ts); `notify!` dispatches on `:type` (`:http` POSTs EDN via `babashka.http-client`, bundled in bb 1.12, with a curl fallback; `:noop` returns the message for tests). A `:transport` key OVERRIDES type dispatch — a test injects a fake 1-arg fn so no live HTTP is ever exercised. Wired into `core.clj` `halt-result!` immediately AFTER `log/halt-bundle!` (so the bundle exists before the message references it). Entire call gated on `(:notify cfg)` — absent `:notify` is a silent no-op, so Phase 0/1/2 configs and tests are byte-identical.
3. **Gotchas encountered & resolved:** (a) destructure `{:keys [name]}` in `format-summary` shadowed `clojure.core/name`, causing `String cannot be cast to IFn` when calling `(name reason)` — used `clojure.core/name` fully-qualified; (b) a long `ns` docstring containing escaped `\"` quotes tripped sci's reader — shortened to a one-line docstring; (c) `babashka.http-client` is bundled (no need for an external dep).

**Evidence (all green):**
- Unit suite: **46 tests / 171 assertions / 0 failures / 0 errors** (`./.tools/bin/bb -cp src:test -m axiom.run-tests`) — up from 33/131 before this session's Phase 3 finish work. New namespaces: `axiom.status-test` (halt+running+disk formats) + `axiom.notify-test` (build-message, :noop, transport-override, end-to-end real-loop halt firing notify).
- Demos unchanged: steady=0, stall=1, corrupt=1, recover=0 (no `:notify` key anywhere → notifier is a silent no-op).
- End-to-end Phase 3 halt-bundle: `bb axiom.clj examples/stall.edn` halts, writes `examples/demo/.axiom-logs-stall/halt-bundle.edn` carrying `:reason :stall`, and `bb axiom.clj status examples/stall.edn` prints `State: HALTED: stall` + `Iterations: 8` + `Halt iterations: 2` + the last world snapshot — a human can name the blocker (stall) from the status output alone, meeting the Phase 3 done-when gate.

**Consequence:** A halted run now leaves a self-contained diagnostic bundle on disk plus an optional push notification, and a read-only `status` command reconstructs what happened without re-running. A broken config is caught at load time (validation throws), not mid-loop. Phase 3 is closed. The irreversible openspeq permanent-record merge (staged specs → `specs/specs/`) remains deferred to the MAIN session for explicit user approval, same policy as Phases 0/1/2.

---
## ADR-0010 -- Phase 1: rollback-as-recovery + axiom-bundles registry
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 0's loop halted permanently on the first stall — a single no-progress budget exhaustion killed the run, even if the agent could succeed on a fresh attempt. The ROADMAP's Phase 1 (Integrity & Rollback) required two capabilities: (1) when the stall budget is exhausted but rollback attempts remain, reset the workspace to the last-known-good checkpoint and retry instead of dying; (2) a registry of pre-built integrity-check sets ("axiom bundles") keyed by project type so per-project configs stay DRY without sacrificing fail-loud validation.

**Decision:**
1. **Rollback-as-recovery in `decide`:** when `(:stall state) >= stall-after` AND `(:rollbacks state) < (:max-rollbacks cfg 2)`, return `{:type :rollback}` instead of `{:type :halt :reason :stall}`. The halt only fires after the rollback budget is exhausted.
2. **Rollback handling in `run!`:** the `:rollback` branch calls `git/rollback!` to `last-good`, resets `:stall` to 0, increments `:rollbacks`, and recurs. The loop retries from a clean slate, bounded by `max-rollbacks`.
3. **Axiom-bundles registry (`config.clj`):** `:gleam-build`, `:rust-build`, `:clj-build`, `:clean-tree` — each a vector of predicate DSL checks. `expand-axioms` composes the bundle (first) with any explicit `:integrity` checks (appended), and throws on an unknown bundle name (fail loud, never silently drop axioms). `load-config` expands `:axiom-bundle` into `:integrity` at load time.
4. **Git is required for meaningful rollback:** `git/rollback!` is a no-op (returns nil) outside a git repo, so non-git workspaces degrade gracefully — the loop retries (stall resets) but nothing is restored, and it halts once rollbacks are exhausted.

**Evidence (all green):**
- Unit suite: **20 tests / 87 assertions / 0 failures / 0 errors** (`bb test`)
  - `decide-stall-budget`: rollback decision when rollbacks remain; halt after budget exhausted
  - `expand-axioms-bundles`: bundle expansion, composition order, fail-loud on unknown bundle
  - `clj-build-bundle-halts-on-red-test-or-build`: dogfooding — red tests OR broken build = halt
  - `tag-and-rollback-restores-byte-identical-state` (git-test): `git/rollback!` restores exact bytes in a real repo
  - `run-rolls-back-and-recovers-to-goal` (phase1-test): **runner-level** — flaky agent stalls, loop rolls back, agent recovers, goal fulfilled (level-count 3, call counter >= 5 proving a rollback+retry occurred)
- Phase 1 end-to-end demo (`examples/recover.edn` + `examples/demo-recover/`): **exit 0**, `GOAL FULFILLED {:level-count 3, :build-ok "pass"}` after `rollback recovery {:restored? true, :rollbacks 1}`. Git log: baseline -> level 1 -> level 2 -> [stall] -> rollback -> level 5 (recovery).
- Phase 0 regression: steady exit 0, stall exit 1, corrupt exit 1 — all unchanged.

**Consequence:** A stall is no longer a death sentence if rollbacks remain — the agent gets a clean-slate retry from the last-known-good state, bounded by `max-rollbacks` (default 2) so the loop can't spin forever. Git-backed workspaces get real byte-identical recovery; non-git repos degrade to retry-then-halt. Axiom-bundles eliminate boilerplate integrity config per project type while preserving the fail-loud discipline (an unknown bundle throws, never silently drops checks). Phase 2's escalation ladder (`:escalate` / `:thrash-after`) was scaffolded alongside and is exercised by `phase2-test`, but its full rung semantics (reseed, reframe, escalate-model) remain Phase 2 work.

---
## ADR-0009 -- Numeric predicates are fail-safe on missing/non-numeric refs
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** The expanded Phase 0 unit suite (13 tests / 49 assertions) exercising `eval-pred`'s comparison, combinator, and `:exists` paths surfaced a latent bug: the numeric predicate ops (`:>= :<= :> :<`) threw `NullPointerException` when their `:ref` was missing or non-numeric. `observe.clj` deliberately yields `nil` for a failed observer, and its own docstring states *"predicates treat nil as missing, so integrity checks catch breakage rather than the observer layer crashing the whole loop"* -- but the implementation violated that: a single failed observer would crash `decide` and take down the entire loop. This is exactly the "state poisoning / silent crash" failure mode Axiom exists to prevent, and it contradicted mission axiom #1 (integrity is a halt condition, not a crash).

**Decision:** Route all four numeric comparisons through a `num-cmp` helper that returns false ("not satisfied") when the ref is not a number, instead of calling Clojure's numeric comparator on `nil`. `:=` / `:not=` already tolerate `nil` and are unchanged. Predicates are now total functions over any world map: missing/broken data fails the axiom (halt) rather than throwing.

**Evidence (all green):**
- `bb test/axiom/run_tests.clj` -> 13 tests / 49 assertions / 0 failures / 0 errors
- New assertions: `(eval-pred {} {:op :>= ...})` and `:<<` on a missing ref return falsey without throwing
- Demos unchanged: steady exit 0, stall exit 1, corrupt exit 1

**Consequence:** The loop is now robust to failed observers -- a broken observer triggers an integrity `:halt` instead of an NPE crash, matching the documented intent and strengthening "the runner is simpler than the system it supervises" (it never shares the target's data-shape failure modes). Predicates are safe to evaluate against any world, including partially-observed ones.

---


## ADR-0008 -- Phase 0 validation gate ACTUALLY passed (corrects premature ADR-0006)
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** ADR-0006 claimed Phase 0 was recorded, but the code was broken at that time (`lock.clj` had a parse bug; validation commands were never run). A previous autoheal session promoted the plan to `_archive/` and stamped a completion ADR without verifying against the world — the exact "trust self-report" failure Axiom is built to prevent.

**Decision:** Re-verify from scratch. Fix the code, run the plan's own validation commands as a hard gate, and only then accept the recording. Promote the delta to `specs/specs/core-loop.md` with evidence.

**Evidence (all green):**
- Unit suite: 11 tests / 28 assertions / 0 failures (`bb test/run_tests.clj`)
- `bb axiom.clj examples/steady.edn` → exit 0, `GOAL FULFILLED {:level-count 3}`
- `bb axiom.clj examples/stall.edn` → exit 1, `HALT: stall {:iterations 2}`
- `bb axiom.clj examples/corrupt.edn` → exit 1, `HALT: integrity` at stall=1 (proves integrity-before-stall priority)

**Consequence:** Phase 0 is now legitimately recorded. The premature ADR-0006 stands as a cautionary record — the self-driving loop ran but certified a false completion because it lacked a verification gate. The lesson is encoded in Axiom's own design: validation commands are a mandatory gate, not documentation.

## ADR-0007 -- Per-iteration logs are EDN, not JSON
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 0 needed structured per-iteration logs. JSON was tempting for external tooling (`jq`), but `clojure.data.json` is NOT bundled in Babashka (verified empirically) and cheshire is unavailable by default.

**Decision:** Log every iteration as EDN (`.edn`). EDN is native, zero-dep, and round-trips into Clojure tooling; the mission already chose EDN as the data format.

**Consequence:** Logs are immediately readable from Babashka with no dependencies. If external/non-Clojure tooling needs JSON later, add a JSON exporter in Phase 3 observability rather than switching the native format.

## ADR-0006 -- Phase 0 bare loop recorded
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** Phase 0 needed a runnable proof that Axiom can observe disk state, evaluate data predicates, act, re-observe, detect stalls, and halt on integrity violations before any real AI agent is attached.

**Decision:** Ship the bare loop as Babashka namespaces (`config`, `observe`, `core`, `lock`, `log`, `git`) plus three fake-agent demos: steady progress, no-op stall, and corruption. Promote the Phase 0 core-loop staged delta into `SPEC.md` and verify it with `bb test`.

**Consequence:** Axiom now has an executable MVP and a regression suite for the core decision semantics. The escalation ladder, snapshot fallback, and real-agent integration remain future phases.


## ADR-0005 -- openspeq spec workspace adopted
**Date:** 2026-07-06 - **Status:** Accepted

**Context:** The project needs durable, agent-agnostic planning artifacts that outlive any single coding session and survive context compaction.

**Decision:** Adopt the openspeq `mission -> plan -> implement -> record` workflow with a repo-local `specs/` workspace (`mission.md`, `decision-log.md`, `_plans/`).

**Consequence:** Future work flows through staged plan/record docs. Mission is the north-star; this log tracks why decisions were made.

---

## ADR-0004 -- Babashka (Clojure) as the implementation language
**Date:** 2026-07-06 - **Status:** Accepted

**Context:** Axiom is glue -- spawn, read files, log, decide. It must survive the target project's toolchain failure modes, not share them.

**Decision:** Implement in Babashka. Defer a Go static binary to Phase 4 only if distribution demands it.

**Consequence:** EDN config is natural; the runner stays simple; JVM interop available if needed. Non-Babashka hosts wait for Phase 4.

---

## ADR-0003 -- Checkpoint before every act; rollback on stall
**Date:** 2026-07-06 - **Status:** Accepted

**Context:** A corrupt half-write from iteration N poisons N+1..infinity. The original `loop_infinite.clj` had no rollback.

**Decision:** Tag `last-good` (git) before every `ACT`. On stall-driven escalation, `reset --hard` to `last-good` before retrying. Only Axiom-created checkpoints are valid targets.

**Consequence:** Bad iterations never leak forward. Cost: one git op per iteration (negligible vs. agent runtime).

---

## ADR-0002 -- Retries must perturb; two stall budgets
**Date:** 2026-07-06 - **Status:** Accepted

**Context:** Identical retries are insanity. Spinning (no progress) and thrashing (progress without convergence) are distinct failures needing distinct responses.

**Decision:** Track two budgets (`max-no-progress`, `max-no-converge`). On exhaustion, climb a finite ladder: `rollback -> reseed -> reframe -> escalate-model -> halt`. Every rung changes something; identical `(prompt, signature)` repeats are forbidden.

**Consequence:** No infinite-spin, no insanity retries. The ladder is bounded; exhaustion is a genuine, documented halt.

---

## ADR-0001 -- Separate four concerns; trust only re-derived state
**Date:** 2026-07-06 - **Status:** Accepted

**Context:** Hand-rolled loops conflate "what to do," "is it done," "did we move," and "is anything broken." They fail to distinguish a retry condition (stuck) from a stop condition (broken).

**Decision:** Separate Goal (done?), Integrity (sound?), Progress (moved?), Action (work). Integrity violation = halt, never retry. Authoritative state is re-derived from disk/git/build every iteration; the agent's self-report is advisory only.

**Consequence:** The loop is idempotent and resumable. The agent can lie; Axiom won't believe it.

---

*Template for new entries:* `## ADR-00NN -- <title>` -> Date/Status -> Context -> Decision -> Consequence.

---

## ADR-0011 -- Phase 2: escalation ladder rungs perform real perturbation
**Date:** 2026-07-07 - **Status:** Accepted

**Context:** ADR-0002 specified the ladder `rollback -> reseed -> reframe -> escalate-model -> halt`, but until now the `:escalate` branch in `run!` was pure bookkeeping -- it reset the stall/thrash budgets and advanced `:escalation-index` but did NOT execute any rung side-effect (the code comment read "side effects land later"). That meant a stuck goal climbed the ladder in name only: every rung was identical, which is precisely the "insane identical retries" failure ADR-0002 was written to prevent.

**Decision:** Make each rung perturb something observably different, all opt-in via data so Phase 0/1 configs are unaffected:
- `:reseed` -- runs `(:reseed cfg)` `:sh` command (perturbs inputs/environment) before retrying. No-op when `:reseed` is absent.
- `:reframe` -- advances `:prompt-index` in run-state; `run-act!` now treats `:act` as a vector of templates and selects by `:prompt-index` (clamped to last). A single-map `:act` (Phase 0/1) is unaffected.
- `:escalate-model` -- advances `:model-index`; `run-act!` renders `{{model}}` from `(:models cfg)` indexed by `:model-index`. Absent `:models` -> `{{model}}` collapses to `""`, leaving existing templates intact.

Every rung still resets stall/thrash and advances the ladder index; an unknown rung degrades to bookkeeping (safe default). The `run-act!` signature changed from `(cfg, world, attempt)` to `(cfg, world, state)` to thread prompt/model selection through; `attempt` is derived from state.

**Consequence:** A stuck goal now climbs the ladder deterministically with each rung observably different (proven by `phase2-rungs-test`: rung.log = `[A:cheap A:cheap B:cheap B:smart]`, reseed counter = 1). The existing `phase2-test` (ladder-climb with no side-effect config) still passes -- absent keys degrade to bookkeeping, so the rung mechanism is backward-compatible. The identical-retry guard from ADR-0002 (no `(prompt, signature)` tuple repeats) is the remaining Phase 2 sub-item; it is tracked as follow-up since it needs careful design to avoid over-eager halting on legitimate retries. Validation gate: 21 tests / 91 assertions / 0 failures; steady=0, stall=1, corrupt=1, recover=0.
