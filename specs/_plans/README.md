# Plans

Staged plan documents live here (one per proposed change), following the openspeq
`mission -> plan -> implement -> record` workflow. A plan is staged here before
implementation; once accepted, its outcome is recorded in `../decision-log.md`.

## Naming

`<phase>-<slug>` folder containing `plan.md`, an optional `adr.md` template, and optional specifications in a `deltas/` subdirectory.

## Automation Tooling

To automate the promotion of a plan, run:
```bash
bb promote <phase-slug>
```

This task:
1. Validates that `specs/_plans/<phase-slug>` exists and contains `plan.md`.
2. Scans `specs/decision-log.md` to auto-assign the next ADR index (incrementing the highest parsed index).
3. Prepends the content of `adr.md` (if present) to the decision log, replacing any `ADR-00NN` or `ADR-XXXX` placeholders with the assigned index.
4. Copies any files in `deltas/` to the permanent specification directory `specs/specs/`.
5. Archives the plan folder by moving it to `specs/_plans/_archive/<phase-slug>`.

## Status legend

- `proposed` -- drafted, not yet started
- `in-progress` -- under implementation
- `accepted` -- merged into decision-log + mission as needed
- `rejected` -- abandoned; keep the doc with a reason
