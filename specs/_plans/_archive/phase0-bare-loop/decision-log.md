# Decision Log -- Phase 0

## D1: EDN config DSL over code-as-config

**Decision:** Config is pure EDN; predicates/observers expressed as data interpreted by a small DSL.
**Alternatives:** `.clj` config files returning maps with real fns (more powerful, faster to build).
**Rationale:** Mission mandates "EDN, data not code-as-config ceremony". Data config is inspectable, sandboxable, and validates. The DSL is small (8 operators + parse types). Power gap is acceptable for Phase 0.
**Revisit:** Phase 3 if the DSL proves too restrictive -- consider an escape hatch.

## D2: EDN logs over JSON

**Decision:** Per-iteration logs are EDN (`.edn`).
**Alternatives:** JSON (jq-friendly).
**Rationale:** `clojure.data.json` is NOT bundled in Babashka (verified empirically); cheshire not available by default. EDN is native, zero-dep, round-trips into Clojure tooling. Mission already chose EDN as the data format.
**Revisit:** Phase 3 observability -- add a JSON exporter if external tooling needs it.

## D3: Git as checkpoint, filesystem as fallback

**Decision:** Checkpoint = git tag; rollback = `reset --hard` to tag.
**Alternatives:** Filesystem snapshot (cp/tar).
**Rationale:** Git is ubiquitous in target repos, reversible; mission lists it as preferred. Fallback snapshot deferred.
**Revisit:** Phase 1 -- add snapshot fallback for non-git repos.

## D4: Pure `decide` + side-effecting `run!`

**Decision:** Core exposes a pure `decide` fn `(config, world, state) -> action`, and a separate `run!` performing I/O.
**Rationale:** Mission: "the `(config, world) -> decision` function is tested independently of any real agent." Pure core = trivial unit tests + halt determinism.

## D5: Minimal perturbation in Phase 0

**Decision:** Phase 0 ladder is `[:retry :halt]` only -- no rollback/reseed/reframe.
**Rationale:** Rollback (D3) and escalation are Phase 1/2 scope. Phase 0 must prove the loop; a real ladder would muddy the demo.
