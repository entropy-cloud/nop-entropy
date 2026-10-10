#!/bin/bash

# nop-cli - Nop CLI command wrapper
# Usage: nop-cli [arguments...]
#
# Resolution order:
#   1. the copy installed by nop-entropy tools/install-cli.sh (~/.nop/cli)
#   2. the uber-jar built in this repo
#
# (1) wins so that a deleted or replaced worktree does not break a machine-wide
# `nop-cli`. The jar name is globbed rather than hardcoded because nop-cli pins
# its own version independently of the reactor.

set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_PATH="$(readlink "$SCRIPT_PATH")"
done
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

INSTALLED_JAR="${NOP_CLI_HOME:-$HOME/.nop/cli}/nop-cli.jar"
if [ -f "$INSTALLED_JAR" ]; then
    exec "$JAVA" ${NOP_CLI_OPTS:-} -jar "$INSTALLED_JAR" "$@"
fi

# Pick the uber-jar, skipping the Quarkus -runner jar and the auxiliary
# sources/tests/javadoc jars.
JAR=""
for candidate in "$PROJECT_ROOT"/nop-runner/nop-cli/target/nop-cli-*.jar; do
    case "$candidate" in
        *-sources.jar|*-tests.jar|*-javadoc.jar|*-runner.jar|original-*) continue ;;
    esac
    [ -f "$candidate" ] || continue
    JAR="$candidate"
    break
done

if [ -z "$JAR" ]; then
    echo "Error: nop-cli jar not found. Looked in:" >&2
    echo "  $INSTALLED_JAR" >&2
    echo "  $PROJECT_ROOT/nop-runner/nop-cli/target/" >&2
    echo "" >&2
    echo "Install it globally, or build it here:" >&2
    echo "  bash $PROJECT_ROOT/tools/install-cli.sh" >&2
    echo "  ./mvnw clean install -DskipTests -T 1C" >&2
    exit 1
fi

exec "$JAVA" ${NOP_CLI_OPTS:-} -jar "$JAR" "$@"