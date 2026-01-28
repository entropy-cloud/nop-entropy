#!/bin/bash

# Script to parse multi-section task file and execute nop-ai run for each section
# Usage: ./nop-batch-worktree.sh <input-file>
# Usage (validate only): ./nop-batch-worktree.sh -c <input-file>
#
# Input file format:
#   >>> feature-name [base-branch] <<<
#   argument-1
#   argument-2
#
#   >>> another-feature <<<
#   argument-1

# Check for --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: nop-batch-worktree.sh <input-file>"
    echo "Usage (validate only): nop-batch-worktree.sh -c <input-file>"
    echo ""
    echo "Options:"
    echo "  -c, --check    Validate input file format only (no execution)"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "Input file format:"
    echo "  >>> feature-name [base-branch] <<<"
    echo "  argument-1"
    echo "  argument-2"
    echo ""
    echo "  >>> another-feature <<<"
    echo "  argument-1"
    echo ""
    echo "Examples:"
    echo "  nop-batch-worktree.sh tasks.txt"
    echo "  nop-batch-worktree.sh -c tasks.txt"
    exit 0
fi

# Function to find project root by looking for .git directory
find_project_root() {
    local current_dir="$(pwd)"
    local max_depth=5
    local depth=0

    while [ $depth -lt $max_depth ]; do
        # Check if current directory contains .git
        if [ -d "$current_dir/.git" ]; then
            echo "$current_dir"
            return 0
        fi

        # Move up one level
        current_dir="$(dirname "$current_dir")"

        # Stop if we've reached root
        if [ "$current_dir" = "/" ]; then
            break
        fi

        ((depth++))
    done

    return 1
}

# Find project root from current directory
PROJECT_ROOT=$(find_project_root)

if [ $? -ne 0 ]; then
    echo "Error: Could not find project root directory"
    echo "Please run this script from within a git repository"
    exit 1
fi

# Set paths
MAIN_REPO="$PROJECT_ROOT"
WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"

# Color codes for output

# Function to print colored messages
log_info() {
    echo "[INFO] $1"
}

log_warn() {
    echo "[WARN] $1"
}

log_error() {
    echo "[ERROR] $1"
}

log_success() {
    echo "[SUCCESS] $1"
}

# Check if nop-ai is available
if ! command -v nop-ai >/dev/null 2>&1; then
    log_error "nop-ai command not found in PATH"
    exit 1
fi

# Parse command line arguments
if [ "$1" == "-c" ] || [ "$1" == "--check" ]; then
    VALIDATE_ONLY=true
    INPUT_FILE="$2"
else
    VALIDATE_ONLY=false
    INPUT_FILE="$1"
fi

# Check if input file is provided
if [ -z "$INPUT_FILE" ]; then
    echo "Usage: $0 <input-file>"
    echo "   or: $0 -c <input-file> (validate only)"
    exit 1
fi

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    log_error "Input file not found: $INPUT_FILE"
    exit 1
fi

