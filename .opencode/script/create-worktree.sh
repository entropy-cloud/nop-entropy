#!/bin/bash

# Script to create or update a git worktree with a temporary branch
# Usage: ./create-worktree.sh [feature_name] [base_branch]
#
# Examples:
#   ./create-worktree.sh                    # Auto-generate name: worktrees/temp-20260114-150000
#   ./create-worktree.sh feat-user          # Specified name: worktrees/feat-user
#   ./create-worktree.sh feat-user main     # Specified name and base branch
#
# This script:
# 1. Creates a worktree in ../worktrees/ directory (parallel to project)
# 2. Creates a temporary branch based on timestamp or specified name
# 3. The branch is created from current local base_branch (no fetch needed)
# 4. The branch is local only (not pushed to remote)
# 5. Branch name is stored in .worktree-branch file for later identification
#
# Note: This script works regardless of current working directory.
#       It determines the project root from the script's location.

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
# This works regardless of how the script is invoked (relative path, absolute path, etc.)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Derive the main repository path from the script location
# Script is at: <repo>/.opencode/script/create-worktree.sh
# So we need to go up two levels: script → .opencode → repo
MAIN_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

log_info "Script location: $SCRIPT_DIR"
log_info "Main repository: $MAIN_REPO"

# Check if MAIN_REPO is a valid git repository
if ! cd "$MAIN_REPO" && git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not a git repository at: $MAIN_REPO"
    exit 1
fi

# Parse arguments
FEATURE_NAME="$1"
BASE_BRANCH="${2:-$(cd "$MAIN_REPO" && git branch --show-current)}"

# Extract project name from repository path
PROJECT_NAME=$(basename "$MAIN_REPO")

# Set worktrees directory parallel to project
WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"

# Ensure worktrees directory exists
if [ ! -d "$WORKTREES_DIR" ]; then
    log_info "Creating worktrees directory: $WORKTREES_DIR"
    mkdir -p "$WORKTREES_DIR"
fi

# Generate feature name if not provided
if [ -z "$FEATURE_NAME" ]; then
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    FEATURE_NAME="temp-${TIMESTAMP}"
    log_info "Auto-generated feature name: $FEATURE_NAME"
fi

# Construct target directory path
TARGET_DIR="$WORKTREES_DIR/$FEATURE_NAME"

# Generate branch name
# If FEATURE_NAME already starts with TMP-, use it directly as branch name
# Otherwise, generate a temporary branch name
if [[ "$FEATURE_NAME" == TMP-* ]]; then
    # Feature name is already a temporary branch name (e.g., TMP-20260114-234559-solution-1-test)
    TEMP_BRANCH="$FEATURE_NAME"
    log_info "Using provided TMP-prefixed name as branch: $TEMP_BRANCH"
else
    # Generate a unique temporary branch name
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    RANDOM_SUFFIX=$(printf "%04d" $((RANDOM % 10000)))
    TEMP_BRANCH="TMP-${FEATURE_NAME}-${TIMESTAMP}-${RANDOM_SUFFIX}"
fi

log_info "Worktree will be created at: $TARGET_DIR"
log_info "Base branch: $BASE_BRANCH"

# Check if base branch exists locally
if ! cd "$MAIN_REPO" && git show-ref --verify --quiet "refs/heads/$BASE_BRANCH" 2>/dev/null; then
    log_error "Base branch '$BASE_BRANCH' does not exist locally"
    log_info "Available local branches:"
    git branch
    exit 1
fi

# Clean up any stale worktree registrations before creating new ones
log_info "Cleaning up stale worktree registrations..."
cd "$MAIN_REPO"
git worktree prune

# Check if worktree already exists
if [ -d "$TARGET_DIR" ]; then
    # Check if it's a valid worktree (use basename to handle path format differences)
    FEATURE_BASENAME=$(basename "$TARGET_DIR")
    if cd "$MAIN_REPO" && git worktree list | grep -q "$FEATURE_BASENAME"; then
        log_info "Worktree already exists at: $TARGET_DIR"
        log_info "Updating existing worktree..."

        cd "$TARGET_DIR"

        # Get current branch in worktree
        CURRENT_BRANCH=$(git branch --show-current)
        log_info "Current branch in worktree: $CURRENT_BRANCH"

        # Rebase current worktree branch onto local base branch
        log_info "Rebasing $CURRENT_BRANCH onto $BASE_BRANCH..."
        if ! git rebase "$BASE_BRANCH"; then
            log_warn "Rebase failed, trying hard reset..."
            git reset --hard "$BASE_BRANCH"
        fi

        cd "$MAIN_REPO"
        log_success "Worktree updated successfully"
        exit 0
    else
        log_error "Target directory exists but is not a valid worktree: $TARGET_DIR"
        exit 1
    fi
fi

# Create new worktree

log_info "Creating temporary branch: $TEMP_BRANCH (from $BASE_BRANCH)"

# Create worktree with new branch directly (avoids conflicts with main worktree)
cd "$MAIN_REPO"
log_info "Creating worktree with new branch..."
git worktree add -b "$TEMP_BRANCH" "$TARGET_DIR" "$BASE_BRANCH"

# Save temporary branch name in worktree directory for later use
echo "$TEMP_BRANCH" > "$TARGET_DIR/.worktree-branch"
echo "$BASE_BRANCH" > "$TARGET_DIR/.worktree-base"

# Add metadata files to .gitignore
echo ".worktree-branch" >> "$TARGET_DIR/.gitignore"
echo ".worktree-base" >> "$TARGET_DIR/.gitignore"

log_success "Worktree created successfully!"
log_info "  Worktree path: $TARGET_DIR"
log_info "  Branch name: $TEMP_BRANCH"
log_info "  Base branch: $BASE_BRANCH"
log_info "  Worktree based on: local $BASE_BRANCH (current state)"
echo ""
log_info "To work in this worktree:"
echo "  cd $TARGET_DIR"
echo ""
log_info "To merge changes back:"
echo "  cd $MAIN_REPO/.opencode/script"
echo "  ./push-worktree.sh $TARGET_DIR"
echo ""
