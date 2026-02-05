#!/bin/bash

# Script to create or update a git worktree with a temporary branch
# Usage: ./nop-create-worktree.sh [feature_name] [base_branch]
#
# Examples:
#   ./nop-create-worktree.sh                    # Auto-generate name: worktrees/temp-20260114-150000
#   ./nop-create-worktree.sh feat-user          # Specified name: worktrees/feat-user
#   ./nop-create-worktree.sh feat-user main     # Specified name and base branch
#
# This script:
# 1. Creates a worktree in ../worktrees/ directory (parallel to project)
# 2. Creates a temporary branch based on timestamp or specified name
# 3. The branch is created from current local base_branch (no fetch needed)
# 4. The branch is local only (not pushed to remote)
# 5. Branch name is stored in .worktree-branch file for later identification
#
# Note: This script works regardless of current working directory.
#       It determines the project root from the current directory.

# Check for --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: nop-create-worktree.sh [feature_name] [base_branch]"
    echo ""
    echo "Options:"
    echo "  --help, -h     Show this help message"
    echo ""
    echo "Examples:"
    echo "  nop-create-worktree.sh                    # Auto-generate name"
    echo "  nop-create-worktree.sh feat-user          # Specified name"
    echo "  nop-create-worktree.sh feat-user main     # Specified name and base branch"
    echo ""
    echo "This script:"
    echo "  1. Creates a worktree in ../worktrees/ directory (parallel to project)"
    echo "  2. Creates a temporary branch based on timestamp or specified name"
    echo "  3. The branch is created from current local base_branch (no fetch needed)"
    echo "  4. The branch is local only (not pushed to remote)"
    echo "  5. Branch name is stored in .worktree-branch file for later identification"
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

# Function to log debug messages
log_debug() {
    echo "[DEBUG] $1" >&2
}

# Function to get default branch of the repository
get_default_branch() {
    cd "$MAIN_REPO"

    # Try main first
    if git show-ref --verify --quiet "refs/heads/main" 2>/dev/null; then
        echo "main"
        return 0
    fi

    # Try master
    if git show-ref --verify --quiet "refs/heads/master" 2>/dev/null; then
        echo "master"
        return 0
    fi

    # Get default branch from remote
    if DEFAULT_REMOTE=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null); then
        echo "$DEFAULT_REMOTE" | sed 's@^refs/remotes/origin/@@'
        return 0
    fi

    # Fallback to current branch in main repository
    git rev-parse --abbrev-ref HEAD
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

log_info "Main repository: $MAIN_REPO"

# Check if MAIN_REPO is a valid git repository
log_debug "Validating git repository at: $MAIN_REPO"
if ! cd "$MAIN_REPO" && git rev-parse --git-dir > /dev/null 2>&1; then
    log_error "Not a git repository at: $MAIN_REPO"
    exit 1
fi

log_debug "Git repository validated successfully"

# Parse arguments
FEATURE_NAME="$1"
BASE_BRANCH="${2:-$(get_default_branch)}"

log_debug "Arguments parsed - FEATURE_NAME: $FEATURE_NAME, BASE_BRANCH: $BASE_BRANCH"

# Extract project name from repository path
PROJECT_NAME=$(basename "$MAIN_REPO")
log_debug "Project name: $PROJECT_NAME"

# Set worktrees directory parallel to project
WORKTREES_DIR="$(dirname "$MAIN_REPO")/worktrees"
log_debug "Worktrees directory: $WORKTREES_DIR"

# Ensure worktrees directory exists
if [ ! -d "$WORKTREES_DIR" ]; then
    log_info "Creating worktrees directory: $WORKTREES_DIR"
    mkdir -p "$WORKTREES_DIR"
    log_debug "Created worktrees directory"
else
    log_debug "Worktrees directory already exists"
fi

# Generate feature name if not provided
if [ -z "$FEATURE_NAME" ]; then
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    FEATURE_NAME="temp-${TIMESTAMP}"
    log_info "Auto-generated feature name: $FEATURE_NAME"
    log_debug "Feature name auto-generated: $FEATURE_NAME"
else
    log_debug "Feature name provided: $FEATURE_NAME"
fi

# Construct target directory path
TARGET_DIR="$WORKTREES_DIR/$FEATURE_NAME"
log_debug "Target directory: $TARGET_DIR"

# Generate branch name
# If FEATURE_NAME already starts with TMP-, use it directly as branch name
# Otherwise, generate a temporary branch name
if [[ "$FEATURE_NAME" == TMP-* ]]; then
    # Feature name is already a temporary branch name (e.g., TMP-20260114-234559-solution-1-test)
    TEMP_BRANCH="$FEATURE_NAME"
    log_info "Using provided TMP-prefixed name as branch: $TEMP_BRANCH"
    log_debug "Branch name (TMP-prefixed): $TEMP_BRANCH"
else
    # Generate a unique temporary branch name
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    RANDOM_SUFFIX=$(printf "%04d" $((RANDOM % 10000)))
    TEMP_BRANCH="TMP-${FEATURE_NAME}-${TIMESTAMP}-${RANDOM_SUFFIX}"
    log_debug "Branch name (generated): $TEMP_BRANCH"
