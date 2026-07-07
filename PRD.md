# Axiom -- Product Requirements Document

> Status: Draft v0.1 - Defines *what* Axiom must do and *why*. The "how" lives in SPEC.md; the "when" in ROADMAP.md.

## 1. Problem Statement

Autonomous AI agents are unreliable workers. When you point one at an open-ended goal ("generate enough test fixtures to cover this surface," "refactor until the suite is green," "iterate until the benchmark holds"), three failure modes recur:

1. **Silent spinning** -- the agent reports success while making no real progress; the loop runs forever on a treadmill.
2. **State poisoning** -- a half-written or corrupt artifact from attempt N sabotages attempts N+1..infinity.
3. **Insane retries** -- the loop reruns the *identical* prompt on the *identical* state, expecting a different result.

Today, people glue around this with bespoke shell scripts (`loop_infinite.clj`-style). Each one rediscovers the same pitfalls (no lock, no integrity gate, no rollback, retry-without-perturbation) and ships them as bugs. There is no reusable, general-purpose supervisor that treats the agent as an unreliable narrator and the filesystem as the only source of truth.

## 2. Target Users

| Persona | Who they are | What they need from Axiom |
|---------|-------------|---------------------------|
| **The Automation Engineer** | Builds CI/dogfood loops; lives in shell + a scripting lang | A config-driven loop they can drop a goal into and walk away |
| **The Solo Dev / Indie Hacker** | Runs long autonomous generation jobs overnight | Confidence that it won't thrash forever or corrupt their repo |
| **The ML/AI Tooling Dev** | Orchestrates LLM agents at task-level granularity | A reliable supervisor with structured logs and halt diagnostics |
| **The QA / Test-Fixture Owner** | Needs bulk generated, validated, registered content | Monotonic progress with a clean "done" predicate |

## 3. Goals & Non-Goals

