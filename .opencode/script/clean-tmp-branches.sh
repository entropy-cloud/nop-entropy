#!/bin/bash

# Script to clean up all temporary branches with TMP- prefix
# Usage: ./clean-tmp-branches.sh
#
# This script:
# 1. Lists all local branches starting with TMP-
# 2. Removes any worktrees associated with those branches
# 3. Deletes the branches

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${BLUE}[SUCCESS]${NC} $1"
}

# Get absolute path to this script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Derive the main repository path from the script location
MAIN_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

log_info "Main repository: $MAIN_REPO"

# Check if MAIN_REPO is a valid git repository
if ! cd "$MAIN_REPO" && git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not a git repository at: $MAIN_REPO"
    exit 1
fi

cd "$MAIN_REPO"

# Find all branches starting with TMP-
log_info "Finding branches starting with TMP-..."
TMP_BRANCHES=$(git branch --list 'TMP-*' | sed 's/^[*+ ] //')

if [ -z "$TMP_BRANCHES" ]; then
    log_info "No TMP- branches found. Nothing to clean."
    exit 0
fi

# Count branches
BRANCH_COUNT=$(echo "$TMP_BRANCHES" | wc -l)
log_warn "Found $BRANCH_COUNT temporary branches:"
echo "$TMP_BRANCHES" | while read -r branch; do
    echo "  - $branch"
done

# Ask for confirmation
echo ""
read -p "Are you sure you want to delete these branches? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "Operation cancelled."
    exit 0
fi

# Clean up worktrees first
log_info "Cleaning up worktrees..."
git worktree prune

# Remove worktrees associated with TMP branches
log_info "Removing worktrees associated with TMP branches..."
WORKTREE_LIST=$(git worktree list --porcelain)

echo "$TMP_BRANCHES" | while read -r branch; do
    # Extract worktree path for this branch (get lines before branch line, then find worktree)
    WORKTREE_PATH=$(echo "$WORKTREE_LIST" | grep -B2 "refs/heads/$branch$" | grep "worktree " | sed 's/worktree //')
    if [ -n "$WORKTREE_PATH" ] && [ -d "$WORKTREE_PATH" ]; then
        log_info "Removing worktree at: $WORKTREE_PATH"
        if git worktree remove "$WORKTREE_PATH" --force 2>/dev/null; then
            log_success "Removed worktree: $WORKTREE_PATH"
        else
            log_warn "Failed to remove worktree: $WORKTREE_PATH"
        fi
    fi
done

# Prune again after removal
git worktree prune

# Delete branches
log_info "Deleting TMP- branches..."

echo "$TMP_BRANCHES" | while read -r branch; do
    log_info "Deleting branch: $branch"
    if git branch -D "$branch" 2>/dev/null; then
        log_success "Deleted: $branch"
    else
        log_error "Failed to delete: $branch"
    fi
done

# Final prune
git worktree prune

# Count remaining TMP branches
REMAINING=$(git branch --list 'TMP-*' | sed 's/^[* ] //' | wc -l)

log_success "Cleanup completed!"
if [ $REMAINING -eq 0 ]; then
    log_info "All TMP- branches deleted successfully"
else
    log_warn "$REMAINING TMP- branches remain"
fi

echo ""
log_info "Remaining worktrees:"
git worktree list
