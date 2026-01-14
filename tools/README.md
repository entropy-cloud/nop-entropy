# Tools Directory

This directory contains utility scripts for development and project management.

## CLI Tool Installation

### install-cli.sh

Installs `nop-cli` command that wraps the JAR file for easy command-line access.

```bash
./install-cli.sh
```

**What it does:**
1. Locates nop-cli JAR file at `nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar`
2. Creates a wrapper script in `$HOME/bin` (Windows Git Bash) or `$HOME/.local/bin` (Linux/Mac)
3. Makes the wrapper executable and sets up the `nop-cli` command

**Features:**
- Cross-platform: Works on Windows (Git Bash), Linux, and macOS
- Path-agnostic: Records the absolute path to the JAR file during installation, so it works regardless of where the project is checked out
- Automatic PATH setup: Provides instructions if the install directory is not in PATH

**Example:**
```bash
cd tools
./install-cli.sh
# Now you can run: nop-cli --help
```

**Reinstall after moving the project:**
If you move the project to a different location, simply run `./install-cli.sh` again to update the wrapper with the new path.

## Git Worktree Scripts

### create-worktree.sh

Creates a worktree in `../worktrees/` directory (parallel to project).

```bash
# Auto-generate branch name with timestamp
./create-worktree.sh

# Specify branch name
./create-worktree.sh feat-user

# Specify branch name and base branch
./create-worktree.sh feat-user master

# Run from any directory using absolute path
/c/path/to/nop-entropy/.opencode/script/create-worktree.sh feat-auth

# Works regardless of current working directory
cd /tmp
~/projects/nop-entropy/.opencode/script/create-worktree.sh feat-user
```

**What it does:**
1. Automatically determines project root from script location (works from any directory)
2. Creates a worktree in `../worktrees/` directory (parallel to project)
3. Creates a temporary branch with the specified name (or auto-generated timestamp-based name)
4. The branch is local only (not pushed to remote)
5. Saves branch info to `.worktree-branch` and `.worktree-base` files
6. Adds metadata files to `.gitignore` so they won't be committed

**Key Feature:**
1. Creates a worktree in `../worktrees/` directory
2. Creates a temporary branch with the specified name (or auto-generated `temp-YYYYMMDD-HHMMSS` if not specified)
3. The branch is local only (not pushed to remote)
4. Saves branch info to `.worktree-branch` and `.worktree-base` files
5. Adds metadata files to `.gitignore` so they won't be committed

**Features:**
- **Auto-create worktrees directory**: Automatically creates `../worktrees/` if it doesn't exist
- **Smart naming**: Auto-generates unique timestamp-based names if no name provided
- **Flexible paths**: Accepts branch names, full paths, or relative paths
- **Safe updates**: If worktree already exists, it will update it instead of failing

**Examples:**
```bash
# Auto-generated name: ../worktrees/temp-20260114-150000
./create-worktree.sh

# Custom name: ../worktrees/feat-user-auth
./create-worktree.sh feat-user-auth

# Custom name and base branch: ../worktrees/feat-fix-bug
./create-worktree.sh feat-fix-bug dev

# Update existing worktree
./create-worktree.sh feat-user-auth
```

### push-worktree.sh

Merges worktree changes to base branch and cleans up.

```bash
# Using full path
./push-worktree.sh C:/can/nop/worktrees/feat-user

# Using relative path
./push-worktree.sh ../worktrees/feat-user

# Using worktree name (recommended)
./push-worktree.sh feat-user

# Run from any directory using absolute path
cd /tmp
/c/path/to/nop-entropy/.opencode/script/push-worktree.sh feat-user
```

**What it does:**
1. Automatically determines project root from script location (works from any directory)
2. Reads temp branch from `.worktree-branch`
3. Reads base branch from `.worktree-base`
4. Stages and commits all changes in worktree (modified, staged, and untracked files)
5. Switches to base branch in main repo
6. Merges temp branch → base branch
7. Pushes base branch to remote
8. Removes worktree and deletes branch

**Key Features:**
- **Flexible path resolution**: Accepts full paths, relative paths, or just the worktree name
- **Smart change detection**: Detects modified, staged, and untracked files
- **Safe cleanup**: Removes worktree before deleting branch to avoid conflicts
- **No-change handling**: Automatically removes worktree if there are no changes

**Examples:**
```bash
# Full path
./push-worktree.sh /path/to/worktrees/feat-user

# Relative path
./push-worktree.sh ../worktrees/feat-user

# Worktree name only (most convenient)
./push-worktree.sh feat-user
```

## Workflow

### Quick Start (Auto-generated Name)

```bash
# 1. Create worktree with auto-generated name
cd .opencode/script
./create-worktree.sh
# Creates: ../worktrees/temp-20260114-150000 with branch temp-20260114-150000

# 2. Work in isolation
cd ../../worktrees/temp-20260114-150000
# ... make changes ...

# 3. Merge and push back
cd ../nop-entropy/.opencode/script
./push-worktree.sh temp-20260114-150000
```

### Named Feature Workflow

```bash
# 1. Create worktree with specific name
cd .opencode/script
./create-worktree.sh feat-user-auth master
# Creates: ../worktrees/feat-user-auth with branch feat-user-auth

# 2. Work in isolation
cd ../../worktrees/feat-user-auth
# ... make changes ...

# 3. Merge and push back
cd ../nop-entropy/.opencode/script
./push-worktree.sh feat-user-auth
```

### Multiple Features

```bash
# Create multiple worktrees for different features
./create-worktree.sh feat-a
./create-worktree.sh feat-b
./create-worktree.sh feat-c

# Work on each feature independently
cd ../../worktrees/feat-a
# ... work on feature A ...

cd ../../worktrees/feat-b
# ... work on feature B ...

# Merge features when ready
cd ../nop-entropy/.opencode/script
./push-worktree.sh feat-a
./push-worktree.sh feat-b
```

## Directory Structure

```
can/
├─nop/
│  ├─nop-entropy/           # Main repository
│  └─worktrees/            # Worktree directory (parallel to project)
│     ├─feat-user/          # Feature worktree 1
│     ├─feat-auth/         # Feature worktree 2
│     └─temp-20260114-150000  # Auto-generated worktree
```

## Key Points

- **Work from any directory**: Scripts automatically determine project root from their location, so you can execute them from any working directory (using absolute paths, relative paths, or just the worktree name)
- **Parallel worktrees**: Worktrees are created in `../worktrees/` parallel to project, not inside it
- **Clean project structure**: No pollution of the main project directory
- **Temporary branches**: Local only, never pushed to remote
- **Multiple worktrees**: Each has a unique branch, can share the same base branch
- **Auto cleanup**: Temp branch and worktree removed after successful push
- **Merge conflicts**: Stops process, preserves worktree for manual resolution
- **Metadata files**: `.worktree-branch` and `.worktree-base` are automatically added to `.gitignore`

## Files Created in Worktree

- `.worktree-branch`: Temporary branch name (e.g., `feat-user` or `temp-20260114-150000`)
- `.worktree-base`: Base branch name (e.g., `master`)
- `.gitignore`: Updated to exclude metadata files

## Return Codes

- `0`: Success
- `1`: Error (invalid params, merge conflict, push failed, worktree not found)

## Cleaning Up Old Worktrees

If you have stale worktrees, you can manually remove them:

```bash
# List all worktrees
git worktree list

# Remove specific worktree
git worktree remove <worktree_path>

# Or use push-worktree.sh to merge and clean up
./push-worktree.sh <worktree_name>
```
