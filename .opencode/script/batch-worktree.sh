#!/bin/bash

# Script to parse multi-section task file and execute opencode run for each section
# Usage: ./batch-worktree.sh <input-file>
# Usage (validate only): ./batch-worktree.sh -c <input-file>
#
# Input file format:
#   >>> feature-name [base-branch] <<<
#   argument-1
#   argument-2
#
#   >>> another-feature <<<
#   argument-1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Generate random string for unique filenames
generate_random_string() {
    local length="${1:-8}"
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
        # Windows/MinGW: use simple approach
        echo "$(date +%s%N | head -c $length | tr -d ' ')"
    else
        # Unix-like: use /dev/urandom
        head /dev/urandom | tr -dc 'a-zA-Z0-9' | head -c $length
    fi
}

# Parse arguments
VALIDATE_MODE=false
input_file=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -c)
            VALIDATE_MODE=true
            shift
            ;;
        *)
            input_file="$1"
            shift
            ;;
    esac
done

if [ -z "$input_file" ]; then
    echo "Usage: $0 [-c] <input-file>"
    echo ""
    echo "Options:"
    echo "  -c           Validate mode - check file format only (no execution)"
    echo ""
    echo "Input file should contain sections in format:"
    echo ">>> feature-name [base-branch] <<<"
    echo "argument-1"
    echo "argument-2"
    exit 1
fi

# Convert Windows path to Unix format if needed
if [[ "$input_file" =~ ^[A-Za-z]: ]]; then
    # Convert C:\path\to\file to /c/path/to/file
    input_file=$(echo "$input_file" | sed 's|^\([A-Za-z]\):|/\L\1|' | tr '\\' '/')
fi

