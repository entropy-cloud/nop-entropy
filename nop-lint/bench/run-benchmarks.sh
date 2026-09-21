#!/usr/bin/env bash
# Builds the test classpath and runs the nop-lint baseline benchmarks from
# the repo root (results land in _tmp/lint-bench-result.txt). Mirrors the
# nop-treesitter bench/run-c-reference.sh role.
set -euo pipefail
cd "$(dirname "$0")/../.."

./mvnw -pl nop-lint/nop-lint-core -am test-compile -q -DskipTests

CP_FILE=nop-lint/nop-lint-core/target/bench.cp
./mvnw -pl nop-lint/nop-lint-core dependency:build-classpath -Dmdep.outputFile="${PWD}/$CP_FILE" -q

java -cp "nop-lint/nop-lint-core/target/test-classes:nop-lint/nop-lint-core/target/classes:$(cat "$CP_FILE")" \
    io.nop.lint.core.bench.LintBenchmarkRunner
