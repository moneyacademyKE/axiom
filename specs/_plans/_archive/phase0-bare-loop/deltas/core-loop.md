# Delta: Core Loop Behavior

> Staged spec for the Phase 0 loop. Promoted to permanent spec on record.

## Capability: `run!`

The entry point loads an EDN config and drives the loop until the goal is met, an axiom is violated, or the stall budget is exhausted.

### Scenario: goal fulfilled
- **Given** a config whose goal is `level-count >= 3`
- **And** the act creates one level per invocation
- **When** the loop runs
- **Then** it acts, re-observes, detects monotonic progress
- **And** exits 0 once `level-count >= 3`

### Scenario: stall budget exhausted
- **Given** a config with `stall-after 2`
- **And** an act that changes nothing
- **When** progress is unchanged for 2 consecutive iterations
- **Then** the loop halts with reason `:stall`
- **And** exits non-zero
- **And** logs the stall

### Scenario: integrity violation halts immediately
- **Given** an integrity check `build-ok = "pass"`
- **When** an act causes `build-ok = "fail"`
- **Then** the loop halts before any further act
- **With** reason `:integrity`
- **And** exits non-zero

### Scenario: resumability
- **Given** the loop is killed mid-run
- **When** it restarts
- **Then** it reconstructs state solely from the world (disk)
- **And** does not rely on any in-memory checkpoint

## Capability: `eval-pred` (predicate DSL)

Pure interpreter over a world map.

### Operators
`:>= :<= := :> :< :not :and :or :exists`

### World access
`{:op :>= :ref :level-count :value 3}` reads `(world :level-count)`

### Scenario: comparison
- **Given** world `{level-count 5}`
- **Then** `{:op :>= :ref :level-count :value 3}` evaluates true
- **And** `{:op := :ref :level-count :value 3}` evaluates false

### Scenario: boolean composition
- **Given** world `{build-ok "fail"}`
- **Then** `{:op :not :expr {:op := :ref :build-ok :value "pass"}}` evaluates true

## Capability: `build-world` (observers)

Runs configured shell commands and produces a world map.

### Parse types
`:int :string :bool :lines :first-line :last-line`

### Scenario: integer parse
- **Given** observer `{:sh "echo 42" :parse :int}`
- **Then** world contains `{:that-key 42}` as a number
