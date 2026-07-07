# Axiom -- Release Readiness

> Current release target: **developer preview v0.1**. Axiom is runnable and test-covered, but should still supervise disposable or git-backed workspaces until more dogfood runs harden the edge cases.

## Install

Axiom is a Babashka program. The repository vendors a local `bb` binary under `.tools/bin/bb` for reproducible demos and tests.

```sh
cd /path/to/axiom
./.tools/bin/bb -cp src:test -m axiom.run-tests
```

Optional system install:

```sh
brew install borkdude/brew/babashka
bb -cp src:test -m axiom.run-tests
```

## Run

```sh
./.tools/bin/bb axiom.clj examples/steady.edn
./.tools/bin/bb axiom.clj status examples/stall.edn --last 10
```

Exit codes:

| Code | Meaning |
|---:|---|
| `0` | Goal fulfilled, or read-only `status` completed |
| `1` | Supervised run halted (`:stall`, `:integrity`, `:no-convergence`, etc.) |
| `2` | Usage/config loading error |

## Config schema v1

Axiom config is EDN data. Schema v1 is intentionally small and interpreted -- no `eval`, no executable config.

Required keys:

| Key | Shape | Purpose |
|---|---|---|
| `:name` | string | Operator-facing run name |
| `:observers` | vector of observer maps | Re-derive authoritative world state |
| `:goal` | predicate data | The theorem Axiom is trying to make true |
| `:progress` | keyword/vector/nil | Signature used to detect movement |
| `:act` | map or vector of maps | Shell or harness action templates |

Important optional keys:

| Key | Shape | Purpose |
|---|---|---|
| `:integrity` | vector of predicate maps | Axioms that must never break |
| `:axiom-bundle` | keyword or vector | Reusable integrity bundles (`:clj-build`, `:rust-build`, etc.) |
| `:stall-after` | integer | No-progress budget |
| `:thrash-after` | integer | No-convergence budget |
| `:escalations` | vector | Ladder rungs, default `[:rollback :reseed :reframe :escalate-model]` |
| `:reseed` | action map | Optional perturbation command for the reseed rung |
| `:models` | vector of strings | Model ladder for `:escalate-model` |
| `:hot-reload` | boolean | Validate and swap changed config at iteration boundaries |
| `:notify` | map | Optional halt notifier (`:noop`/`:http`/transport override) |
| `:harnesses` | map | Custom external harness argv templates |

Predicate grammar:

```edn
{:op :>= :ref :level-count :value 3}
{:op :=  :ref :build-ok    :value "pass"}
{:op :exists :ref :artifact}
{:op :and :exprs [<pred> <pred>]}
{:op :or  :exprs [<pred> <pred>]}
{:op :not :expr <pred>}
```

Action grammar:

```edn
;; shell act
{:sh "./script.sh {{attempt}} {{model}}"}

;; harness act, e.g. opencode
{:harness :opencode
 :prompt "Make {{target}} true without breaking tests; attempt={{attempt}}"}
```

## Release gate

Before tagging a release candidate:

1. Run `./.tools/bin/bb -cp src:test -m axiom.run-tests`.
2. Run the Phase 0 demos from a reset workspace: steady must exit `0`; stall/corrupt must exit `1`; recover must exit `0`.
3. Verify `bb axiom.clj status <config.edn>` can explain the last halt without re-running.
4. Dogfood at least one shell act and one harness act against disposable or git-backed workspaces.
5. Confirm every new design decision has an ADR in `specs/decision-log.md`.

## Security notes

- Harness profiles use argv vectors, not shell interpolation.
- Config is data interpreted by Axiom; predicates are not executable code.
- Axiom trusts re-derived world state, not agent/harness stdout.
- Use git-backed workspaces for meaningful rollback; outside git, rollback degrades to bounded retry.
