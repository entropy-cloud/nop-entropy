#!/bin/bash

# Script to clean up all TMP- prefixed temporary branches and worktrees
# Usage: ./nop-clean-tmp-branches.sh [--force]
#
# This script:
# 1. Finds all branches starting with TMP-
# 2. Removes worktrees associated with those branches
# 3. Deletes branches
#
# Options:
#   --force  Skip confirmation and proceed with cleanup
#
# Note: This script works regardless of current working directory.
#       It determines the project root from the current directory.

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
WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"

log_info "Main repository: $MAIN_REPO"

# Parse arguments
FORCE_CONFIRMATION=false
for arg in "$@"; do
    if [[ "$arg" == "--force" ]] || [[ "$arg" == "-f" ]]; then
        FORCE_CONFIRMATION=true
        log_info "Force mode enabled - will skip confirmation"
    fi
done

# Check if we're in a git repository
if ! cd "$MAIN_REPO" && git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not a git repository at: $MAIN_REPO"
    exit 1
fi

# Find all TMP- branches
TMP_BRANCHES=$(cd "$MAIN_REPO" && git branch | grep '^  TMP-' | sed 's/^  //')

if [ -z "$TMP_BRANCHES" ]; then
    log_info "No TMP- branches found. Nothing to clean up."
    exit 0
fi

BRANCH_COUNT=$(echo "$TMP_BRANCHES" | wc -l)
log_info "Finding branches starting with TMP-..."
log_warn "Found $BRANCH_COUNT temporary branches:"
echo "$TMP_BRANCHES" | sed 's/^/  - /'
echo ""

# Ask for confirmation (unless --force is specified)
if [ "$FORCE_CONFIRMATION" = false ]; then
    read -p "Delete all TMP- branches and their worktrees? (y/N): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Cleanup cancelled"
        exit 0
    fi
else
    log_info "Force mode - proceeding with cleanup without confirmation"
fi
echo ""

# Remove worktrees first
log_info "Cleaning up worktrees..."
cd "$MAIN_REPO"

deleted_count=0
for branch in $TMP_BRANCHES; do
    # Try to find worktree path for this branch
    # Check worktree list for paths containing branch name
    worktree_path=$(git worktree list | grep "$branch" | awk '{print $1}')

    if [ -n "$worktree_path" ] && [ -d "$worktree_path" ]; then
        log_info "Removing worktree at: $worktree_path"
        if git worktree remove --force "$worktree_path"; then
            log_success "Removed worktree: $worktree_path"
            ((deleted_count++))
        else
            log_warn "Failed to remove worktree: $worktree_path"
        fi
    fi
done

# Delete TMP- branches
log_info "Deleting TMP- branches..."

deleted_branch_count=0
for branch in $TMP_BRANCHES; do
    log_info "Deleting branch: $branch"
    if git branch -D "$branch" 2>/dev/null; then
        log_success "Deleted: $branch"
        ((deleted_branch_count++))
    else
        log_warn "Failed to delete: $branch"
    fi
done

log_success "Cleanup completed!"
log_info "Deleted $deleted_count worktree(s) and $deleted_branch_count branch(es)"

# Show remaining worktrees
echo ""
log_info "Remaining worktrees:"
git worktree list || echo "  (none)"
