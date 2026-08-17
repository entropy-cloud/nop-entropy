#!/usr/bin/env bash
# ============================================================
# run-nop-metadata-invariants — nop-metadata invariant hard-gate aggregator
# ============================================================
# Runs all 6 nop-metadata invariant guards as a fail-fast chain.
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
#   6. INV-ERROR-PARAM     — check-error-param-consistency.mjs (Cycle 3, plan
#                            2026-08-15-1913-3 P1-6/P1-7; define-face rules added
#                            by plan 2026-08-16-0226-2 P2-10). Zero-hit hard gate:
#                            every ErrorCode description placeholder of a
#                            `new NopMetadataException(...)` throw site must have a
#                            matching `.param()` key ({error} exemption CLOSED
#                            2026-08-16 by P2-09 — missing error param now hits);
#                            every `ErrorCode.define` must declare exactly its
#                            description placeholders (ARG-value symmetric diff)
#                            and have a production consumer (dead / test-only
#                            defines are violations); variable-form error codes
#                            require `// invariant-ok:` adjudication.
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

# Verify Node tooling is installed for the 5 scanners.
if [ ! -d "$SCRIPT_DIR/node_modules" ]; then
  echo "Error: ai-dev/tools/node_modules not found. Run 'pnpm install' in ai-dev/tools first." >&2
  exit 1
fi

echo "==> [1/6] INV-SILENT-SWALLOW: check-silent-swallow.mjs"
node "$SCRIPT_DIR/check-silent-swallow.mjs" --module nop-metadata

echo "==> [2/6] INV-UK: check-orm-unique-key-constraint.mjs"
node "$SCRIPT_DIR/check-orm-unique-key-constraint.mjs" --module nop-metadata

echo "==> [3/6] INV-SENSITIVE: check-sensitive-literal-leak.mjs"
node "$SCRIPT_DIR/check-sensitive-literal-leak.mjs" --module nop-metadata

echo "==> [4/6] INV-LIMIT: TestLimitNegativeValueInvariant"
# INV-LIMIT also runs in the default surefire (build job). Re-invoking it here is an
# intentional defense-in-depth second layer: this aggregator is the fail-fast CI gate
# (run on every push/PR via the invariant-gate job), independent of the build job result.
# P1-9（plan 2026-08-15-1913-3）：4 行全部钉精确错误码——移除任一 limit 检查的变异变红。
"$MVN" test -pl nop-metadata/nop-metadata-service \
  -Dtest=TestLimitNegativeValueInvariant \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -q

echo "==> [5/6] INV-SILENT-WRONG-RESULT: check-silent-wrong-result.mjs (mode b, baseline reconciliation)"
# Exit 0 iff hit-key set is a count-aware subset of the baseline snapshot (mode b).
# No continue-on-error / || true: any new or increased hit key turns CI red.
node "$SCRIPT_DIR/check-silent-wrong-result.mjs" --module nop-metadata --baseline "$CYCLE2_BASELINE"

echo "==> [6/6] INV-ERROR-PARAM: check-error-param-consistency.mjs (zero-hit hard gate)"
# Cycle 3（plan 2026-08-15-1913-3）：识别性占位符↔.param 键一致性零命中门禁
# （豁免面 = {error} + // invariant-ok: 标注，全部显式列出无静默盲区）。
node "$SCRIPT_DIR/check-error-param-consistency.mjs" --module nop-metadata

echo "==> All 6 nop-metadata invariant guards passed (guards 1-4 zero hits; guard 5 within baseline; guard 6 zero hits)."
