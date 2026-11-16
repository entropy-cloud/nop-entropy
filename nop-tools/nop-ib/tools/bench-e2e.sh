#!/usr/bin/env bash
# bench-e2e.sh — nop-ib end-to-end wall-clock harness (roadmap Wave 7, Phase 1)
#
# Usage: bench-e2e.sh <projectDir> <moduleArtifactId> [repeats]
#   projectDir       external Maven project root (must have .nop-ib model, per runbook)
#   moduleArtifactId module to test (surefire-active; skipTests modules are skipped by fact)
#   repeats          warm repetitions per variant (default 3)
#
# Variants:
#   mvn         mvn test -pl :module (lifecycle oracle, cold path)
#   ib          ib test -pl :module (junit-fork fast path)
#   ib-maven    ib test -pl :module --runner maven (surefire delegation)
#
# The FIRST run of each variant is recorded as "cold" (JVM/page-cache warm but
# command fresh), the following repeats as "warm". Output: one JSON object per
# run on stdout, plus a summary file _tmp/bench/<module>.json next to the
# project. Honesty: this harness measures wall clock only — correctness must be
# judged separately by the parity runbook (zero-fake-green is not derivable
# from timing data).
set -euo pipefail

PROJECT_DIR="$1"
MODULE="$2"
REPEATS="${3:-3}"
IB_HOME="${IB_HOME:?set IB_HOME to the nop-entropy worktree root}"

CP="$IB_HOME/nop-tools/nop-ib/target/classes:$(cat "$IB_HOME/nop-tools/nop-ib/target/ib-cp.txt")"
OUT_DIR="$PROJECT_DIR/_tmp/bench"
mkdir -p "$OUT_DIR"

now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

run_timed() {
  local label="$1"; shift
  local t0 t1 rc
  t0=$(now_ms)
  set +e
  "$@" > "$OUT_DIR/${label}.log" 2>&1
  rc=$?
  set -e
  t1=$(now_ms)
  echo "$t0 $t1 $rc"
}

result_line() { # variant pass index ms exit
  printf '{"project":"%s","module":"%s","variant":"%s","pass":"%s","ms":%s,"exit":%s}\n' \
    "$(basename "$PROJECT_DIR")" "$MODULE" "$1" "$2" "$3" "$4"
}

RESULTS=""

echo "== mvn test (oracle) =="
for i in "cold" $(seq 1 "$REPEATS"); do
  read -r _ _ ms rc <<< "$(run_timed "mvn-$i" mvn -q test -pl ":$MODULE" -Denforcer.skip=true -Dmaven.test.failure.ignore=true)"
  line=$(result_line mvn "$i" "$ms" "$rc"); echo "$line"; RESULTS="$RESULTS$line"$'\n'
done

echo "== ib test (junit-fork fast path) =="
for i in "cold" $(seq 1 "$REPEATS"); do
  read -r _ _ ms rc <<< "$(run_timed "ib-$i" java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain test -pl ":$MODULE")"
  line=$(result_line ib "$i" "$ms" "$rc"); echo "$line"; RESULTS="$RESULTS$line"$'\n'
done

echo "== ib test --runner maven (surefire delegation) =="
for i in "cold" $(seq 1 "$REPEATS"); do
  read -r _ _ ms rc <<< "$(run_timed "ibmaven-$i" java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain test -pl ":$MODULE" --runner maven)"
  line=$(result_line ibmaven "$i" "$ms" "$rc"); echo "$line"; RESULTS="$RESULTS$line"$'\n'
done

echo "$RESULTS" | grep -v '^$' > "$OUT_DIR/$MODULE.json"
echo "summary written: $OUT_DIR/$MODULE.json"
