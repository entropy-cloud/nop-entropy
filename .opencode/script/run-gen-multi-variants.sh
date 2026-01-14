#!/bin/bash

# Script to generate multi-solution config and execute batch-worktree
# Usage: ./run-gen-multi-variants.sh "<user-requirement>"
#
# This script can be called from any directory and will:
# 1. Locate the project root directory containing .opencode/script/
# 2. Find or create ai-tasks/ directory in project root
# 3. Generate random filename and create output file
# 4. Call opencode run to generate configuration
# 5. Execute batch-worktree-new.sh with the generated file

# Get absolute path to this script
# Works regardless of current working directory
if command -v realpath &> /dev/null; then
    SCRIPT_PATH="$(realpath "${BASH_SOURCE[0]}")"
elif command -v readlink &> /dev/null; then
    SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"
else
    # Fallback: use cd and pwd
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    SCRIPT_PATH="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
fi
SCRIPT_DIR="$(dirname "$SCRIPT_PATH")"

# Function to find project root by looking for .opencode/script/ directory
find_project_root() {
    local current_dir="$SCRIPT_DIR"
    local max_depth=5
    local depth=0

    while [ $depth -lt $max_depth ]; do
        # Check if current directory contains .opencode/script/
        if [ -d "$current_dir/.opencode/script" ]; then
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

# Find project root
PROJECT_ROOT=$(find_project_root)

if [ $? -ne 0 ]; then
    echo "Error: Could not find project root directory"
    echo "Please run this script from within a project containing .opencode/script/"
    exit 1
fi

# Set paths
AI_TASKS_DIR="$PROJECT_ROOT/ai-tasks"
BATCH_WORKTREE_SCRIPT="$SCRIPT_DIR/batch-worktree.sh"

# Check arguments
if [ $# -lt 1 ]; then
    echo "Usage: $0 \"<user-requirement>\""
    echo ""
    echo "Example:"
    echo "  $0 \"创建一个用户登录功能\""
    echo "  $0 \"实现图片上传，生成5个方案\""
    exit 1
fi

USER_REQUIREMENT="$1"

# Generate random filename (8 characters)
RANDOM_STR=$(head /dev/urandom | tr -dc 'a-zA-Z0-9' | head -c 8)
OUTPUT_FILENAME="task-${RANDOM_STR}.txt"
OUTPUT_FILE="$AI_TASKS_DIR/$OUTPUT_FILENAME"

# Create ai-tasks directory if it doesn't exist
if [ ! -d "$AI_TASKS_DIR" ]; then
    echo "Creating directory: $AI_TASKS_DIR"
    mkdir -p "$AI_TASKS_DIR"
fi

# Display what we're doing
echo "=========================================="
echo "Auto-generating multi-solution config"
echo "=========================================="
echo "Requirement: $USER_REQUIREMENT"
echo "Project root: $PROJECT_ROOT"
echo "Output file: $OUTPUT_FILE"
echo ""

# Call opencode run to generate configuration
echo "Generating configuration..."
echo ""

# Construct message for opencode (opencode run doesn't accept --output option)
# We pass output file path and requirement as part of the message
# Note: Avoid starting with / to prevent slash command detection
# Use printf to safely handle special characters in USER_REQUIREMENT
FULL_MESSAGE=$(printf "@.opencode/command/gen-multi-variants.md --output=%s %s" "$OUTPUT_FILE" "$USER_REQUIREMENT")

# Record start time for opencode execution
START_TIME=$(date +%s)

if ! opencode run "$FULL_MESSAGE"; then
    echo ""
    echo "Error: Failed to generate configuration file"
    exit 1
fi

# Calculate elapsed time
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS_REMAINDER=$((ELAPSED % 60))

echo ""
echo "=========================================="
echo "Configuration generated successfully!"
echo "=========================================="
echo "⏱️  Time elapsed: ${MINUTES}m ${SECONDS_REMAINDER}s"

# Check if file was created
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "Error: Configuration file was not created at $OUTPUT_FILE"
    exit 1
fi

# Show preview of generated file
echo ""
echo "Preview:"
echo "---"
head -20 "$OUTPUT_FILE"
echo "..."
echo ""

# Count number of sections
SECTIONS=$(grep -c "^>>>" "$OUTPUT_FILE" 2>/dev/null || echo "0")
echo "Generated $SECTIONS solutions"
echo ""

# Call batch-worktree.sh with the generated file (no confirmation)
echo "Executing batch-worktree..."
echo ""

if ! "$BATCH_WORKTREE_SCRIPT" "$OUTPUT_FILE"; then
    echo ""
    echo "Error: Failed to execute batch-worktree"
    exit 1
fi

echo ""
echo "=========================================="
echo "All tasks completed!"
echo "=========================================="
echo ""
echo "To clean up temporary branches:"
echo "  ./.opencode/script/clean-tmp-branches.sh"