### Goals (v1)
- A reusable, config-as-data supervisor over a single AI agent on a single host.
- Guarantee integrity invariants every iteration; halt (don't retry) on violation.
- Detect both spinning and thrashing; escalate by *perturbing*, never by identical retry.
- Checkpoint before every act; roll back on stall so bad iterations don't leak forward.
- Be idempotent & resumable -- kill anywhere, restart reads disk, continues.
- Structured per-iteration logs + a diagnostic bundle on halt.

### Non-Goals (v1)
- Multi-agent orchestration (one agent per run).
- Distributed / networked / multi-host runs.
- GUI (CLI + structured logs only).
- Cross-run learning / memory (each run is stateless beyond its own log).
- Replacing the agent itself -- Axiom supervises; it is not the worker.

## 4. Use Cases

1. **Bulk content generation** -- "generate levels/fixtures until count >= N; build must pass; no placeholders."
2. **Convergence loops** -- "refactor/patch until test suite is green; git tree must stay valid."
3. **Benchmark chasing** -- "iterate until benchmark threshold holds for 2 consecutive runs."
4. **Dogfood harnesses** -- "spawn an agent to self-generate and register test batches," the original `loop_infinite.clj` use case, generalized.
5. **Any monotonic-progress goal** -- anything expressible as a machine-checkable predicate + a progress signature.

## 5. Functional Requirements

### Core Loop
| ID | Requirement |
|----|-------------|
| FR-1 | Acquire an exclusive lock at start; a second instance exits cleanly. |
| FR-2 | Observe world state by re-deriving it from disk/git/build each iteration. |
| FR-3 | Evaluate every configured axiom each iteration. |
| FR-4 | On any axiom `false`: halt, write diagnostic bundle, notify, exit non-zero. |
| FR-5 | Evaluate the goal predicate; on `true`, record success and exit 0. |
| FR-6 | Checkpoint to `last-good` before every act. |
| FR-7 | Spawn the agent via the configured action function with a state-aware prompt. |
| FR-8 | Re-observe and verify the progress signature advanced. |

### Stall & Escalation
| ID | Requirement |
|----|-------------|
| FR-9 | Track a no-progress budget (signature unchanged). |
| FR-10 | Track a no-convergence budget (state changes, goal never met). |
| FR-11 | On budget exhaustion, climb the escalation ladder; **every rung changes something**. |
| FR-12 | Forbid retrying an identical prompt on identical state. |
| FR-13 | Roll back to `last-good` before any escalated retry. |
| FR-14 | After the ladder is exhausted: halt with a full diagnostic bundle + notify. |

### Lifecycle
| ID | Requirement |
|----|-------------|
| FR-15 | Handle `SIGINT` gracefully: release lock, exit 0, no orphaned lock. |
| FR-16 | Be fully resumable: state comes only from the world, never from memory. |
| FR-17 | Emit a structured JSON record per iteration. |

## 6. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Reliability** | Never corrupt user state; only Axiom-created checkpoints are rollback targets. |
| **Safety** | Agent is sandboxed to the repo dir; `--yolo`-style autonomy stays local. |
| **Observability** | Per-iteration JSON logs; halt-time diagnostic bundle (last N iterations + transcripts + world snapshot). |
| **Portability** | Single-host; minimal dependencies (Babashka + standard tools). |
| **Performance** | Loop overhead negligible vs. agent runtime; no redundant re-derivation beyond what integrity demands. |
| **Simplicity** | Runner is simpler than the system it supervises; never shares the target's toolchain failure modes. |

## 7. Success Metrics

| Metric | Target |
|--------|--------|
| **No infinite-spin** | A stuck goal halts within `max-no-progress + ladder-depth` iterations, always. |
| **No state poisoning** | After a rollback escalation, world state == `last-good`, verifiably. |
| **No identical retry** | Zero iterations where `(prompt, world-signature)` repeats the previous attempt. |
| **Resumability** | Killing at any point and restarting yields identical forward progress to never killing it. |
| **Diagnostic completeness** | A halt bundle lets a human determine the blocker without re-running the loop. |

## 8. Constraints & Assumptions

- The goal **must** be reducible to a machine-checkable predicate. Fuzzy desires ("make it better") are out of scope and explicitly rejected.
- A **progress signature** that changes iff real progress occurs must be definable.
- The target project lives under a single directory with optional VCS (git preferred; snapshot fallback).
- A single agent command exists and is spawnable (`cmd -p ...`, or equivalent).

## 9. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Progress signature lies (changes without real progress) | Med | High | Allow composite signatures; require goal predicate as the final arbiter |
| Agent escapes sandbox and acts externally | Low | Severe | Restrict working dir; no network egress policy; `--yolo` stays local |
| Checkpoint/rollback corrupts unrelated work | Low | High | Only Axiom-created checkpoints are targets; never bare `reset` to arbitrary refs |
| Perturbation ladder still loops (rollback->retry->rollback""¦) | Med | Med | Ladder is finite and ordered; exhaustion = halt, not infinite climb |
| Re-derivation cost dominates runtime | Low | Low | Cache within an iteration; signature is a hash, not a full scan |
| Goal predicate has no clean monotonic signal | Med | High | Reject at config-validation time; require both predicate + signature |

## 10. Dependencies

- **Babashka** (Clojure scripting runtime) -- the implementation language.
- **`flock`** (or equivalent) -- for the exclusive lock.
- **Git** -- preferred checkpoint mechanism; filesystem snapshot as fallback.
- **A spawnable agent command** -- supplied by the user in config.

## 11. Open Questions

1. Snapshot-based checkpoint for non-git repos -- tar, hardlink tree, or refuse to run without VCS?
2. Should the no-convergence budget reset after a successful rollback+reseed, or only after genuine progress?
3. Notifier abstraction now (v1) or defer to a later phase?
4. Hot-reload of config mid-run, or strictly one config per run?

---

*This PRD is the contract for scope. Changes to goals/non-goals must update the [decision log](specs/decision-log.md).*
