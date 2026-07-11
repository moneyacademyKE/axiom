# Learnings and Patterns

## 1. Codebase Constraints: Limiting File Size (<250 LOC)
* **Problem:** Large files weave together different logical concerns (e.g., config loading vs. DSL evaluation; side-effecting shell executions vs. pure decision models).
* **Pattern:**
  - Separate **pure logic** from **I/O / side-effects**. Pure functions are extremely compact, highly testable, and have zero dependencies on environment state.
  - In Clojure, this means separating pure functional state transitions (`axiom.decision`) and parser DSLs (`axiom.predicates`) from orchestrating loops (`axiom.core`) and filesystem loading (`axiom.config`).

## 2. Decoupled Architecture in Run Loops
* **Pattern:** Keep the core run loop purely operational:
  - Loop only handles concurrency lock, observation, action dispatch, Git rollbacks, and file checks.
  - Delegate logic checks to `decision/decide`, which returns a pure action signature map (`{:type :act}`, `{:type :rollback}`, `{:type :halt}`). This ensures the runner behaves deterministically and remains testable.

## 3. Test Alignment for Dynamically Discovered Configs
* **Problem:** Hardcoding list assertions for files dynamically scanned from a directory (e.g. `configs/dogfood/`) results in test breakage when new files are introduced (e.g., untracked dogfood files created by separate runs).
* **Pattern:** Ensure test assertions either:
  - Dynamically assert properties of discovered files (e.g. schema, validity) without asserting the exact list of file names, OR
  - Explicitly keep the expected file list in sync with the project's documented rungs to prevent regression suite noise.

## 4. Separation of Automation Tooling from Execution Core
* **Problem:** Integrating documentation management or workspace layout utilities (e.g., plan merging/archiving) directly into the core runner engine increases codebase complexity and violates single-responsibility principles.
* **Pattern:** Implement workspace helper scripts as distinct Babashka namespaces (e.g., `axiom.promote`) invoked via `bb.edn` tasks. Keep the entry point `-main` robust by handling both unpacked command line arguments and packed lists of arguments, while maintaining a pure execution engine at the core.

