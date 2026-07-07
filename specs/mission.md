# Axiom -- Mission

> The durable north-star. Any plan, change, or decision must be justifiable against this mission. If it isn't, either the work is wrong or this mission is stale.

## Purpose

Axiom is a general-purpose **autonomous goal-fulfillment runner** -- a supervisor over an unreliable AI agent. You give it a machine-checkable **goal** and a set of **integrity invariants** (its axioms). It repeatedly spawns the agent to make progress, re-derives the *real* world state after every action, and decides whether to continue, retry, or escalate -- all while guaranteeing the axioms hold.

It exists to kill three failure modes that plague hand-rolled autonomous loops: silent spinning, state poisoning, and insane identical retries. It does not replace the agent; it supervises it.

## Core Conviction

**The agent is an unreliable narrator; the filesystem is the only source of truth.** Everything else follows from that.

## Users & Capabilities

| User | Capability Axiom gives them |
|------|------------------------------|
| Automation engineers | A config-driven loop; drop in a goal, walk away |
| Solo devs running overnight jobs | Confidence it won't thrash forever or corrupt their repo |
| AI tooling devs | A reliable supervisor with structured logs and halt diagnostics |
| QA / fixture owners | Monotonic progress with a clean "done" predicate |

## What Must Always Be True (the axioms of Axiom itself)

1. **Integrity is a halt condition, not a retry condition.** A broken invariant stops the run immediately.
2. **Retries perturb.** Re-running an identical prompt on identical state is forbidden.
3. **Last-known-good is sacred.** Checkpoint before every act; roll back on stall.
4. **The loop is resumable.** Kill anywhere; state comes only from the world.
5. **The runner is simpler than the system it supervises.** It never shares the target's toolchain failure modes.

## Stack

| Concern | Choice | Why |
|---------|--------|-----|
| Language | **Babashka (Clojure)** | Fast startup, great shelling-out, data-as-config (EDN), JVM interop |
| Distribution | Babashka script (v1); optional Go static binary (Phase 4) | Glue tool vs. shareable binary |
| Checkpoint | **Git** (preferred) + filesystem snapshot fallback | Ubiquitous, cheap, reversible |
| Concurrency | `flock`-based exclusive lock | Single-host, single-agent by design |
| Config | **EDN** (data, not code-as-config ceremony) | Composable, inspectable, pure-function interpreter |

## Testing Strategy

- **Loop logic is pure and unit-tested.** The `(config, world-state) -> decision` function is tested independently of any real agent.
- **Fake agents for integration tests.** A no-op agent, a corrupting agent, a thrashing agent, and a steadily-progressing agent exercise every branch.
- **Each phase is testable before being trusted.** Phase N must pass its suite before Phase N+1 starts.
- **Halt determinism:** given identical inputs, a halt always occurs at the same iteration with the same reason.

## Operational Constraints

- Single host, single agent per run.
- Agent sandboxed to the repo directory; no external egress during a run.
- No destructive operation targets anything but an Axiom-created checkpoint.
- Every run is stateless w.r.t. other runs; continuity lives on disk, not in memory.

## Success Definition

Axiom succeeds when a user can hand it a fuzzy-sounding goal reduced to a predicate + progress signature, walk away, and trust that it will either **fulfill the goal** or **halt with a diagnostic bundle that names the blocker** -- and that it will never corrupt their state or spin forever trying.

---

*This mission is the contract. Scope changes require a [decision-log](decision-log.md) entry.*
