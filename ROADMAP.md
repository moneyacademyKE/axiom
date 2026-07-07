# Axiom -- Delivery Roadmap

> Status: Draft v0.1 - Each phase ships something **independently useful and runnable**. You can stop after any phase and have a working tool.

## Release Philosophy

- **Phased by safety, not by feature.** Each phase removes one class of failure mode, in order of severity (corruption -> spinning -> thrashing -> blindness -> distribution).
- **Every phase is testable in isolation** against a fake/simulated agent before being trusted with a real one.
- **No phase introduces a new failure mode** that a later phase is needed to fix.

---

## Phase 0 -- Bare Loop (MVP)

**Goal:** A config-driven loop that spawns an agent, re-observes, and detects no-progress.

**Deliverables**
- Config loader (EDN): `goal`, `progress`, `act`, `lock`, `log`.
- `observe-world` -- re-derive state from disk/git.
- Lockfile acquisition + graceful `SIGINT` release.
- The core `run` loop: observe -> goal? -> act -> re-observe -> verify progress -> record.
- Per-iteration JSON logging.
- No-progress budget: signature unchanged -> halt after N.

**Done when:** A fake agent that does nothing causes a halt within `N+1` iterations; a fake agent that advances reaches the goal and exits 0.

---

## Phase 1 -- Integrity & Checkpoint

**Goal:** Axioms are sacred; bad iterations don't leak forward.

**Deliverables**
- `axioms` config field; evaluated every iteration.
- Axiom violation -> halt + diagnostic dump + notify (non-zero exit).
- `checkpoint!` -- git `last-good` tag before every act (snapshot fallback).
- `rollback` -- `reset --hard last-good` on stall.
- Reusable axiom bundles per project type (`gleam-build`, `rust-build`, `clj-build`).

**Done when:** An agent that writes a corrupt file triggers an axiom halt; a rollback restores byte-identical state to `last-good`.

---

## Phase 2 -- Escalation & Perturbation

**Goal:** Retries change something; thrashing is caught.

**Deliverables**
- No-convergence budget (state changes, goal never met).
- The escalation ladder: `rollback -> reseed -> reframe -> escalate-model -> halt`.
- **Identical-retry guard:** no `(prompt, signature)` tuple repeats the previous attempt.
- Prompt builder as a function of `(world, attempt)` so reframing is real.
- Halt bundle when the ladder is exhausted.

**Done when:** A stuck goal climbs the ladder deterministically, each rung observably different, and halts after the final rung with a complete bundle.

---

## Phase 3 -- Observability & Ops

**Goal:** A run is debuggable without re-running it; a human can watch it.

**Deliverables**
- Rich per-iteration record (axiom states, durations, attempt number, signature).
- Diagnostic bundle: last N iterations + agent transcripts + world snapshot + failed axiom.
- Live status view (tail the structured log).
- Notifier abstraction (Telegram/Slack/webhook) -- wired to halt events.
- Config validation at load time (reject goals without a clean predicate + signature).

**Done when:** From a halt bundle alone, a human can name the blocker in under 2 minutes.

---

## Phase 4 -- Distribution & Multi-Goal

**Goal:** Hand it to someone else; run more than one goal.

**Deliverables**
- Single static binary option (Go port) for non-Babashka hosts.
- Example config library (dogfood-level-gen, test-convergence, benchmark-chase).
- Hot-reload / config-swap semantics (resolves Open Question #4).
- Composable axiom sets and prompt-builder plugins.

**Done when:** A new user can clone, write ~20 lines of EDN, and run a working supervised loop with zero hand-holding.

---

## Milestones

| Phase | Ships | Core failure mode removed | Exits cleanly on |
|-------|-------|---------------------------|------------------|
| 0 | Bare loop | Unbounded spinning | no-progress halt / goal met |
| 1 | Integrity + checkpoint | State poisoning + undetected corruption | axiom halt / clean rollback |
| 2 | Escalation + perturbation | Insane identical retries + thrashing | ladder-exhausted halt |
| 3 | Observability | Blindness to *why* it stopped | debuggable halt bundle |
| 4 | Distribution | One-off-ness | reusable, shareable runner |

## Deferred (explicitly not v1)

- Multi-agent orchestration.
- Distributed / networked runs.
- GUI.
- Cross-run learning / persistent memory.

---

*This roadmap sequences the work. Each phase should produce an entry in the [decision log](specs/decision-log.md) when its design is finalized.*
