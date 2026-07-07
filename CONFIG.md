# Axiom Config Reference

Axiom configs are EDN data, not code. The current config schema is **v1**: small, interpreted, and validated at load time before the runner re-derives truth from the supervised world every iteration.

## Minimal shape

```clojure
{:name "my-run"
 :workdir "."
 :lock ".axiom.lock"
 :log-dir ".axiom-logs"
 :observers {:tests-ok {:sh "bb test >/dev/null 2>&1; echo $?" :parse :int}}
 :goal {:op := :ref :tests-ok :value 0}
 :progress :tests-ok
 :integrity []
 :act {:sh "echo do the work"}}
```

## Required keys

| Key | Meaning |
|---|---|
| `:name` | Human-readable run name. |
| `:observers` | Map of world facts to shell observers. |
| `:goal` | Predicate that means the theorem is fulfilled. |
| `:progress` | Signature key/vector used to detect movement. |
| `:act` | Shell or harness act to invoke when the goal is not met. |

## Common optional keys

| Key | Meaning |
|---|---|
| `:workdir` | Directory where observers and acts run. Defaults to `.`. |
| `:lock` | Lockfile path. Defaults to `<workdir>/.axiom.lock`. |
| `:log-dir` | Directory for `events.log`, `iter-NNN.edn`, and `halt-bundle.edn`. |
| `:integrity` | Axiom predicates. Violation halts immediately. |
| `:axiom-bundle` | Keyword or vector of keywords, e.g. `:clj-build` or `[:rust-build :clean-tree]`. |
| `:stall-after` | No-progress budget. |
| `:thrash-after` | No-convergence budget. |
| `:max-rollbacks` | Rollback recovery budget. |
| `:escalations` | Ladder, default `[:rollback :reseed :reframe :escalate-model]`. |
| `:models` | Model names used by `{{model}}` and harness profiles. |
| `:hot-reload` | `true` enables config reload between iterations. |
| `:notify` | Optional halt notifier config. |
| `:harnesses` | Custom external harness profiles. |

## Predicate grammar

Predicates are pure data:

- Bare keyword: truthy world lookup, e.g. `:ready?`
- Comparisons: `:>=`, `:<=`, `:=`, `:not=`, `:>`, `:<`
- Existence: `{:op :exists :ref :file}`
- Boolean composition: `:not`, `:and`, `:or`

Numeric comparisons fail safe on missing/non-numeric refs: false, not crash.

## Observer parse types

`build-world` supports: `:int`, `:string`, `:bool`, `:lines`, `:first-line`, `:last-line`.

## Acts

### Shell act

```clojure
{:act {:sh "./fix.sh {{attempt}} {{model}}" :timeout 120000}}
```

Shell acts use `bash -c`, so keep config files trusted and local.

### Harness act, e.g. opencode

```clojure
{:models ["cheap" "smart"]
 :act {:harness :opencode
       :prompt "Make tests pass. Current failures: {{tests-output}}"}}
```

Axiom builds an argv vector from the harness profile, invokes the external CLI, then ignores self-report and re-observes the world. A harness saying "done" means nothing unless the predicates pass.

## Built-in bundles

- `:gleam-build`
- `:rust-build`
- `:clj-build`
- `:node-build`
- `:python-build`
- `:go-build`
- `:clean-tree`

Unknown bundles throw. Silent safety drops are forbidden.

## Security defaults

- Config is EDN data; no eval.
- Predicates are interpreted, not executed.
- Harnesses are argv-based, not shell-string based.
- Integrity failure halts before another act.
- Hot-reload validates the changed config before swapping.
- External harness output is advisory only.
