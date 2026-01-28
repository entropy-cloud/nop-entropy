#!/bin/bash

# Script to generate multi-solution config and execute batch-worktree
# Usage: ./nop-run-multi-variants.sh "<user-requirement>"
#
# This script can be called from any directory and will:
# 1. Locate project root directory containing .opencode/script/
# 2. Find or create ai-tasks/ directory in project root
# 3. Generate random filename and create output file
# 4. Call nop-ai run to generate configuration
# 5. Execute nop-batch-worktree with generated file

# Check for --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: nop-run-multi-variants.sh \"<user-requirement>\""
    echo ""
    echo "Options:"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "This script can be called from any directory and will:"
    echo "  1. Locate project root directory containing .opencode/script/"
    echo "  2. Find or create ai-tasks/ directory in project root"
    echo "  3. Generate random filename and create output file"
    echo "  4. Call nop-ai run to generate configuration"
    echo "  5. Execute nop-batch-worktree with generated file"
    echo ""
    echo "Examples:"
    echo "  nop-run-multi-variants.sh \"Create a user login feature\""
    echo "  nop-run-multi-variants.sh \"Implement image upload, generate 5 solutions\""
    exit 0
fi

# Function to log debug messages
log_debug() {
    echo "[DEBUG] $1" >&2
}

# Function to find project root by looking for .git directory
find_project_root() {
    local current_dir="$(pwd)"
    local max_depth=5
    local depth=0

    log_debug "Finding project root from: $current_dir"

    while [ $depth -lt $max_depth ]; do
        # Check if current directory contains .git
        if [ -d "$current_dir/.git" ]; then
            log_debug "Found .git directory at: $current_dir (depth: $depth)"
            echo "$current_dir"
            return 0
        fi

        # Move up one level
        current_dir="$(dirname "$current_dir")"
        ((depth++))
    done

    log_debug "Project root not found after searching $max_depth levels"
    return 1
}

# Find project root
log_debug "Finding project root..."
PROJECT_ROOT=$(find_project_root)

if [ $? -ne 0 ]; then
    echo "Error: Could not find project root directory"
    echo "Please run this script from within a git repository"
    exit 1
fi

log_debug "Project root found: $PROJECT_ROOT"

# Set paths
AI_TASKS_DIR="$PROJECT_ROOT/ai-tasks"
log_debug "AI tasks directory: $AI_TASKS_DIR"

 # Check arguments
 if [ $# -lt 1 ]; then
     echo "Usage: $0 \"<user-requirement>\""
     echo ""
     echo "Example:"
     echo "  $0 \"Create a user login feature\""
     echo "  $0 \"Implement image upload, generate 5 solutions\""
     exit 1
 fi

USER_REQUIREMENT="$1"
log_debug "User requirement: $USER_REQUIREMENT"

# Generate random filename (8 characters)
RANDOM_STR=$(head /dev/urandom | tr -dc 'a-zA-Z0-9' | head -c 8)
OUTPUT_FILENAME="task-${RANDOM_STR}.txt"
OUTPUT_FILE="$AI_TASKS_DIR/$OUTPUT_FILENAME"
log_debug "Generated filename: $OUTPUT_FILENAME"
log_debug "Output file path: $OUTPUT_FILE"

# Create ai-tasks directory if it doesn't exist
if [ ! -d "$AI_TASKS_DIR" ]; then
    echo "Creating directory: $AI_TASKS_DIR"
    mkdir -p "$AI_TASKS_DIR"
    log_debug "Created ai-tasks directory"
else
    log_debug "AI tasks directory already exists"
fi

# Display what we're doing
echo "=========================================="
echo "Auto-generating multi-solution config"
echo "=========================================="
echo "Requirement: $USER_REQUIREMENT"
echo "Project root: $PROJECT_ROOT"
echo "Output file: $OUTPUT_FILE"
echo ""

# Call nop-ai run to generate configuration
echo "Generating configuration..."
echo ""
log_debug "Preparing nop-ai command..."

# Construct message for nop-ai (nop-ai run doesn't accept --output option)
# We pass output file path and requirement as part of the message
# Note: Avoid starting with / to prevent slash command detection
# Use printf to safely handle special characters in USER_REQUIREMENT
FULL_MESSAGE=$(printf "@.opencode/command/gen-multi-variants.md --output=%s %s" "$OUTPUT_FILE" "$USER_REQUIREMENT")
log_debug "Full message: $FULL_MESSAGE"

# Record start time for nop-ai execution
START_TIME=$(date +%s)
log_debug "Start time: $START_TIME"

if ! nop-ai run "$FULL_MESSAGE"; then
    echo ""
    echo "Error: Failed to generate configuration file"
    log_debug "nop-ai run failed"
    exit 1
fi

# Calculate elapsed time
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS_REMAINDER=$((ELAPSED % 60))

log_debug "nop-ai end time: $END_TIME"
log_debug "Time elapsed: ${MINUTES}m ${SECONDS_REMAINDER}s"

echo ""
echo "=========================================="
echo "Configuration generated successfully!"
echo "=========================================="
echo "[ELAPSED]  Time elapsed: ${MINUTES}m ${SECONDS_REMAINDER}s"

# Check if file was created
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "Error: Configuration file was not created at $OUTPUT_FILE"
    log_debug "Output file not found!"
    exit 1
fi

log_debug "Output file created successfully"

# Show preview of generated file
echo ""
echo "Preview:"
echo "---"
head -20 "$OUTPUT_FILE"
echo "..."
echo ""

# Count number of sections
SECTIONS=$(grep -c "^>>>" "$OUTPUT_FILE" 2>/dev/null || echo "0")
log_debug "Found $SECTIONS solution(s)"
echo "Generated $SECTIONS solutions"
echo ""

# Call nop-batch-worktree with the generated file (no confirmation)
echo "Executing nop-batch-worktree..."
echo ""
log_debug "Calling: nop-batch-worktree \"$OUTPUT_FILE\""

if ! nop-batch-worktree "$OUTPUT_FILE"; then
    echo ""
    echo "Error: Failed to execute batch-worktree"
    log_debug "Batch worktree execution failed"
    exit 1
fi

log_debug "Batch worktree completed successfully"

echo ""
echo "=========================================="
echo "All tasks completed!"
echo "=========================================="
echo ""
echo "To clean up temporary branches:"
echo "  nop-clean-tmp-branches"
