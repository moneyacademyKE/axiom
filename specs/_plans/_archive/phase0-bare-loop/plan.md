# Plan: Phase 0 -- Bare Loop MVP

> **Status:** Accepted (recorded 2026-07-07). Implemented, verified (`bb test` green; steady/stall/corrupt demos exit 0 / non-zero / non-zero). Outcome promoted to permanent `SPEC.md` + ADR-0006 in `specs/decision-log.md`. Retained as a historical planning artifact.

> Scope: a runnable, testable core loop. No escalation ladder, no multi-host. Ships the `decide` function and three fake-agent demos.

## Context

Phase 0 of the Axiom roadmap. Deliver the smallest loop that demonstrably: observes world state, checks a goal, makes monotonic progress, detects stalls, and logs every iteration. Proves the architecture with fake agents **before** any real AI agent is attached -- per mission testing strategy.

## Affected Areas

| Area | Status |
|------|--------|
| `src/axiom/*` (core, config, observe, lock, log, git) | New |
| `axiom.clj` (entry point) | New |
| `examples/*` (3 demo configs + fake agents) | New |
| `test/*` (unit tests) | New |
| `bb.edn` | New |

## Task Breakdown (MECE)

1. **Config layer** -- EDN loader + predicate DSL interpreter (`eval-pred`). Pure, unit-tested.
2. **Observe layer** -- `build-world`: run shell observers, parse, produce world map.
3. **Lock layer** -- pidfile acquire/release with liveness check.
4. **Log layer** -- structured EDN per-iteration + terminal events.
5. **Checkpoint layer** -- git tag/rollback (basic).
6. **Core loop** -- the state machine; pure `decide` fn + side-effecting `run!`.
7. **Entry point** -- `axiom.clj`: parse args, load config, run.
8. **Demos** -- steady / stall / corrupt configs + fake agent scripts.
9. **Tests** -- predicate DSL, decide logic, stall budget, integrity halt, goal-met exit.

## Scenarios (BDD)

- **Goal met:** steady agent creates files; loop exits 0 when `level-count >= target`.
- **Stall:** no-op agent; progress unchanged; loop halts after `stall-after` with `:stall` reason.
- **Integrity halt:** corrupt agent removes marker; loop halts immediately with `:integrity` reason, no further acts.
- **Resumable:** kill mid-run; restart; observes disk, continues from real state.

## Validation Commands

```bash
bb test/axiom/run_tests.clj                     # unit suite must pass
bb axiom.clj examples/steady.edn --max-iters 20 # exits 0, goal reached
bb axiom.clj examples/stall.edn                 # exits non-zero, :stall
bb axiom.clj examples/corrupt.edn               # exits non-zero, :integrity
```

## Out of Scope (deferred)

- Escalation/perturbation ladder -> Phase 2
- Convergence/thrash budget -> Phase 2
- Observer DSL beyond built-in parse types -> Phase 3
- Multi-host / distribution -> Phase 4
- Real AI agent integration -> Phase 1+
