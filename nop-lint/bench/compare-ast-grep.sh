#!/usr/bin/env bash
# Same-rule comparison: nop-lint end-to-end (warm JVM) vs ast-grep CLI
# (cold process — the asymmetry is documented in
# nop-lint/docs/perf-baseline.md). Prints both timings; any sg failure or a
# zero-match corpus is fatal (no silent skips).
set -euo pipefail
cd "$(dirname "$0")/../.."

CORPUS_DIR=_tmp/agrep-corpus
COPIES=200
RULE_FILE=$CORPUS_DIR/rule.yml

rm -rf "$CORPUS_DIR"
mkdir -p "$CORPUS_DIR"

for i in $(seq 1 "$COPIES"); do
    cp -f "$(dirname "$0")/corpus/OrderService.java" "$CORPUS_DIR/OrderService$i.java"
done

cat > "$RULE_FILE" <<'YAML'
id: bench-raw-exception
language: Java
severity: warning  # non-error severity keeps sg exit code at 0
rule:
  pattern: throw new RuntimeException($$$ARGS)
YAML

echo "== ast-grep version =="
ast-grep --version

echo "== match sanity (must be > 0) =="
MATCH_COUNT=$(ast-grep scan --rule "$RULE_FILE" "$CORPUS_DIR" | grep -c 'bench-raw-exception' || true)
echo "matches: $MATCH_COUNT"
if [ "$MATCH_COUNT" -lt 1 ]; then
    echo "FATAL: corpus produced zero matches — timing would be meaningless" >&2
    exit 1
fi

echo "== sg scan over $COPIES files (timing run) =="
/usr/bin/time -p ast-grep scan --rule "$RULE_FILE" "$CORPUS_DIR" > /dev/null 2> /tmp/sg-time.txt || {
    echo "sg scan failed"; cat /tmp/sg-time.txt; exit 1; }
grep real /tmp/sg-time.txt

echo "done (compare against the EndToEnd numbers in nop-lint/docs/perf-baseline.md)"
