#!/bin/bash

# Script to create or update a git worktree with a temporary branch
# Usage: ./create-worktree.sh <target_directory> [base_branch]
#
# This script:
# 1. Creates a temporary branch based on timestamp (temp-YYYYMMDD-HHMMSS)
# 2. Creates a worktree pointing to the temporary branch
# 3. The temporary branch is local only (not pushed to remote)
# 4. Temporary branch name is stored in .worktree-branch file for later identification

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
    echo "Usage: $0 <target_directory> [base_branch]"
    echo "Example: $0 /path/to/worktree main"
    exit 1
fi

TARGET_DIR="$1"
BASE_BRANCH="${2:-$(git branch --show-current)}"

# Check if we are in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not in a git repository"
    exit 1
fi

# Get the main git repository path
MAIN_REPO=$(git rev-parse --show-toplevel)
log_info "Main repository: $MAIN_REPO"

# Check if target directory is inside main repository
if [[ "$TARGET_DIR" == "$MAIN_REPO"* ]]; then
    log_error "Target directory cannot be inside the main repository"
    exit 1
fi

# Check if worktree already exists
if [ -d "$TARGET_DIR" ]; then
    # Check if it's a valid worktree
    if git worktree list | grep -q "$TARGET_DIR"; then
        log_info "Updating existing worktree..."
        cd "$TARGET_DIR"

        # Fetch latest from base branch
        log_info "Fetching latest changes from $BASE_BRANCH..."
        git fetch origin "$BASE_BRANCH"

        # Get current branch in worktree
        CURRENT_BRANCH=$(git branch --show-current)
        log_info "Current branch in worktree: $CURRENT_BRANCH"

        # Rebase current worktree branch onto latest base branch
        log_info "Rebasing $CURRENT_BRANCH onto origin/$BASE_BRANCH..."
        if ! git rebase "origin/$BASE_BRANCH"; then
            log_warn "Rebase failed, trying hard reset..."
            git fetch origin "$BASE_BRANCH"
            git reset --hard "origin/$BASE_BRANCH"
        fi

        log_info "Worktree updated successfully"
        cd "$MAIN_REPO"
    else
        log_error "Target directory exists but is not a valid worktree"
        exit 1
    fi
else
    # Create temporary branch name based on timestamp
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    TEMP_BRANCH="temp-${TIMESTAMP}"

    log_info "Creating temporary branch: $TEMP_BRANCH (from $BASE_BRANCH)"

    # Fetch latest from base branch
    log_info "Fetching latest changes from $BASE_BRANCH..."
    git fetch origin "$BASE_BRANCH"

    # Create temporary branch from base branch
    log_info "Creating temporary branch..."
    git checkout -b "$TEMP_BRANCH" "origin/$BASE_BRANCH"

    # Return to original branch
    log_info "Returning to original branch..."
    git checkout "$BASE_BRANCH"

    # Create worktree pointing to temporary branch
    log_info "Creating worktree at: $TARGET_DIR"
    git worktree add "$TARGET_DIR" "$TEMP_BRANCH"

    # Save temporary branch name in worktree directory for later use
    echo "$TEMP_BRANCH" > "$TARGET_DIR/.worktree-branch"
    echo "$BASE_BRANCH" > "$TARGET_DIR/.worktree-base"

    log_info "Worktree created successfully"
    log_info "Temporary branch: $TEMP_BRANCH"
    log_info "Base branch: $BASE_BRANCH"
fi