if [[ "$input_file" != /* ]]; then
    input_file="$(pwd)/$input_file"
fi

if [ ! -f "$input_file" ]; then
    echo "Error: Input file '$input_file' not found"
    exit 1
fi

# Validate file format
validate_file() {
    local file="$1"
    echo "Validating input file format..."
    line_number=0
    has_valid_sections=0
    while IFS= read -r line || [ -n "$line" ]; do
        ((line_number++))
        # Check if line looks like a section header (starts with >>>)
        if [[ "$line" =~ ^\>\>\> ]]; then
            # Check if it's missing the <<< closing tag
            if [[ ! "$line" =~ \<\<\<$ ]]; then
                echo "❌ Error: Invalid section header at line $line_number"
                echo "   Line: $line"
                echo "   Issue: Missing '<<<' at the end"
                echo "   Expected format: >>> feature-name [base-branch] <<<"
                echo ""
                return 1
            fi
            has_valid_sections=1
        fi
    done < "$file"

    if [ $has_valid_sections -eq 0 ]; then
        echo "❌ Error: No valid section headers found in input file"
        echo "   Expected format: >>> feature-name [base-branch] <<<"
        echo ""
        return 1
    fi

    echo "✅ Format validation passed. Found sections."
    echo ""
    return 0
}

# Run validation
if ! validate_file "$input_file"; then
    exit 1
fi

# If validate mode, exit after validation
if [ "$VALIDATE_MODE" = true ]; then
    echo "✅ File validation successful. Ready for execution."
    echo ""
    echo "To execute the tasks, run:"
    echo "  $0 $input_file"
    exit 0
fi

# Parse input file and process each section
current_feature=""
current_base_branch=""
current_args=""
current_timestamp=""

while IFS= read -r line || [ -n "$line" ]; do
    # Skip empty lines and comments
    if [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]]; then
        continue
    fi

    # Check for section header: >>> feature-name [base-branch] <<<
    if [[ "$line" =~ ^\>\>\>[[:space:]]*([^[:space:]]+)[[:space:]]*([^<]*)\<\<\<$ ]]; then
        # Process previous section if exists
        if [ -n "$current_feature" ]; then
            echo "Processing feature: $current_feature"

            # Create worktree
            create_worktree_script="$SCRIPT_DIR/create-worktree.sh"
            if [ ! -f "$create_worktree_script" ]; then
                echo "Error: create-worktree.sh not found at $create_worktree_script"
            else
                # Pass TMP-prefixed name for unique directory and branch
                feature_with_timestamp="TMP-${current_timestamp}-${current_feature}"
                if [ -n "$current_base_branch" ]; then
                    "$create_worktree_script" "$feature_with_timestamp" "$current_base_branch"
                else
                    "$create_worktree_script" "$feature_with_timestamp"
                fi

                if [ $? -ne 0 ]; then
                    echo "Error: Failed to create worktree for $current_feature"
                else
                    # Get worktree path with TMP prefix
                    MAIN_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
                    WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"
                    worktree_dir="${WORKTREES_DIR}/TMP-${current_timestamp}-${current_feature}"

                    if [ -d "$worktree_dir" ]; then
                        echo "Changing to worktree directory: $worktree_dir"
                        cd "$worktree_dir" || continue

                        # Execute each argument line
                        while IFS= read -r arg_line; do
                            if [[ -z "$arg_line" || "$arg_line" =~ ^[[:space:]]*# ]]; then
                                continue
                            fi

                            echo "Executing: opencode run $arg_line"

                            # Write debug file for diagnostics
                            random_suffix=$(generate_random_string)
                            debug_file="opencode-debug-${random_suffix}.txt"
                            echo "Writing opencode input to debug file: $debug_file"
                            {
                                echo "Feature: $current_feature"
                                echo "Timestamp: $(date)"
                                echo "Arguments: $arg_line"
                                echo "---"
                                echo "$arg_line"
                            } > "$debug_file"

                            # Record start time
                            OPENCODE_START=$(date +%s)

                            {
                                echo "===================="
                                echo "Started: $(date)"
                                echo "Command: opencode run $arg_line"
                                echo "===================="
                                opencode run $arg_line
                                echo "===================="
                                echo "Ended: $(date)"
                            } 2>&1 | tee task.log

                            # Calculate elapsed time
                            OPENCODE_END=$(date +%s)
                            OPENCODE_ELAPSED=$((OPENCODE_END - OPENCODE_START))
                            OPENCODE_MINUTES=$((OPENCODE_ELAPSED / 60))
                            OPENCODE_SECONDS=$((OPENCODE_ELAPSED % 60))
                            echo "⏱️  Time elapsed: ${OPENCODE_MINUTES}m ${OPENCODE_SECONDS}s"

                            if [ $? -ne 0 ]; then
                                echo "Error: Failed to execute opencode run for $current_feature with args: $arg_line"
                            else
                                echo "Successfully executed opencode run for $current_feature with args: $arg_line"
                            fi
                        done <<< "$current_args"

                        cd - > /dev/null
                    fi
                fi
            fi
        fi

        # Start new section
        current_feature="${BASH_REMATCH[1]}"
        current_base_branch="${BASH_REMATCH[2]}"
        current_args=""

        # Generate timestamp for unique directory name
        current_timestamp=$(date +%Y%m%d-%H%M%S)

        # Trim base_branch whitespace
        current_base_branch=$(echo "$current_base_branch" | xargs)

        echo ""
        echo "Found feature: $current_feature"
        if [ -n "$current_base_branch" ]; then
            echo "  Base branch: $current_base_branch"
        fi
        echo "  Timestamp: $current_timestamp"

    elif [ -n "$current_feature" ]; then
        # Collect arguments for current feature
        current_args+="$line"$'\n'
    fi

done < "$input_file"

# Process the last section
if [ -n "$current_feature" ]; then
    echo "Processing feature: $current_feature"

    create_worktree_script="$SCRIPT_DIR/create-worktree.sh"
    if [ ! -f "$create_worktree_script" ]; then
        echo "Error: create-worktree.sh not found at $create_worktree_script"
    else
        # Pass TMP-prefixed name for unique directory and branch
        feature_with_timestamp="TMP-${current_timestamp}-${current_feature}"
        if [ -n "$current_base_branch" ]; then
            "$create_worktree_script" "$feature_with_timestamp" "$current_base_branch"
        else
            "$create_worktree_script" "$feature_with_timestamp"
        fi

        if [ $? -ne 0 ]; then
            echo "Error: Failed to create worktree for $current_feature"
        else
            # Get worktree path with TMP prefix
            MAIN_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
            WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"
            worktree_dir="${WORKTREES_DIR}/TMP-${current_timestamp}-${current_feature}"

            if [ -d "$worktree_dir" ]; then
                echo "Changing to worktree directory: $worktree_dir"
                cd "$worktree_dir" || continue

                while IFS= read -r arg_line; do
                    if [[ -z "$arg_line" || "$arg_line" =~ ^[[:space:]]*# ]]; then
                        continue
                    fi

                    echo "Executing: opencode run $arg_line"

                    # Write debug file for diagnostics
                    random_suffix=$(generate_random_string)
                    debug_file="opencode-debug-${random_suffix}.txt"
                    echo "Writing opencode input to debug file: $debug_file"
                    {
                        echo "Feature: $current_feature"
                        echo "Timestamp: $(date)"
                        echo "Arguments: $arg_line"
                        echo "---"
                        echo "$arg_line"
                    } > "$debug_file"

                    # Record start time
                    OPENCODE_START=$(date +%s)

                    {
                        echo "===================="
                        echo "Started: $(date)"
                        echo "Command: opencode run $arg_line"
                        echo "===================="
                        opencode run $arg_line
                        echo "===================="
                        echo "Ended: $(date)"
                    } 2>&1 | tee task.log

                    # Calculate elapsed time
                    OPENCODE_END=$(date +%s)
                    OPENCODE_ELAPSED=$((OPENCODE_END - OPENCODE_START))
                    OPENCODE_MINUTES=$((OPENCODE_ELAPSED / 60))
                    OPENCODE_SECONDS=$((OPENCODE_ELAPSED % 60))
                    echo "⏱️  Time elapsed: ${OPENCODE_MINUTES}m ${OPENCODE_SECONDS}s"

                    if [ $? -ne 0 ]; then
                        echo "Error: Failed to execute opencode run for $current_feature with args: $arg_line"
                    else
                        echo "Successfully executed opencode run for $current_feature with args: $arg_line"
                    fi
                done <<< "$current_args"

                cd - > /dev/null
            fi
        fi
    fi
fi

echo ""
echo "All processing completed"
