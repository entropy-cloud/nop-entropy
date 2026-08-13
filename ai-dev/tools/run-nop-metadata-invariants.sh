#!/usr/bin/env bash
# ============================================================
# run-nop-metadata-invariants — nop-metadata invariant hard-gate aggregator
# ============================================================
# Runs all 4 nop-metadata invariant guards as a fail-fast chain.
# Any single guard non-zero exit ⇒ overall non-zero exit (hard CI gate).
#
# Guards:
#   1. INV-SILENT-SWALLOW  — check-silent-swallow.mjs (130 catch blocks)
#   2. INV-UK              — check-orm-unique-key-constraint.mjs (37 unique-key)
#   3. INV-SENSITIVE       — check-sensitive-literal-leak.mjs (error/log literals)
#   4. INV-LIMIT           — TestLimitNegativeValueInvariant (4 limit-taking entries)
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

cd "$PROJECT_ROOT"

# Verify Node tooling is installed for the 3 scanners.
if [ ! -d "$SCRIPT_DIR/node_modules" ]; then
  echo "Error: ai-dev/tools/node_modules not found. Run 'pnpm install' in ai-dev/tools first." >&2
  exit 1
fi

echo "==> [1/4] INV-SILENT-SWALLOW: check-silent-swallow.mjs"
node "$SCRIPT_DIR/check-silent-swallow.mjs" --module nop-metadata

echo "==> [2/4] INV-UK: check-orm-unique-key-constraint.mjs"
node "$SCRIPT_DIR/check-orm-unique-key-constraint.mjs" --module nop-metadata

echo "==> [3/4] INV-SENSITIVE: check-sensitive-literal-leak.mjs"
node "$SCRIPT_DIR/check-sensitive-literal-leak.mjs" --module nop-metadata

echo "==> [4/4] INV-LIMIT: TestLimitNegativeValueInvariant"
# INV-LIMIT is excluded from default surefire (pom.xml <excludes>); invoked explicitly here.
"$MVN" test -pl nop-metadata/nop-metadata-service \
  -Dtest=TestLimitNegativeValueInvariant \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -q

echo "==> All 4 nop-metadata invariant guards passed (zero hits)."
