---
name: git
description: Daily git discipline for this repository — commits, rebase, history search, and the worktree policy. Use when committing, branching, rebasing, searching history, or when a merge conflict or history question comes up.
---

# Git Discipline

## Committing

1. **Review what you actually staged** — `git diff --cached` before every commit. Never commit from assumption; the diff is the truth.
2. **Small, honest slices**: a planned delivery is one independently accepted plan = one local delivery commit, coordinated as specified in `docs/guides/roadmap-goal-execution.md`. A no-plan low-risk fix remains one coherent small commit. Never mix unrelated work.
3. **Message format**: conventional-commit style, imperative mood, first line ≤ 72 chars; scope carries the roadmap context when one exists (e.g. `feat(template): ... (template-consumer WI3)`). The body, when present, is organized: what changed and why (1–3 bullets or short paragraphs), then boundaries/limitations if any. When the change's full verification suite ran green, append the verification status line (e.g. `Verification: pnpm test exit=0`) with commands from `docs/context/project-context.md` — future debugging relies on these known-good baselines.
4. **Never stage**: `.env`, secrets, keys, crash logs (`hs_err_pid*.log`), generated data files, `_tmp/` content. If a scratch file is needed as evidence, put it in the plan directory or `docs/testing/`. When one of these was staged by accident, unstage it **and fix the root cause**: add the pattern to `.gitignore` (or move the file) in the same commit, so the next `git add .` cannot repeat the mistake.
5. Pre-commit hooks may run document checks — read their output; do not `--no-verify` past a failure without understanding it.

## Branching

- The **main branch is the primary workplace**. Work directly on main for small and medium changes; use a short-lived branch only for non-trivial plans that need isolation or review gating.
- Branches are disposable: delete after merge.

## Worktrees

Default: **do not use worktrees.** The main checkout is the working surface.

When parallel isolated work is genuinely required:

```bash
git worktree add ../worktrees/<repo>-<topic>   # sibling of the main checkout, never inside it
```

- The `worktrees/` directory sits **next to** the repository, not inside it; it must never be committed or appear inside the repo tree.
- Remove the worktree (`git worktree remove`) promptly after the work merges, then delete its branch too (`git branch -d <branch>`) — the worktree and its branch share one lifecycle. Do not accumulate stale worktrees or orphan branches.

## Rebase & History

1. Rebase local unpushed commits freely (`git rebase -i @{u}`) to clean up before sharing. Without an upstream, rebase against the merge-base: `git rebase -i $(git merge-base main HEAD)`. Sessions without an interactive editor drive `-i` non-interactively via the sequence editor — squash the last two commits: `GIT_SEQUENCE_EDITOR="sed -i '2s/^pick/squash/'" git rebase -i HEAD~2`.
2. **Never rewrite pushed/shared history** (`push --force`, `rebase` over shared commits) without an explicit human decision — and then prefer `--force-with-lease`.
3. Resolve conflicts on the branch that is being rebased/merged; understand both sides (`git log -p <file>`, `git blame`) before picking hunks — never resolve by "take mine".

## History Search Recipes

```bash
git log --oneline -- <path>            # what happened to a file
git log -S "<string>" --oneline        # when a string appeared/disappeared
git log -p --follow -- <file> | head   # inspect a file's evolution
git blame -L <a>,<b> -- <file>         # who/which commit owns these lines
git show <sha> --stat                  # what a commit touched
```

## Hygiene

- `git status` before and after any non-trivial operation; no orphan files left behind.
- Stash with a message (`git stash push -m "wip: <what>"`); drop stashes you cannot name the purpose of.
- If the repo feels wrong, `git fsck` + `git status` before any repair command; never run destructive repairs (`reset --hard`, `clean -fd`) without listing what would be lost first.
