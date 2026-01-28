#!/bin/bash

# Script to create worktree and execute nop-ai run with prompt
# Usage: ./nop-run-variant.sh [feature_name] "<prompt>"
#
# This script:
# 1. Creates a worktree with specified or auto-generated name
# 2. Changes to the worktree directory
# 3. Executes nop-ai run with the provided prompt
#
# Examples:
#   ./nop-run-variant.sh "Implement user login feature"
#   ./nop-run-variant.sh feat-login "Implement user login with 2FA"
#
# Note: This script works regardless of current working directory.
#       It determines the project root from the script's location.

# Check for --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: nop-run-variant.sh [feature_name] \"<prompt>\""
    echo ""
    echo "Options:"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "This script:"
    echo "  1. Creates a worktree with specified or auto-generated name"
    echo "  2. Changes to the worktree directory"
    echo "  3. Executes nop-ai run with the provided prompt"
    echo ""
    echo "Examples:"
    echo "  nop-run-variant.sh \"Implement user login feature\""
    echo "  nop-run-variant.sh feat-login \"Implement user login with 2FA\""
    exit 0
fi

set -e

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

# Find project root from current directory
log_debug "Starting project root search..."
PROJECT_ROOT=$(find_project_root)

if [ $? -ne 0 ]; then
    log_error "Could not find project root directory"
    log_error "Please run this script from within a git repository"
    exit 1
fi

log_debug "Project root found: $PROJECT_ROOT"

# Set paths
MAIN_REPO="$PROJECT_ROOT"
WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"

log_debug "MAIN_REPO: $MAIN_REPO"
log_debug "WORKTREES_DIR: $WORKTREES_DIR"
log_info "Main repository: $MAIN_REPO"