fi

log_info "Worktree will be created at: $TARGET_DIR"
log_info "Base branch: $BASE_BRANCH"

# Check if base branch exists locally
log_debug "Checking if base branch '$BASE_BRANCH' exists locally..."
if ! cd "$MAIN_REPO" && git show-ref --verify --quiet "refs/heads/$BASE_BRANCH" 2>/dev/null; then
    log_error "Base branch '$BASE_BRANCH' does not exist locally"
    log_debug "Listing available branches..."
    log_info "Available local branches:"
    git branch
    log_error "Please provide a valid base branch name"
    exit 1
else
    log_debug "Base branch '$BASE_BRANCH' exists locally"
fi

# Verify we're in a clean git repository
cd "$MAIN_REPO"
REPO_STATUS=$(git status --porcelain 2>&1)
if [ -n "$REPO_STATUS" ]; then
    log_warn "Repository has uncommitted changes in main worktree"
    log_warn "This may affect worktree creation"
    log_debug "Repository status:"
    git status --short
else
    log_debug "Repository is clean (no uncommitted changes)"
fi

# Clean up any stale worktree registrations before creating new ones
log_info "Cleaning up stale worktree registrations..."
log_debug "Running: git worktree prune"
cd "$MAIN_REPO"
git worktree prune
log_debug "Worktree prune completed"

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
            if ! git reset --hard "$BASE_BRANCH"; then
                log_error "Failed to reset worktree branch"
                log_error "Manual intervention may be required"
                exit 1
            else
                log_warn "Successfully reset to $BASE_BRANCH"
            fi
        else
            log_success "Rebase completed successfully"
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
log_debug "Worktree creation parameters:"
log_debug "  - Branch name: $TEMP_BRANCH"
log_debug "  - Target directory: $TARGET_DIR"
log_debug "  - Base branch: $BASE_BRANCH"

# Create worktree with new branch directly (avoids conflicts with main worktree)
cd "$MAIN_REPO"
log_info "Creating worktree with new branch..."
log_debug "Running: git worktree add -b $TEMP_BRANCH $TARGET_DIR $BASE_BRANCH"

if ! git worktree add -b "$TEMP_BRANCH" "$TARGET_DIR" "$BASE_BRANCH"; then
    log_error "Failed to create worktree"
    log_error "Command: git worktree add -b $TEMP_BRANCH $TARGET_DIR $BASE_BRANCH"

    # Provide helpful error information
    log_error "Possible causes:"
    log_error "  1. Worktree already exists (git worktree list to check)"
    log_error "  2. Target directory already exists and is not empty"
    log_error "  3. Filesystem permission issues"
    log_error "  4. Branch name conflicts"

    # Check if worktree already exists
    log_debug "Checking if worktree already registered..."
    if git worktree list | grep -q "$TARGET_DIR"; then
        log_error "Worktree directory already registered in git"
        log_info "Try: git worktree remove $TARGET_DIR"
    else
        log_debug "Worktree not already registered"
    fi

    # Check if directory exists
    if [ -d "$TARGET_DIR" ]; then
        log_error "Target directory already exists: $TARGET_DIR"
        log_debug "Directory contents:"
        ls -la "$TARGET_DIR" 2>/dev/null || echo "  (cannot read directory)"
    fi

    # Check for existing branches
    log_debug "Checking if branch already exists..."
    if git branch | grep -q "$TEMP_BRANCH"; then
        log_error "Branch already exists: $TEMP_BRANCH"
        log_info "Try: git branch -D $TEMP_BRANCH"
    else
        log_debug "Branch does not exist (good)"
    fi

    exit 1
fi

# Save temporary branch name in worktree directory for later use
log_debug "Saving metadata files..."
echo "$TEMP_BRANCH" > "$TARGET_DIR/.worktree-branch"
echo "$BASE_BRANCH" > "$TARGET_DIR/.worktree-base"
log_debug "Saved .worktree-branch: $TEMP_BRANCH"
log_debug "Saved .worktree-base: $BASE_BRANCH"

# Add metadata files to .gitignore
echo ".worktree-branch" >> "$TARGET_DIR/.gitignore"
echo ".worktree-base" >> "$TARGET_DIR/.gitignore"

# Create .mvn/maven.config file
mkdir -p "$TARGET_DIR/.mvn"
cat > "$TARGET_DIR/.mvn/maven.config" << 'EOF'
-Dmaven.repo.local.head=.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
EOF
log_debug "Created .mvn/maven.config file"

log_success "Worktree created successfully!"
log_info "  Worktree path: $TARGET_DIR"
log_info "  Branch name: $TEMP_BRANCH"log_info "  Base branch: $BASE_BRANCH"
log_info "  Worktree based on: local $BASE_BRANCH (current state)"
echo ""
log_info "To work in this worktree:"
echo "  cd $TARGET_DIR"
echo ""
log_info "To merge changes back:"
echo "  cd $MAIN_REPO/.opencode/script"
echo "  ./nop-push-worktree.sh $TARGET_DIR"
echo ""
