#!/usr/bin/env bash
# Reset the Phase 1 rollback-recovery demo workspace.
# Initializes a FRESH git repo so checkpoints/rollbacks are REAL --
# git reset --hard restores byte-identical committed state.
set -euo pipefail
cd "$(dirname "$0")"

# Clean slate: remove all generated state including the git repo itself
# so every run starts from a deterministic baseline.
rm -f level_*.txt calls.txt .axiom-marker .axiom.lock
rm -rf .git .axiom-logs-*

# Integrity marker -- the axiom the agent must never violate.
echo "recover-demo" > .axiom-marker

# Gitignore: the call counter, lock, and logs must SURVIVE rollbacks.
# git reset --hard only touches tracked files, so gitignored state
# persists across a rollback -- this is how the agent "remembers" it
# already stalled and should now produce fresh output on retry.
cat > .gitignore <<'EOF'
calls.txt
.axiom.lock
.axiom-logs-*
EOF

# Fresh git repo with a committed known-good baseline.
git init -q
git config user.email "axiom@demo"
git config user.name "axiom-demo"
git add -A
git commit -q -m "baseline: known-good state"
echo "recover demo workspace reset (git-backed)"