# Check arguments
if [ $# -lt 1 ]; then
    echo "Usage: $0 [feature_name] \"<prompt>\""
    echo ""
    echo "Examples:"
    echo "  $0 \"Implement user login feature\""
    echo "  $0 feat-login \"Implement user login with 2FA\""
    exit 1
fi

# Parse arguments
log_debug "Parsing arguments: $# argument(s)"
if [ $# -gt 1 ]; then
    # Two arguments: feature_name and prompt
    FEATURE_NAME="$1"
    PROMPT="$2"
    log_debug "Two arguments detected - FEATURE_NAME: $FEATURE_NAME, PROMPT: $PROMPT"
else
    # One argument: treat as prompt, auto-generate feature name
    FEATURE_NAME=""
    PROMPT="$1"
    log_debug "One argument detected - PROMPT: $PROMPT (will auto-generate feature name)"
fi

# Generate feature name if not provided
if [ -z "$FEATURE_NAME" ]; then
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    RANDOM_SUFFIX=$(printf "%04d" $((RANDOM % 10000)))
    FEATURE_NAME="variant-${TIMESTAMP}-${RANDOM_SUFFIX}"
    log_debug "Auto-generated feature name: $FEATURE_NAME"
else
    log_debug "Using provided feature name: $FEATURE_NAME"
fi

log_info "Creating worktree for variant..."
log_info "Feature name: $FEATURE_NAME"
log_info "Prompt: $PROMPT"
echo ""

# Record start time
START_TIME=$(date +%s)
log_debug "Start time recorded: $START_TIME"

# Create worktree using registered command
log_info "Calling nop-create-worktree..."
if ! nop-create-worktree "$FEATURE_NAME"; then
    log_error "Failed to create worktree"
    log_debug "Worktree creation failed"
    exit 1
fi

log_debug "Worktree created successfully"

# Construct worktree path
WORKTREE_PATH="$WORKTREES_DIR/$FEATURE_NAME"
log_debug "Constructed worktree path: $WORKTREE_PATH"

# Check if worktree exists
if [ ! -d "$WORKTREE_PATH" ]; then
    log_error "Worktree directory not found: $WORKTREE_PATH"
    log_debug "Directory listing:"
    ls -la "$WORKTREES_DIR" | head -20 || true
    exit 1
fi

log_debug "Worktree directory exists"
log_info "Changing to worktree directory: $WORKTREE_PATH"
cd "$WORKTREE_PATH"
log_debug "Current working directory: $(pwd)"
log_debug "Current git branch: $(git branch --show-current 2>/dev/null || echo '<unknown>')"

# Execute nop-ai run
echo ""
echo "========================================"
echo "Executing nop-ai run"
echo "========================================"
echo "Prompt: $PROMPT"
echo "========================================"
echo ""

# Record start time for nop-ai execution
OPENCODE_START=$(date +%s)
log_debug "nop-ai start time: $OPENCODE_START"

# Execute nop-ai run
log_debug "Running: nop-ai run \"$PROMPT\""
if ! nop-ai run "$PROMPT"; then
    OPENCODE_EXIT_CODE=$?
    echo ""
    log_error "nop-ai run failed with exit code: $OPENCODE_EXIT_CODE"
    echo ""

    # Ask if user wants to keep worktree for debugging
    read -p "Keep worktree for debugging? (y/N): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        # Delete worktree and branch
        log_info "Cleaning up worktree..."
        cd "$MAIN_REPO"
        git worktree remove --force "$WORKTREE_PATH" 2>/dev/null || true

        # Read branch name
        if [ -f "$WORKTREE_PATH/.worktree-branch" ]; then
            TEMP_BRANCH=$(cat "$WORKTREE_PATH/.worktree-branch")
            git branch -D "$TEMP_BRANCH" 2>/dev/null || true
        fi

        log_success "Worktree removed"
    else
        log_info "Worktree kept at: $WORKTREE_PATH"
    fi

    exit $OPENCODE_EXIT_CODE
fi

# Calculate elapsed time
OPENCODE_END=$(date +%s)
OPENCODE_ELAPSED=$((OPENCODE_END - OPENCODE_START))
OPENCODE_MINUTES=$((OPENCODE_ELAPSED / 60))
OPENCODE_SECONDS=$((OPENCODE_ELAPSED % 60))

echo ""
log_success "nop-ai run completed successfully"
log_info "Time elapsed: ${OPENCODE_MINUTES}m ${OPENCODE_SECONDS}s"
echo ""

# List changes
MODIFIED_COUNT=$(git diff --name-only 2>/dev/null | wc -l)
STAGED_COUNT=$(git diff --cached --name-only 2>/dev/null | wc -l)
UNTRACKED_COUNT=$(git ls-files --others --exclude-standard 2>/dev/null | wc -l)

log_info "Changes: $MODIFIED_COUNT modified, $STAGED_COUNT staged, $UNTRACKED_COUNT untracked"

# Ask if user wants to keep worktree
read -p "Keep worktree? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    # Delete worktree and branch
    log_info "Cleaning up worktree..."
    cd "$MAIN_REPO"
    git worktree remove --force "$WORKTREE_PATH" 2>/dev/null || true

    # Read branch name
    if [ -f "$WORKTREE_PATH/.worktree-branch" ]; then
        TEMP_BRANCH=$(cat "$WORKTREE_PATH/.worktree-branch")
        git branch -D "$TEMP_BRANCH" 2>/dev/null || true
    fi

    log_success "Worktree removed"
else
    log_info "Worktree kept at: $WORKTREE_PATH"
    log_info "To merge changes back:"
    echo "  cd $MAIN_REPO/.opencode/script"
    echo "  ./nop-push-worktree.sh $WORKTREE_PATH"
fi

# Calculate total elapsed time
END_TIME=$(date +%s)
TOTAL_ELAPSED=$((END_TIME - START_TIME))
TOTAL_MINUTES=$((TOTAL_ELAPSED / 60))
TOTAL_SECONDS=$((TOTAL_ELAPSED % 60))

echo ""
log_success "Total time: ${TOTAL_MINUTES}m ${TOTAL_SECONDS}s"
echo ""
