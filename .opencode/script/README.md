# NOP Scripts

Git worktree management tools for isolated task development and parallel solution comparison.

## Installation

```bash
cd .opencode/script
./register.sh
```

## Command List

| Command | Purpose |
|---------|---------|
| `nop-ai` | AI command adapter (underlying tool configurable) |
| `nop-cli` | Nop CLI tool (code generation, file watching, etc.) |
| `nop-create-worktree` | Create worktree |
| `nop-run-variant` | Create worktree and execute nop-ai |
| `nop-run-multi-variants` | Generate multi-solution config and batch execute |
| `nop-batch-worktree` | Batch process tasks |
| `nop-push-worktree` | Merge worktree changes |
| `nop-clean-tmp-branches` | Clean up temporary branches |

## Usage

### nop-cli

Nop CLI tool for code generation, file format conversion, and other utilities.

```bash
nop-cli [command] [options]
```

**Examples:**
```bash
# Show help
nop-cli --help

# Generate code from ORM model
nop-cli gen -t /path/to/model.orm.xlsx

# Convert file format
nop-cli help
```

### nop-create-worktree

Create or update worktree.

```bash
nop-create-worktree [feature_name] [base_branch]
```

**Examples:**
```bash
# Auto-generate name
nop-create-worktree

# Specify name
nop-create-worktree feat-login

# Specify name and base branch
nop-create-worktree feat-login main
```

### nop-run-variant

Create worktree and execute nop-ai task.

```bash
nop-run-variant [feature_name] "<prompt>"
```

**Examples:**
```bash
# Auto-generate name
nop-run-variant "Implement user login feature"

# Specify name
nop-run-variant feat-login "Implement user login feature"
```

### nop-run-multi-variants

Generate multi-solution config and batch execute nop-ai tasks.

```bash
nop-run-multi-variants "<user-requirement>"
```

**Examples:**
```bash
nop-run-multi-variants "Create a user management system, generate 3 different solutions"
```

### nop-batch-worktree

Batch process worktree tasks.

```bash
nop-batch-worktree <input-file>
nop-batch-worktree -c <input-file>  # Validate format only
```

**Input file format:**
```
>>> feature-name [base-branch] <<<
nop-ai run command1
nop-ai run command2

>>> another-feature main <<<
nop-ai run command1
```

**Examples:**
```bash
nop-batch-worktree tasks.txt
```

### nop-push-worktree

Merge worktree changes to base branch and clean up worktree.

```bash
nop-push-worktree <worktree_path_or_name>
```

**Examples:**
```bash
# Use worktree path
nop-push-worktree C:/can/nop/worktrees/feat-login

# Use worktree name
nop-push-worktree feat-login
```

### nop-clean-tmp-branches

Clean up all TMP- prefixed temporary branches and worktrees.

```bash
nop-clean-tmp-branches
nop-clean-tmp-branches --force  # Skip confirmation
```

## Workflows

### Single Task Development

```bash
# 1. Create worktree and execute task
nop-run-variant "Implement new feature"

# 2. Choose whether to keep worktree
# - y: Keep, manual merge required
# - n: Auto-delete

# If kept, manual merge:
nop-push-worktree worktree_path
```

### Multi-Solution Comparison

```bash
# 1. Generate and execute multiple solutions
nop-run-multi-variants "Optimize login performance"

# 2. Auto-create multiple worktrees and execute
# 3. Clean up temporary branches
nop-clean-tmp-branches --force
```

### Batch Tasks

```bash
# 1. Prepare task file tasks.txt
# 2. Execute batch tasks
nop-batch-worktree tasks.txt
# 3. Clean up temporary branches
nop-clean-tmp-branches --force
```

## Notes

- All commands can be executed from any directory within the project
- Worktrees are created in `../worktrees/` directory (parallel to project)
- Temporary branches start with `TMP-`, use `nop-clean-tmp-branches` to clean up
- Recommend committing or staging uncommitted changes before executing commands

## Uninstall

```bash
cd .opencode/script
./register.sh --unregister
```
