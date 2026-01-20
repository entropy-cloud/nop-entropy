#!/bin/bash

# nop-ai - AI command adapter
# This script serves as an adapter layer for AI tools
# Usage: nop-ai run <prompt>
#
# To change the underlying AI tool:
# 1. Modify the EXEC_CMD variable below
# 2. No need to change calling scripts (nop-run-variant, etc.)

# The underlying AI command to execute
EXEC_CMD="opencode"

# Check if the underlying command is available
if ! command -v "$EXEC_CMD" >/dev/null 2>&1; then
    echo "Error: $EXEC_CMD command not found in PATH"
    echo "Please install $EXEC_CMD or update EXEC_CMD in $0"
    exit 1
fi

# Pass all arguments to the underlying command
exec "$EXEC_CMD" "$@"
