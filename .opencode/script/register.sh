#!/bin/bash

# Script to register nop-* scripts as global commands
# Usage: ./register.sh [--unregister]
#
# This script creates symbolic links to nop-* scripts in a directory
# that is in your PATH, making them available from anywhere.
#
# Supported platforms:
#   - macOS/Linux
#   - Windows Git Bash

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

log_debug() {
    if [ "${DEBUG:-0}" = "1" ]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# Check for --unregister flag
UNREGISTER=false
if [[ "$1" == "--unregister" ]]; then
    UNREGISTER=true
fi

# Get absolute path to this script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# List of scripts to register
# Command name is derived from script filename by removing .sh extension
SCRIPTS=(
    "nop-batch-worktree.sh"
    "nop-clean-tmp-branches.sh"
    "nop-create-worktree.sh"
    "nop-push-worktree.sh"
    "nop-run-multi-variants.sh"
    "nop-run-variant.sh"
)

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

# Determine installation directory
get_install_dir() {
    local os=$(detect_os)

    # List of candidate directories (in priority order)
    local candidates=(
        "$HOME/.local/bin"
        "$HOME/bin"
        "/usr/local/bin"
    )

    for dir in "${candidates[@]}"; do
        # Skip if directory doesn't exist and we can't create it
        if [ ! -d "$dir" ]; then
            if [ "$dir" == "/usr/local/bin" ]; then
                # Skip system directories that don't exist (need sudo)
                continue
            fi
            mkdir -p "$dir" 2>/dev/null && continue
        fi

        # Check if directory is in PATH
        if [[ ":$PATH:" == *":$dir:"* ]]; then
            echo "$dir"
            return 0
        fi
    done

    # If none found, default to ~/.local/bin
    echo "$HOME/.local/bin"
}

# Check if a command is available in PATH
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Unregister commands
unregister_commands() {
    local install_dir=$(get_install_dir)

    log_info "Unregistering nop-* commands from: $install_dir"
    echo ""

    for script in "${SCRIPTS[@]}"; do
        # Derive command name by removing .sh extension
        local cmd="${script%.sh}"
        local link_path="$install_dir/$cmd"

        if [ -L "$link_path" ]; then
            rm "$link_path"
            log_success "Removed: $link_path"
        elif [ -f "$link_path" ]; then
            log_warn "Found regular file (not symlink): $link_path"
            log_warn "Please remove it manually if desired"
        else
            log_warn "Not found: $link_path"
        fi
    done

    echo ""
    log_info "Unregistration complete"
    echo ""
}

# Register commands
register_commands() {
    local install_dir=$(get_install_dir)

    # Keep SCRIPT_DIR as Unix-style path for symbolic link creation
    # No conversion needed

    log_info "Installing nop-* commands to: $install_dir"
    echo ""

    # Create install directory if it doesn't exist
    if [ ! -d "$install_dir" ]; then
        log_info "Creating directory: $install_dir"
        mkdir -p "$install_dir"
    fi

    # Check if install directory is in PATH
    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        log_warn "Warning: $install_dir is not in your PATH"
        echo ""
        log_info "To add it to your PATH, run one of following:"
        echo ""

        local os=$(detect_os)
        if [[ "$os" == "macos" ]]; then
            echo "  For bash:"
            echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bash_profile"
            echo "  For zsh:"
            echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.zshrc"
            echo ""
            log_info "Then restart your terminal or run: source ~/.bash_profile (or ~/.zshrc)"
        elif [[ "$os" == "linux" ]]; then
            echo "  For bash:"
            echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc"
            echo ""
            log_info "Then run: source ~/.bashrc"
        elif [[ "$os" == "windows" ]]; then
            echo "  For Git Bash:"
            echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc"
            echo ""
            log_info "Then run: source ~/.bashrc"
        fi
        echo ""
    fi

    # Create symbolic links for each script
    for script in "${SCRIPTS[@]}"; do
        # Derive command name by removing .sh extension
        local cmd="${script%.sh}"
        local script_path="$SCRIPT_DIR/$script"
        local link_path="$install_dir/$cmd"

        # Check if source script exists
        if [ ! -f "$script_path" ]; then
            log_error "Script not found: $script_path"
            continue
        fi

        # Remove existing link or file
        if [ -L "$link_path" ]; then
            log_info "Updating existing link: $cmd"
            rm "$link_path"
        elif [ -f "$link_path" ]; then
            log_warn "File already exists: $link_path"
            log_warn "Skipping $cmd (please remove manually if needed)"
            continue
        fi

        # Create symbolic link
        ln -s "$script_path" "$link_path"
        log_success "Registered: $cmd -> $script"
    done

    echo ""
    log_info "Registration complete!"
    echo ""

    # Show registered commands
    log_info "Available commands:"
    for script in "${SCRIPTS[@]}"; do
        # Derive command name by removing .sh extension
        local cmd="${script%.sh}"
        if [ -L "$install_dir/$cmd" ]; then
            echo "  $cmd"
        fi
    done

    echo ""

    # Verify PATH if needed
    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        log_warn "Remember to add $install_dir to your PATH (see instructions above)"
    else
        log_success "Installation directory is in your PATH"
        log_info "You can now use the commands from anywhere!"
    fi

    echo ""
}

# Main script logic
if [ "$UNREGISTER" = true ]; then
    echo ""
    echo "========================================"
    echo "NOP COMMANDS UNREGISTRATION"
    echo "========================================"
    echo ""

    unregister_commands
else
    echo ""
    echo "========================================"
    echo "NOP COMMANDS REGISTRATION"
    echo "========================================"
    echo ""

    # Detect OS
    os=$(detect_os)
    log_info "Detected OS: $os"
    echo ""

    # Show what will be registered
    log_info "Scripts to register:"
    for script in "${SCRIPTS[@]}"; do
        echo "  $script"
    done
    echo ""

    register_commands
fi

echo "========================================"
echo ""
