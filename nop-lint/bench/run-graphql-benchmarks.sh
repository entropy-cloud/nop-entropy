#!/usr/bin/env bash
# The graphql-domain benchmarks (plan 10): the production rule YAML rides
# nop-lint-nop, which is only on nop-lint-graphql's test classpath, so the
# full-library and GraphQL faces run from this module's test-classes.
# IMPORTANT: install nop-lint-core (and siblings) before measuring — a stale
# .m2 jar silently benches old engine code (perf-baseline 警示段).
set -euo pipefail
cd "$(dirname "$0")/../.."

./mvnw install -DskipTests -pl nop-lint/nop-lint-core -q
./mvnw -pl nop-lint/nop-lint-graphql test-compile dependency:build-classpath \
    -Dmdep.outputFile="${PWD}/nop-lint/nop-lint-graphql/target/bench.cp" -q

java -cp "nop-lint/nop-lint-graphql/target/test-classes:nop-lint/nop-lint-graphql/target/classes:$(cat nop-lint/nop-lint-graphql/target/bench.cp)" \
    org.openjdk.jmh.Main "GraphQlLintBenchmark.graphqlCheckSource" \
    -wi 3 -w 1s -i 5 -r 1s -f 1 -prof gc -rf text -rff _tmp/graphql-bench-result.txt
java -cp "nop-lint/nop-lint-graphql/target/test-classes:nop-lint/nop-lint-graphql/target/classes:$(cat nop-lint/nop-lint-graphql/target/bench.cp)" \
    org.openjdk.jmh.Main "FullLibraryBenchmark.*" \
    -wi 3 -w 1s -i 5 -r 1s -f 1 -prof gc -rf text -rff _tmp/full-library-bench-result.txt
