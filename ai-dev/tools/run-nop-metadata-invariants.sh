#!/usr/bin/env bash
# ============================================================
# run-nop-metadata-invariants — nop-metadata invariant hard-gate aggregator
# ============================================================
# Runs all 5 nop-metadata invariant guards as a fail-fast chain.
# Any single guard non-zero exit ⇒ overall non-zero exit (hard CI gate).
#
# Guards:
#   1. INV-SILENT-SWALLOW  — check-silent-swallow.mjs (125 catch blocks / 45 files,
#                            comment-stripped scanner count, live 2026-08-15)
#   2. INV-UK              — check-orm-unique-key-constraint.mjs (37 unique-key)
#   3. INV-SENSITIVE       — check-sensitive-literal-leak.mjs (error/log literals)
#   4. INV-LIMIT           — TestLimitNegativeValueInvariant (4 limit-taking entries)
#   5. INV-SILENT-WRONG-RESULT — check-silent-wrong-result.mjs, 1 scanner x 5 rules
#                            (INV-LOCALE / INV-NARROW / INV-CONTAINS-CLASSIFY /
#                             INV-DELIM-KEY / INV-BIGDEC; Cycle 2, plan
#                             2026-08-15-0820-1). Mode b: snapshot reconciliation —
#                            exits green iff every hit key's current count <= baseline
#                            count. Baseline shrinks only via I3' adjudication terminal
#                            states or I4' fixes; any new hit key or count increase = red.
#
# Usage:
#   ./run-nop-metadata-invariants.sh            # local (uses ./mvnw)
#   MVN=mvn ./run-nop-metadata-invariants.sh    # CI (uses mvn)
#
# Maven command is selected via the MVN env var (default: ./mvnw).
# Node scanners require ai-dev/tools/node_modules (run `pnpm install` in ai-dev/tools first).
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
MVN="${MVN:-./mvnw}"

CYCLE2_BASELINE="$PROJECT_ROOT/ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json"

cd "$PROJECT_ROOT"

# Verify Node tooling is installed for the 4 scanners.
if [ ! -d "$SCRIPT_DIR/node_modules" ]; then
  echo "Error: ai-dev/tools/node_modules not found. Run 'pnpm install' in ai-dev/tools first." >&2
  exit 1
fi

echo "==> [1/5] INV-SILENT-SWALLOW: check-silent-swallow.mjs"
node "$SCRIPT_DIR/check-silent-swallow.mjs" --module nop-metadata

echo "==> [2/5] INV-UK: check-orm-unique-key-constraint.mjs"
node "$SCRIPT_DIR/check-orm-unique-key-constraint.mjs" --module nop-metadata

echo "==> [3/5] INV-SENSITIVE: check-sensitive-literal-leak.mjs"
node "$SCRIPT_DIR/check-sensitive-literal-leak.mjs" --module nop-metadata

echo "==> [4/5] INV-LIMIT: TestLimitNegativeValueInvariant"
# INV-LIMIT also runs in the default surefire (build job). Re-invoking it here is an
# intentional defense-in-depth second layer: this aggregator is the fail-fast CI gate
# (run on every push/PR via the invariant-gate job), independent of the build job result.
"$MVN" test -pl nop-metadata/nop-metadata-service \
  -Dtest=TestLimitNegativeValueInvariant \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -q

echo "==> [5/5] INV-SILENT-WRONG-RESULT: check-silent-wrong-result.mjs (mode b, baseline reconciliation)"
# Exit 0 iff hit-key set is a count-aware subset of the baseline snapshot (mode b).
# No continue-on-error / || true: any new or increased hit key turns CI red.
node "$SCRIPT_DIR/check-silent-wrong-result.mjs" --module nop-metadata --baseline "$CYCLE2_BASELINE"

echo "==> All 5 nop-metadata invariant guards passed (guards 1-4 zero hits; guard 5 within baseline)."
