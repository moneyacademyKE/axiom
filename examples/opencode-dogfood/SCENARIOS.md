# Opencode Dogfood Scenarios

Axiom should supervise `opencode` as an external actor, not trust it as an oracle. These scenarios are graduated: each level raises ambiguity, invariants, and escalation pressure while keeping the runner's contract the same — observe the world, assert axioms, act through argv, re-observe, decide.

Run them against disposable repos or fixtures first. Every scenario should have its own `:workdir`, `:log-dir`, and checkpoint tag prefix so a failed dogfood run leaves useful forensic evidence instead of a mystery puddle.

| Level | Scenario | Goal theorem | Integrity axioms | Progress signature | Escalation pressure |
|---:|---|---|---|---|---|
| 1 | Red-green microfix | One intentionally failing test becomes green | Build still loads; test runner remains executable | Failing-test count | Stall should rollback once, then reseed with a narrower prompt |
| 2 | Small feature slice | A documented CLI/API behavior exists and tests pass | Existing public behavior unchanged; no dead generated files | Passing assertion count + touched-file count | Reframe after repeated cosmetic edits |
| 3 | Refactor under invariant | Split an overgrown module without changing behavior | Full suite green; public config schema unchanged | Max file LOC decreases | Escalate model if edits repeat the same structural shape |
| 4 | Cross-file integration | Add a harness config plus docs plus regression test | Config validates; harness invocation stays argv-based, never shell-string-based | New scenario test count | Reseed with concrete file targets if opencode drifts into prose-only answers |
| 5 | Adversarial self-report | Harness claims success while fixture remains wrong | Axiom trusts observers only; lying output never fulfills the goal | World-state delta, not harness stdout | Halt as `:no-convergence` after bounded repeated lies |
| 6 | Dirty-tree recovery | Actor makes partial progress and breaks an axiom | Rollback restores last-good tree; halt bundle names violated axiom | Last-good tag + clean observer values | Ladder must advance rollback → reseed → reframe before halt |

## Candidate EDN config pattern

This is the shape a concrete opencode dogfood config should use once a disposable fixture repo exists:

```clojure
{:name "opencode-red-green-microfix"
 :workdir "./examples/opencode-dogfood/fixtures/red-green"
 :lock "./examples/opencode-dogfood/fixtures/red-green/.axiom.lock"
 :log-dir "./examples/opencode-dogfood/logs/red-green"
 :models ["cheap" "smart"]
 :observers {:tests-ok {:sh "./run-tests.sh >/dev/null 2>&1 && echo pass || echo fail" :parse :string}
             :fails    {:sh "./run-tests.sh 2>&1 | grep -c 'FAIL\\|ERROR' | tr -d ' '" :parse :int}
             :git-clean {:sh "git diff --quiet && echo true || echo false" :parse :string}}
 :goal {:op := :ref :tests-ok :value "pass"}
 :axiom-bundle [:clean-tree]
 :progress [:fails :git-clean]
 :act {:harness :opencode
       :prompt "Make the fixture tests pass. Do not change the test intent. Current failing count: {{fails}}. Attempt: {{attempt}}. Model: {{model}}."}
 :checkpoint {:tag-prefix "axiom-opencode-red-green"}
 :stall-after 2
 :thrash-after 4
 :max-rollbacks 2
 :escalations [:rollback :reseed :reframe :escalate-model]}
```

## Validation checklist

1. Confirm `opencode` is on `PATH` only in the dogfood environment, not in core tests.
2. Run Axiom with a low iteration cap first: `./.tools/bin/bb axiom.clj <config.edn>`.
3. Inspect `<log-dir>/events.log` and `<log-dir>/halt-bundle.edn`; ignore harness self-report unless observers agree.
4. Reset the fixture between levels. Dogfood fixtures are stateful; stale success is fake success.
5. Promote a scenario into `configs/` only after it has a deterministic fixture and a regression test proving the runner semantics.
