#!/bin/bash

# Script to push changes from a worktree directory to base branch and remove worktree
# Usage: ./push-worktree.sh <target_directory>
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

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Check if target directory is provided
if [ -z "$1" ]; then
    log_error "Target directory is required"
    echo "Usage: $0 <target_directory>"
    echo "Example: $0 /path/to/worktree"
    exit 1
fi

TARGET_DIR="$1"

# Check if target directory exists
if [ ! -d "$TARGET_DIR" ]; then
    log_error "Target directory does not exist: $TARGET_DIR"
    exit 1
fi

# Check if we are in a git repository
MAIN_REPO=$(git rev-parse --show-toplevel)
if [ -z "$MAIN_REPO" ]; then
    log_error "Not in a git repository"
    exit 1
fi

log_info "Main repository: $MAIN_REPO"

# Check if target directory is a valid worktree
if ! git worktree list | grep -q "$TARGET_DIR"; then
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
    BASE_BRANCH=$(git branch --show-current)
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
if git diff --quiet && git diff --cached --quiet; then
    log_warn "No changes to commit in worktree"
    cd "$MAIN_REPO"
    log_info "Removing worktree..."
    git worktree remove "$TARGET_DIR"
    git branch -D "$TEMP_BRANCH" 2>/dev/null || true
    exit 0
fi

# Show changes summary
CHANGE_COUNT=$(git diff --name-only | wc -l)
log_info "Changes to commit: $CHANGE_COUNT files"

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

# Delete temporary branch
log_info "Deleting temporary branch: $TEMP_BRANCH"
git branch -D "$TEMP_BRANCH"

# Remove worktree
log_info "Removing worktree..."
git worktree remove "$TARGET_DIR"

log_info "Worktree removed successfully"
