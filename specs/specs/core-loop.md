# Spec: Core Loop Behavior (Phase 0)

> Permanent behavior spec. Promoted from `specs/_plans/_archive/phase0-bare-loop/deltas/core-loop.md` after validation passed. This is the contract the core loop must satisfy.

**Validation status:** ✅ VERIFIED 2026-07-07 (re-verified after nil-safety fix)
- Unit suite: 13 tests / 49 assertions / 0 failures / 0 errors (`bb test/axiom/run_tests.clj`)
- End-to-end: steady (exit 0), stall (exit 1 :stall), corrupt (exit 1 :integrity)
- Predicate robustness: numeric predicates are fail-safe on missing/non-numeric refs (no NPE); a failed observer triggers an integrity `:halt`, not a crash (ADR-0009)

---

## Capability: `run!`

Loads an EDN config and drives the loop until the goal is met, an axiom is violated, or the stall budget is exhausted.

| Scenario | Given | When | Then | Verified by |
|---|---|---|---|---|
| Goal fulfilled | goal `level-count >= 3`, act creates 1 level/call | loop runs | acts, detects monotonic progress, exits 0 at `level-count >= 3` | `examples/steady.edn` → exit 0 |
| Stall exhausted | `stall-after 2`, no-op act | progress unchanged 2× | halts `:stall`, exits non-zero | `examples/stall.edn` → exit 1 |
| Integrity halt | integrity `build-ok = "pass"` | act causes `build-ok = "fail"` | halts `:integrity` before further acts, exits non-zero | `examples/corrupt.edn` → exit 1 |
| Integrity before stall | integrity violated at `stall=1` | decide is called | halts `:integrity` not `:stall` (priority proven) | corrupt demo halted at stall=1 |
| Resumability | loop killed mid-run | restarts | reconstructs state from disk only (no in-memory checkpoint) | design-verified: every iteration calls `observe` fresh |

## Capability: `eval-pred` (predicate DSL)

Pure interpreter over a world map. Operators: `:>= :<= := :> :< :not= :not :and :or :exists`.

| Scenario | World | Predicate | Result | Verified |
|---|---|---|---|---|
| Comparison | `{level-count 5}` | `{:op :>= :ref :level-count :value 3}` | true | unit test ✓ |
| Comparison false | `{level-count 5}` | `{:op := :ref :level-count :value 3}` | false | unit test ✓ |
| Boolean NOT | `{build-ok "fail"}` | `{:op :not :expr {:op := :ref :build-ok :value "pass"}}` | true | unit test ✓ |
| AND / OR | `{level-count 5, build-ok "pass"}` | composed exprs | correct | unit test ✓ |
| Bare keyword | `{level-count 5}` | `:level-count` | truthy | unit test ✓ |

## Capability: `build-world` (observers)

Runs configured shell commands (via `bash -c`) and produces a world map. Parse types: `:int :string :bool :lines :first-line :last-line`.

| Observer | Command | Parse | Verified by |
|---|---|---|---|
| level-count | `ls level_*.txt \| wc -l` | `:int` | steady demo (1→2→3) |
| build-ok | `test -f .axiom-marker && echo pass \|\| echo fail` | `:string` | corrupt demo (pass→fail) |

Failed observers yield `nil` for their key (captured, not fatal) — integrity checks catch breakage.
