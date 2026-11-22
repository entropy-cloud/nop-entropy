# .agents/skills — Operational Skills

Operational skills the AI agent auto-loads during daily work. Canonical location for this template: `.agents/skills/` (cross-tool convention; do not scatter skill copies into tool-specific directories — if your runner only reads its own directory, mirror there from this source, never the reverse).

## Naming Rule

All skills shipped by this template carry the **`nop-` prefix** (`nop-git`, `nop-grillme`). The prefix prevents name collisions with skills from other sources the same runner may load. Project-authored skills should pick their own consistent prefix.

## Current Set

| Skill | One-liner |
| --- | --- |
| `nop-git/` | daily git discipline: commits, rebase, history search, worktree policy |
| `nop-grillme/` | relentless interview that stress-tests a requirement, plan, or design; outcome recorded under `docs/discussions/` |

## Boundary With `docs/skills/`

- `.agents/skills/` = **operational**: how to *do* something with tooling (git operations, an interview procedure, engine operation). Auto-loaded; short; imperative.
- `docs/skills/` = **method/audit prompts**: how to *review or challenge* work. Referenced by plans (`Skill: <name>`); must be discoverable by any independent reviewer.

Adding an operational skill: keep it under ~120 lines, one concern per skill, no business logic. Business knowledge belongs in owner docs, never in skills.