# Function to validate input file format
validate_input_file() {
    local file="$1"
    local section_count=0
    local in_section=false
    local has_args=false

    while IFS= read -r line; do
        # Skip empty lines and comments
        if [[ -z "$line" ]] || [[ "$line" =~ ^[[:space:]]*# ]]; then
            continue
        fi

        # Check for section start
        if [[ "$line" =~ ^\>\>\>\>[[:space:]]+([^[:space:]]+)[[:space:]]*\<\<\< ]]; then
            in_section=true
            has_args=false
            ((section_count++))
        elif [[ "$in_section" == true ]]; then
            # Inside a section, check for arguments
            if [[ ! -z "$line" ]] && [[ ! "$line" =~ ^[[:space:]]*$ ]]; then
                has_args=true
            fi
        fi
    done < "$file"

    if [ $section_count -eq 0 ]; then
        log_error "No valid sections found in input file"
        return 1
    fi

    log_info "Format validation passed. Found $section_count section(s)."
    return 0
}

# Validate input file
validate_input_file "$INPUT_FILE"
if [ $? -ne 0 ]; then
    exit 1
fi

# If validation only, exit here
if [ "$VALIDATE_ONLY" = true ]; then
    log_info "Validation complete. File format is valid."
    exit 0
fi

# Parse input file and process each section
process_input_file() {
    local file="$1"
    local section_count=0
    local failed_sections=0
    local current_section=""
    local current_feature=""
    local current_base_branch=""
    local current_args=""
    local current_timestamp=""
    local in_section=false

    while IFS= read -r line; do
        # Skip empty lines and comments
        if [[ -z "$line" ]] || [[ "$line" =~ ^[[:space:]]*# ]]; then
            continue
        fi

        # Check for section start
        if [[ "$line" =~ ^\>\>\>\>[[:space:]]+([^[:space:]]+)([[:space:]]+([^[:space:]]+))?[[:space:]]*\<\<\< ]]; then
            # Process previous section if exists
            if [ ! -z "$current_section" ]; then
                process_section "$current_section" "$current_feature" "$current_base_branch" "$current_args" "$current_timestamp"
                if [ $? -ne 0 ]; then
                    ((failed_sections++))
                fi
            fi

            # Start new section
            current_section="${BASH_REMATCH[1]}"
            current_base_branch="${BASH_REMATCH[3]:-}"
            current_timestamp=$(date +%Y%m%d-%H%M%S)
            current_feature="$current_section"
            current_args=""
            in_section=true
            ((section_count++))

            echo ""
            echo "[FOUND] Found new section: $current_section"
            echo "   Base branch: ${current_base_branch:-<default>}"
        elif [[ "$in_section" == true ]]; then
            # Inside a section, collect arguments
            current_args="${current_args}${line}"$'\n'
        fi
    done < "$file"

    # Process last section
    if [ ! -z "$current_section" ]; then
        process_section "$current_section" "$current_feature" "$current_base_branch" "$current_args" "$current_timestamp"
        if [ $? -ne 0 ]; then
            ((failed_sections++))
        fi
    fi

    # Print summary
    echo ""
    echo "========================================"
    echo "[SUMMARY] BATCH WORKTREE SUMMARY"
    echo "========================================"
    echo "Total sections: $section_count"
    echo "Successful: $((section_count - failed_sections))"
    echo "Failed: $failed_sections"
    echo "========================================"

    return $failed_sections
}

# Function to process a single section
process_section() {
    local section_name="$1"
    local feature_name="$2"
    local base_branch="$3"
    local args="$4"
    local timestamp="$5"

    echo ""
    echo "========================================"
    echo "[$section_count/$TOTAL_SECTIONS] Processing feature: $feature_name"
    echo "========================================"
    echo "Timestamp: $timestamp"
    echo "Base branch: ${base_branch:-<default>}"
    echo "Arguments count: $(echo "$args" | grep -c '[^[:space:]]' 2>/dev/null || echo 0)"
    echo ""

    # Check if nop-create-worktree command is available
    if ! command -v nop-create-worktree >/dev/null 2>&1; then
        log_error "nop-create-worktree command not found in PATH"
        ((failed_sections++))
    else
        # Pass TMP-prefixed name for unique directory and branch
        feature_with_timestamp="TMP-${timestamp}-${feature_name}"
        echo "[DIR] Creating worktree: $feature_with_timestamp"
        echo "[DIR] Worktree directory: ../worktrees/$feature_with_timestamp"

        # Record start time for worktree creation
        WORKTREE_START=$(date +%s)

        # Attempt to create worktree with full error output
        if [ -n "$base_branch" ]; then
            nop-create-worktree "$feature_with_timestamp" "$base_branch" 2>&1 | tee -a worktree-creation.log
            WORKTREE_EXIT_CODE=${PIPESTATUS[0]}
        else
            nop-create-worktree "$feature_with_timestamp" 2>&1 | tee -a worktree-creation.log
            WORKTREE_EXIT_CODE=${PIPESTATUS[0]}
        fi

        WORKTREE_END=$(date +%s)
        WORKTREE_ELAPSED=$((WORKTREE_END - WORKTREE_START))

        if [ $WORKTREE_EXIT_CODE -ne 0 ]; then
            echo ""
            echo "[ERROR] ERROR: Failed to create worktree for $feature_name"
            echo "[ERROR] Exit code: $WORKTREE_EXIT_CODE"
            echo "[ERROR] Time elapsed: ${WORKTREE_ELAPSED}s"
            echo "[ERROR] Check worktree-creation.log for detailed error information"
            echo "[ERROR] Attempted command: nop-create-worktree $feature_with_timestamp ${base_branch:+$base_branch}"
            ((failed_sections++))
        else
            echo "[OK] Worktree created successfully (took ${WORKTREE_ELAPSED}s)"

            # Get worktree path with TMP prefix
            MAIN_REPO="$PROJECT_ROOT"
            WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"
            worktree_dir="${WORKTREES_DIR}/TMP-${timestamp}-${feature_name}"

            if [ ! -d "$worktree_dir" ]; then
                echo "[ERROR] CRITICAL ERROR: Worktree directory does not exist: $worktree_dir"
                echo "[ERROR] This should not happen - worktree script returned success but directory was not created"
                ((failed_sections++))
                continue
            fi

            if [ -d "$worktree_dir" ]; then
                echo ""
                echo "[CHANGE] Changing to worktree directory: $worktree_dir"
                cd "$worktree_dir" || { echo "[ERROR] ERROR: Failed to cd to $worktree_dir"; ((failed_sections++)); continue; }

                # Log current directory and branch
                echo "[LOCATION] Current directory: $(pwd)"
                CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "<unknown>")
                echo "[BRANCH] Current branch: $CURRENT_BRANCH"

                # Count argument lines
                ARG_LINES_COUNT=$(echo "$args" | grep -c '[^[:space:]]' 2>/dev/null || echo 0)
                echo "[EXEC] Executing $ARG_LINES_COUNT argument line(s)..."
                echo ""

                # Execute each argument line
                arg_line_number=0
                while IFS= read -r arg_line; do
                    # Skip empty lines and comments
                    if [[ -z "$arg_line" ]] || [[ "$arg_line" =~ ^[[:space:]]*# ]]; then
                        continue
                    fi

                    ((arg_line_number++))
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "[${section_count}.${arg_line_number}] Executing nop-ai run"
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "Command: nop-ai run $arg_line"

                    # Write nop-ai input to debug file
                    echo "[TIME] Started: $(date)" | tee -a opencode-debug-${timestamp}.txt
                    echo "[CMD] Command: nop-ai run $arg_line" | tee -a opencode-debug-${timestamp}.txt
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a opencode-debug-${timestamp}.txt

                    # Execute nop-ai run
                    START_TIME=$(date +%s)
                    nop-ai run $arg_line
                    EXIT_CODE=$?
                    END_TIME=$(date +%s)

                    # Calculate elapsed time
                    ELAPSED=$((END_TIME - START_TIME))
                    MINUTES=$((ELAPSED / 60))
                    SECONDS_REMAINDER=$((ELAPSED % 60))

                    echo ""
                    if [ $EXIT_CODE -eq 0 ]; then
                        echo "[OK] Command completed successfully (took ${MINUTES}m ${SECONDS_REMAINDER}s)"
                    else
                        echo "[ERROR] Command failed with exit code: $EXIT_CODE (took ${MINUTES}m ${SECONDS_REMAINDER}s)"
                    fi
                    echo ""
                done <<< "$args"

                # Return to main repository
                cd "$MAIN_REPO"
            fi
        fi
    fi
}

# Count total sections first
TOTAL_SECTIONS=$(grep -c "^>>>" "$INPUT_FILE" 2>/dev/null || echo 0)
section_count=0

# Execute
echo ""
echo "========================================"
echo "[SUMMARY] BATCH WORKTREE EXECUTION"
echo "========================================"
echo "Input file: $INPUT_FILE"
echo "Total sections to process: $TOTAL_SECTIONS"
echo "Validation mode: $VALIDATE_ONLY"
echo "========================================"

process_input_file "$INPUT_FILE"
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    log_error "Batch worktree processing completed with errors"
    exit 1
else
    echo ""
    log_success "Batch worktree processing completed successfully!"
    exit 0
fi
