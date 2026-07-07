# Axiom

> An autonomous goal-fulfillment runner that never violates its axioms.

Axiom is a self-driving task runner. You give it a **goal** and a set of **invariants** (its axioms). It repeatedly spawns an AI agent to make progress toward the goal, re-derives the real world state after every action, and decides whether to continue, retry, or escalate -- all while guaranteeing that the invariants hold. If an axiom breaks, it halts immediately. If it stalls, it perturbs its approach and tries harder. It only gives up after a genuine, exhausted, well-documented effort.

## The name

In a formal system, **axioms** are statements that *must* be true; a **theorem** is what you *want* to prove true within those axioms. Axiom the runner treats integrity invariants as axioms (violation = halt) and the goal as a theorem to fulfill (becomes true = done). It relentlessly pursues the theorem without ever breaking an axiom.

## What's in this folder

| Document | What it is |
|----------|-----------|
| **[SPEC.md](SPEC.md)** | Technical product specification -- the architecture, data model, run loop, contracts. *How it's built.* |
| **[PRD.md](PRD.md)** | Product requirements -- problem, users, use cases, functional/non-functional requirements, metrics, risks. *What & why.* |
| **[ROADMAP.md](ROADMAP.md)** | Delivery roadmap -- phased plan, milestones, what ships when. *When & in what order.* |
| **[CONFIG.md](CONFIG.md)** | EDN config reference, bundles, harness acts, and safety defaults. |
| **[RELEASE.md](RELEASE.md)** | Release validation checklist and release-candidate gate. |
| **[CHANGELOG.md](CHANGELOG.md)** | Release history and validation notes. |
| **[docs/opencode-dogfood.md](docs/opencode-dogfood.md)** | Graduated dogfood ladder for supervising `opencode` as an external harness. |


## In one breath

A state machine, not a `while(true)`:

```
ACQUIRE lock -> OBSERVE world -> ASSERT axioms -> GOAL met?
  -> CHECKPOINT -> ACT -> RE-OBSERVE -> VERIFY progress -> RECORD -> DECIDE
```

- **Never trusts the agent's self-report.** All state is re-derived from the world (filesystem, git, build output).
- **Two stall budgets** -- no-progress (spinning) and no-convergence (thrashing) -- each with its own escalation path.
- **Retries perturb**, never repeat. Rollback -> reseed -> reframe -> escalate model -> halt.
- **Checkpoints before every act, rolls back on stall** so one bad iteration never poisons the next.
- **Config as data** (EDN). The runner is a pure function of `(config, world-state) -> decision`.

## Status

**Phases 0-8 -- shipped & verified (2026-07-07).** Axiom now has the Babashka runner, rollback/checkpoint recovery, escalation/perturbation, observability/ops, example config library, composable axiom bundles, hot-reload semantics, dogfood failure taxonomy, operator status facts, external harness orchestration for tools like `opencode`, and release-readiness docs.

Verification:

- Full suite: `./.tools/bin/bb -cp src:test -m axiom.run-tests` -- 72 tests, 401 assertions, 0 failures.
- Phase 0 demos still gate correctly after later phases: steady=0, stall=1, corrupt=1, recover=0.
- Phase 2 ladder rungs perturb observably: `:reseed` command, `:reframe` prompt index, `:escalate-model` model index, bounded by `:no-convergence` halt.
- Phase 3 produces status summaries, halt bundles, and optional notifications.
- Phase 4 adds `configs/` examples, vector `:axiom-bundle` composition, and opt-in `:hot-reload true` config swaps.
- Phase 5 adds stable failure taxonomy and forensic artifact summaries for dogfood runs.
- Phase 6 exposes pure operator facts and richer `status` output.
- Phase 7 supervises external harnesses such as `opencode` while still trusting only observed world state.
- Phase 8 adds config/reference and release checklist docs.

**Still deferred:** distributed/networked runs, GUI, cross-run learning, openspeq permanent-record merge without explicit approval, and a static Go port unless distribution pressure proves Babashka is not enough.
