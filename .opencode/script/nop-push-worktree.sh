#!/bin/bash

# Script to push changes from a worktree directory to base branch and remove worktree
# Usage: ./nop-push-worktree.sh <worktree_path_or_name>
#
# Examples:
#   ./nop-push-worktree.sh C:/can/nop/worktrees/feat-user      # Full path
#   ./nop-push-worktree.sh feat-user                           # Worktree name (in ../worktrees/)
#   ./nop-push-worktree.sh ../worktrees/feat-user              # Relative path
#
# This script:
# 1. Reads temporary branch name from .worktree-branch file
# 2. Reads base branch name from .worktree-base file
# 3. Stages and commits changes in worktree
# 4. Switches to base branch in main repository
# 5. Merges temporary branch into base branch
# 6. Pushes base branch to remote
# 7. Deletes temporary branch
# 8. Removes worktree
#
# Note: This script works regardless of current working directory.
#       It determines the project root from the current directory.

# Check for --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: nop-push-worktree.sh <worktree_path_or_name>"
    echo ""
    echo "Options:"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "Examples:"
    echo "  nop-push-worktree.sh /path/to/worktrees/feat-user      # Full path"
    echo "  nop-push-worktree.sh feat-user                           # Worktree name"
    echo "  nop-push-worktree.sh ../worktrees/feat-user              # Relative path"
    echo ""
    echo "This script:"
    echo "  1. Reads temporary branch name from .worktree-branch file"
    echo "  2. Reads base branch name from .worktree-base file"
    echo "  3. Stages and commits changes in worktree"
    echo "  4. Switches to base branch in main repository"
    echo "  5. Merges temporary branch into base branch"
    echo "  6. Pushes base branch to remote"
    echo "  7. Deletes temporary branch"
    echo "  8. Removes worktree"
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
    log_error "Could not find project root directory"
    log_error "Please run this script from within a git repository"
    exit 1
fi

# Set paths
MAIN_REPO="$PROJECT_ROOT"

log_info "Script location: $(pwd)"
log_info "Main repository: $MAIN_REPO"

# Check if we are in a git repository
if ! cd "$MAIN_REPO" && git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not a git repository at: $MAIN_REPO"
    exit 1
fi

# Check if target directory/name is provided
if [ -z "$1" ]; then
    log_error "Worktree path or name is required"
    echo "Usage: $0 <worktree_path_or_name>"
    echo "Example: $0 C:/can/nop/worktrees/feat-user"
    echo "Example: $0 feat-user"
    exit 1
fi

TARGET_INPUT="$1"

# Resolve worktree path
if [ -d "$TARGET_INPUT" ]; then
    # Full or relative path provided
    TARGET_DIR="$(cd "$TARGET_INPUT" && pwd)"
else
    # Assume worktree name provided, construct path in ../worktrees/
    WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"
    if [ -d "$WORKTREES_DIR/$TARGET_INPUT" ]; then
        TARGET_DIR="$WORKTREES_DIR/$TARGET_INPUT"
    else
        log_error "Worktree not found: $TARGET_INPUT"
        log_info "Tried: $WORKTREES_DIR/$TARGET_INPUT"
        exit 1
    fi
fi

log_info "Worktree directory: $TARGET_DIR"

# Check if target directory is a valid worktree
if ! cd "$MAIN_REPO" && git worktree list | grep -q "$TARGET_DIR"; then
    log_error "Target directory is not a valid worktree"
    exit 1
fi

# Read temporary branch name and base branch name
if [ ! -f "$TARGET_DIR/.worktree-branch" ]; then
    log_error "Cannot find .worktree-branch file in worktree directory"
    exit 1
fi

TEMP_BRANCH=$(cat "$TARGET_DIR/.worktree-branch")

if [ -f "$TARGET_DIR/.worktree-base" ]; then
    BASE_BRANCH=$(cat "$TARGET_DIR/.worktree-base")
else
    BASE_BRANCH=$(cd "$MAIN_REPO" && git branch --show-current)
fi

log_info "Temporary branch: $TEMP_BRANCH"
log_info "Base branch: $BASE_BRANCH"

# Change to worktree directory
cd "$TARGET_DIR"

# Get current branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "$TEMP_BRANCH" ]; then
    log_warn "Current branch ($CURRENT_BRANCH) differs from expected ($TEMP_BRANCH)"
fi

# Check if there are changes
HAS_STAGED=$(git diff --cached --quiet && echo "false" || echo "true")
HAS_MODIFIED=$(git diff --quiet && echo "false" || echo "true")
HAS_UNTRACKED=$(git ls-files --others --exclude-standard | grep -q . && echo "true" || echo "false")

if [ "$HAS_STAGED" = "false" ] && [ "$HAS_MODIFIED" = "false" ] && [ "$HAS_UNTRACKED" = "false" ]; then
    log_warn "No changes to commit in worktree"
    cd "$MAIN_REPO"
    log_info "Removing worktree..."
    git worktree remove --force "$TARGET_DIR"
    git branch -D "$TEMP_BRANCH" 2>/dev/null || true
    log_success "Worktree removed (no changes)"
    exit 0
fi

# Show changes summary
MODIFIED_COUNT=$(git diff --name-only | wc -l)
STAGED_COUNT=$(git diff --cached --name-only | wc -l)
UNTRACKED_COUNT=$(git ls-files --others --exclude-standard | wc -l)
log_info "Changes to commit: $MODIFIED_COUNT modified, $STAGED_COUNT staged, $UNTRACKED_COUNT untracked"

# Add all changes
log_info "Staging changes..."
git add -A

# Commit changes with timestamp
TIMESTAMP=$(date +%Y%m%d%H%M%S)
COMMIT_MSG="Worktree commit $TIMESTAMP"
log_info "Committing changes..."
git commit -m "$COMMIT_MSG"

# Go back to main repository
cd "$MAIN_REPO"

# Checkout base branch
log_info "Switching to base branch: $BASE_BRANCH"
git checkout "$BASE_BRANCH"

# Pull latest changes from remote
log_info "Pulling latest changes from origin/$BASE_BRANCH..."
git pull origin "$BASE_BRANCH" || true

# Merge temporary branch into base branch
log_info "Merging $TEMP_BRANCH into $BASE_BRANCH..."
if ! git merge "$TEMP_BRANCH" -m "Merge worktree changes from $TEMP_BRANCH"; then
    log_error "Merge failed. Please resolve conflicts manually."
    log_info "Temporary branch $TEMP_BRANCH still exists"
    log_info "Worktree still at: $TARGET_DIR"
    exit 1
fi

# Push to remote
log_info "Pushing $BASE_BRANCH to remote..."
if ! git push origin "$BASE_BRANCH"; then
    log_error "Push failed"
    exit 1
fi

log_info "Pushed successfully to: $BASE_BRANCH"

# Remove worktree first
log_info "Removing worktree..."
git worktree remove --force "$TARGET_DIR"

# Delete temporary branch
log_info "Deleting temporary branch: $TEMP_BRANCH"
git branch -D "$TEMP_BRANCH"

log_success "Worktree merged and removed successfully!"
