#!/usr/bin/env bash
# Builds and runs the C tree-sitter reference harness on the same generated
# inputs and the same parse+serialize work as the JMH benchmarks, then runs
# the Java side, printing ops/s for both. Requires ~/sources/treesitter
# (read-only reference checkout) and a C compiler.
set -euo pipefail

TS=~/sources/treesitter/tree-sitter
G=~/sources/treesitter/grammars
if [[ ! -f "$TS/lib/src/lib.c" ]]; then
  echo "C reference not available at ~/sources/treesitter" >&2
  exit 1
fi

cd "$(dirname "$0")/../.."   # repo root

mkdir -p _tmp/ts-bench/inputs
CP_FILE=nop-treesitter/_tmp/ts-bench-test-cp.txt
./mvnw -q -pl nop-treesitter dependency:build-classpath -Dmdep.outputFile=$CP_FILE -Dmdep.includeScope=test
java -cp "nop-treesitter/target/classes:nop-treesitter/target/test-classes:$(cat $CP_FILE)" io.nop.treesitter.bench.BenchSourcesDump _tmp/ts-bench/inputs

echo "== Java (JMH full run) =="
java -cp "nop-treesitter/target/classes:nop-treesitter/target/test-classes:$(cat $CP_FILE)" io.nop.treesitter.bench.TreeSitterBenchmarkRunner

echo "== C reference =="
cc -O2 -I "$TS/lib/src" -I "$TS/lib/include" \
   -I "$G/tree-sitter-json/src" -I "$G/tree-sitter-java/src" \
   nop-treesitter/bench/c_harness.c "$TS/lib/src/lib.c" \
   "$G/tree-sitter-json/src/parser.c" "$G/tree-sitter-java/src/parser.c" \
   -o _tmp/ts-bench/c_ref
(cd . && ./_tmp/ts-bench/c_ref)
