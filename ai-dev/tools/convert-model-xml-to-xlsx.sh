#!/bin/bash

set -u

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
PROJECT_LIST_FILE="$SCRIPT_DIR/model-convert-projects.txt"

shopt -s nullglob

CLI_JARS=("$PROJECT_ROOT"/nop-runner/nop-cli/target/nop-cli-*.jar)
if [ ${#CLI_JARS[@]} -eq 0 ]; then
    echo "ERROR: nop-cli jar not found under nop-runner/nop-cli/target" >&2
    exit 1
fi
CLI_JAR="${CLI_JARS[0]}"

if [ ! -f "$PROJECT_LIST_FILE" ]; then
    echo "ERROR: project list not found: $PROJECT_LIST_FILE" >&2
    exit 1
fi

to_rel() {
    local path="$1"
    path="${path#"$PROJECT_ROOT"/}"
    printf '%s\n' "${path//\\//}"
}

is_convertible_file() {
    local file_name="$1"
    case "$file_name" in
        app.orm.xml|_app.orm.xml)
            return 1
            ;;
        *.orm.xml|*.api.xml)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

output_file_for() {
    local input_file="$1"
    case "$input_file" in
        *.orm.xml)
            printf '%s\n' "${input_file%.orm.xml}.orm.xlsx"
            ;;
        *.api.xml)
            printf '%s\n' "${input_file%.api.xml}.api.xlsx"
            ;;
        *)
            return 1
            ;;
    esac
}

PROJECT_DIRS=()
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%%#*}"
    line="${line%$'\r'}"
    if [ -z "$line" ]; then
        continue
    fi
    PROJECT_DIRS+=("$line")
done < "$PROJECT_LIST_FILE"

if [ ${#PROJECT_DIRS[@]} -eq 0 ]; then
    echo "ERROR: no project dirs configured in $(to_rel "$PROJECT_LIST_FILE")" >&2
    exit 1
fi

JOBS=()
for dir in "${PROJECT_DIRS[@]}"; do
    model_dir="$PROJECT_ROOT/$dir/model"
    if [ ! -d "$model_dir" ]; then
        continue
    fi

    for input_file in "$model_dir"/*.orm.xml "$model_dir"/*.api.xml; do
        file_name=$(basename "$input_file")
        if ! is_convertible_file "$file_name"; then
            continue
        fi
        output_file=$(output_file_for "$input_file") || continue
        JOBS+=("$input_file|$output_file")
    done
done

if [ ${#JOBS[@]} -eq 0 ]; then
    echo "ERROR: no source model/*.orm.xml or model/*.api.xml files matched the configured project list" >&2
    exit 1
fi

echo "Configured project dirs: ${#PROJECT_DIRS[@]}"
for dir in "${PROJECT_DIRS[@]}"; do
    echo "- $dir"
done

echo ""
echo "Planned conversions: ${#JOBS[@]}"
for job in "${JOBS[@]}"; do
    IFS='|' read -r input_file output_file <<< "$job"
    echo "- $(to_rel "$input_file") -> $(to_rel "$output_file")"
done

success_count=0
failures=()

for job in "${JOBS[@]}"; do
    IFS='|' read -r input_file output_file <<< "$job"
    echo ""
    echo "Converting $(to_rel "$input_file")"

    command_output=$(java -jar "$CLI_JAR" convert "$input_file" "-o=$output_file" 2>&1)
    status=$?

    if [ $status -eq 0 ] && [ -f "$output_file" ]; then
        success_count=$((success_count + 1))
        echo "OK: $(to_rel "$output_file")"
        continue
    fi

    echo "FAIL: $(to_rel "$input_file")" >&2
    if [ -n "$command_output" ]; then
        printf '%s\n' "$command_output" >&2
    fi
    failures+=("$(to_rel "$input_file") (exit=$status)")
done

echo ""
echo "Summary: $success_count/${#JOBS[@]} conversions succeeded"

if [ ${#failures[@]} -gt 0 ]; then
    echo "Failed conversions:" >&2
    for failure in "${failures[@]}"; do
        echo "- $failure" >&2
    done
    exit 1
fi
