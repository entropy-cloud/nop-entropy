#!/bin/bash

# nop-link-dirs - Create symbolic links to .opencode and docs-for-ai directories
# Usage: nop-link-dirs
#
# This script creates symbolic links in the current working directory that point
# to the .opencode and docs-for-ai directories in the project root.
# If the links or directories already exist, they will be skipped.

set -e

# Resolve script path (follow symlinks)
SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_PATH="$(readlink "$SCRIPT_PATH")"
done
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"

# Get project root (two levels up from script directory)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Directories to link
DIRS_TO_LINK=(".opencode" "docs-for-ai")

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Detect operating system
detect_os() {
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
        echo "windows"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "linux"
    else
        echo "unknown"
    fi
}

# Create symbolic link with cross-platform support
create_link() {
    local target_dir="$1"
    local link_name="$2"
    local os=$(detect_os)

    if [ -e "$link_name" ] || [ -L "$link_name" ]; then
        log_warn "Already exists, skipping: $link_name"
        return 0
    fi

    if [ ! -d "$target_dir" ]; then
        log_error "Target directory not found: $target_dir"
        return 1
    fi

    # Create symbolic link
    if ln -s "$target_dir" "$link_name" 2>/dev/null; then
        log_success "Created link: $link_name -> $target_dir"
    else
        # On Windows Git Bash, may need to use different approach
        if [[ "$os" == "windows" ]]; then
            log_warn "Standard symlink failed on Windows, trying alternative..."
            # Try using cmd //c mklink
            local target_win="$(cygpath -w "$target_dir")"
            local link_win="$(cygpath -w "$link_name")"
            if cmd //c mklink //D "$link_win" "$target_win" >/dev/null 2>&1; then
                log_success "Created link (Windows): $link_name -> $target_dir"
            else
                log_error "Failed to create symlink on Windows. Try running as Administrator."
                return 1
            fi
        else
            log_error "Failed to create symlink: $link_name"
            return 1
        fi
    fi

    return 0
}

# Main logic
main() {
    local current_dir="$PWD"
    local os=$(detect_os)

    echo ""
    echo "========================================"
    echo "NOP LINK DIRS"
    echo "========================================"
    echo ""
    log_info "Project root: $PROJECT_ROOT"
    log_info "Current directory: $current_dir"
    log_info "Detected OS: $os"
    echo ""

    # Check if we're in the project root (skip if so)
    if [ "$current_dir" = "$PROJECT_ROOT" ]; then
        log_warn "You are already in the project root. No need to create links."
        echo ""
        return 0
    fi

    # Create links
    log_info "Creating directory links..."
    echo ""

    local success_count=0
    local skip_count=0
    local error_count=0

    for dir in "${DIRS_TO_LINK[@]}"; do
        local target_dir="$PROJECT_ROOT/$dir"
        local link_name="$current_dir/$dir"

        if [ -e "$link_name" ] || [ -L "$link_name" ]; then
            log_warn "Already exists, skipping: $dir"
            ((skip_count++))
        elif [ ! -d "$target_dir" ]; then
            log_error "Target not found: $dir"
            ((error_count++))
        else
            if create_link "$target_dir" "$link_name"; then
                ((success_count++))
            else
                ((error_count++))
            fi
        fi
    done

    echo ""
    echo "========================================"
    log_info "Summary: $success_count created, $skip_count skipped, $error_count failed"
    echo "========================================"
    echo ""

    if [ $error_count -gt 0 ]; then
        return 1
    fi

    return 0
}

main "$@"
