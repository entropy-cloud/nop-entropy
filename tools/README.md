# Git Worktree Scripts

Manage isolated worktrees with automatic temporary branches.

## Scripts

### create-worktree.sh

Creates a worktree with a temporary local branch.

```bash
./create-worktree.sh <target_directory> [base_branch]
```

**What it does:**
1. Creates temporary branch: `temp-YYYYMMDD-HHMMSS` (from base_branch)
2. Creates worktree pointing to temp branch
3. Saves branch info to `.worktree-branch` and `.worktree-base`

**Example:**
```bash
./create-worktree.sh /tmp/my-work main
# Creates: temp-20250109-123456, worktree at /tmp/my-work
```

### push-worktree.sh

Merges worktree changes to base branch and cleans up.

```bash
./push-worktree.sh <target_directory>
```

**What it does:**
1. Reads temp branch from `.worktree-branch`
2. Stages/commits changes in worktree
3. Switches to base branch in main repo
4. Merges temp branch → base branch
5. Pushes base branch to remote
6. Deletes temp branch
7. Removes worktree

**Example:**
```bash
./push-worktree.sh /tmp/my-work
# Merges: temp-20250109-123456 → main, pushes to origin/main
```

## Workflow

```bash
# 1. Create worktree
./create-worktree.sh /tmp/feature-work main

# 2. Work in isolation
cd /tmp/feature-work
# ... make changes, commit if desired ...

# 3. Merge and push
cd /path/to/main/repo
./push-worktree.sh /tmp/feature-work
```

## Key Points

- **Temporary branches**: Local only, never pushed to remote
- **Multiple worktrees**: Each has unique temp branch, same base branch
- **Auto cleanup**: Temp branch and worktree removed after push
- **Merge conflicts**: Stops process, preserves worktree for manual resolution

## Files Created

- `.worktree-branch`: Temp branch name (e.g., `temp-20250109-123456`)
- `.worktree-base`: Base branch name (e.g., `master`)

## Return Codes

- `0`: Success
- `1`: Error (invalid params, merge conflict, push failed)
