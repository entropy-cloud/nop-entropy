#!/bin/bash

# nop-cli - Nop CLI command wrapper
# Usage: nop-cli [arguments...]

SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_PATH="$(readlink "$SCRIPT_PATH")"
done
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR_PATH="$PROJECT_ROOT/nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: nop-cli jar not found at: $JAR_PATH"
    echo ""
    echo "Please build the project first:"
    echo "  mvn clean install -DskipTests -T 1C"
    exit 1
fi

exec java -jar "$JAR_PATH" "$@"
