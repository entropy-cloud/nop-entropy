---
name: mission-driver
description: >
  Create roadmaps and mission configs, then drive the mission-driver AI dev-loop engine.
  mission-driver lives at tools/mission-driver/ — it reads missions/<name>.json and loops
  CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → DEEP_AUDIT until the roadmap is done
  or the audit budget is exhausted.
  Use this skill when the user wants to: (1) plan a development goal as a roadmap
  ("create roadmap", "make a roadmap", "new dev goal"); (2) bootstrap a mission config
  from an existing roadmap ("create mission", "config mission", "draft mission");
  (3) launch / resume / monitor a mission ("run mission", "start mission-driver",
  "continue mission", "mission stuck"); (4) mentions "roadmap", "mission-driver",
  "dev loop", "auto development", "AGE loop".
  Trigger words: roadmap, mission, mission-driver, dev loop, draft-mission, list-missions.
---

# mission-driver — Roadmap creation and mission execution

This skill operates the `tools/mission-driver/` engine shipped with this repo. It is
**stack-agnostic**: it works for Node, Python, Java, Go, or any project whose
verification commands can be expressed as shell strings. Project-specific values
(test command, module layout, commit format) are read from `missions/base.json`,
`docs/context/project-context.md`, and `AGENTS.md` rather than hardcoded here.

**Companion docs** (read on demand, not all at once):
- `tools/mission-driver/docs/user-manual.zh.md` / `user-manual.en.md` — full training handbook
- `tools/mission-driver/README.md` — command cheat-sheet
- `tools/mission-driver/EXECUTION-PRINCIPLE.md` — internal execution deep dive
- `tools/mission-driver/TROUBLESHOOTING.md` — diagnostics when stuck
- `docs/plans/00-plan-authoring-and-execution-guide.md` — plan format and lifecycle
- `references/mission-config-schema.md` — full mission.json schema (companion to this file)
- `references/roadmap-template.md` — roadmap structure with annotated example

## Mental model

A **mission** = a fixed config (`missions/<name>.json`) for one development goal. The
engine reads it and enters a loop:

```
CHECK (health: tests/build pass)
  → REVIEW_PLANS (review draft plans → promote active; empty forEach = passthrough)
  → EXEC_PLANS (one plan-execution subflow per active plan:
                   EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY)
  → DRAFT_PLANS (draft 1-3 plans from roadmap → sub-agent review → promote active)
  → [loop back to REVIEW_PLANS]
  → nothing to draft → DEEP_AUDIT (multi-audit + open-audit → draft remediation plans)
                       → REVIEW_PLANS (execute audit-created plans)
  → ... until maxAuditRounds exhausted and nothing outstanding
```

Key facts:
- **Roadmap precedes mission.** The `draft` command will generate both roadmap and
  `missions/<name>.json` from a single description, but only after the brief stage
  confirms scope is clear.
- `missions/<name>.json` is **pure static config** — no runtime state. The engine's
  runtime state lives in `_tmp/<runDir>/run-state.json`.
- Every AI step is a child `opencode run` process; logs land in `_tmp/<ts>-mission-driver/`.
- Plan lifecycle: `draft` → (REVIEW_PLANS) → `active` → (EXEC_PLANS) → `completed`.
  Subflow `plan-execution` runs once per active plan.
- Ctrl-C / SIGTERM is caught by `main.js` for graceful process-tree cleanup.
- Plan format is a fixed contract enforced by `tools/mission-driver/src/plan-check.mjs`.

## Decision: is mission-driver the right tool?

| Use it when | Don't use it when |
|---|---|
| Task takes **>1 hour** and has clear acceptance criteria | Quick fix, <30 min |
| Multi-step work: docs + code + tests must stay in sync | Rename a file |
| Roadmap has 3+ work items needing audit closure | One-off script |
| Refactor / tech-debt cleanup with verification gates | Exploratory prototyping |
| Feature implementation from an FSD or design doc | "Make it look nicer" without measurable criteria |

If unsure, ask the user to estimate effort and acceptance criteria first.

## Workflow A: Create a Roadmap

**Trigger**: user gives a development goal (e.g. "refactor the auth module",
"implement the reporting feature from the FSD"), and no roadmap exists yet.

### A.1 Pre-flight

1. Read `docs/context/project-context.md` **once** to learn the project's stack,
   verification commands, and AI-block conditions.
2. Read `docs/backlog/00-roadmap-authoring-guide.md` (or the project's equivalent)
   for the controlling roadmap structure rules.
3. Scan `docs/backlog/` for existing related roadmaps; scan `docs/analysis/`,
   `docs/requirements/`, `docs/design/`, `docs/input/` for source material
   (FSD, bug list, optimization checklist).
4. Scan `missions/*.json` to see if a mission already targets this goal.

### A.2 Write the roadmap

Path: `docs/backlog/{slug}-roadmap.md` where `{slug}` is a short kebab-case topic
(e.g. `auth-refactor`, `q3-tech-debt`).

**Follow the structure in `references/roadmap-template.md`**. Core rules:

- **Work-item granularity**: each todo/planned/done item must be completable by a
  single execution plan (~5-15 files, 200-500 lines, 1-4 phases). Larger initiatives
  must be split into multiple items.
- **Single dynamic state block**: maintain state only in the top "Work Items" list.
  Do not duplicate status anywhere else.
- **Human/AI division of labor**: humans set items and order; AI takes the first
  `todo` item, drafts/executes plans, marks `done` after closure audit passes.
- **No implementation steps**: roadmap is an index layer, not an execution plan.
  Stage details list deliverable scope, never checkboxes.
- **Dependency graph**: use Mermaid `graph TD`; conflicts with the stage table
  resolve in favor of the table.

An annotated example is in `references/roadmap-template.md`.

### A.3 Self-check

After writing, verify against the anti-pattern list in `references/roadmap-template.md`:

- [ ] Each work item is completable by one plan (no epic-sized items)
- [ ] Only one dynamic state block (the Work Items list)
- [ ] Stage details have no checkboxes or implementation steps
- [ ] Framework/platform reuse explicitly noted (avoid rebuilding existing capabilities)
- [ ] Dependency graph matches the stage table
- [ ] Owner-doc references are accurate (don't restate owner-doc business rules)

Record the roadmap file path — Workflow B needs it.

## Workflow B: Create the mission config

**Trigger**: roadmap exists, need a `missions/<name>.json` to drive the loop.

### B.1 Two creation paths

**Path 1 (recommended): use `draft` to auto-generate**

```bash
./tools/mission-driver.sh draft "<goal description>" \
  --target-file <path/to/requirements-or-fsd.md> \
  [--flow-hint mission-driver] \
  [--skip-brief]   # collapse to single-stage draft (skip scope-gate brief)
```

The `draft` command runs two stages:
1. **Brief stage**: a `mission-brief` agent judges scope clarity and emits a brief at
   `docs/backlog/<slug>-brief.md`. If scope is unclear, it returns `<BRIEF_GATE>blocked</BRIEF_GATE>`
   and stops — fix the open questions in the brief, then re-run.
2. **Draft stage**: a `draft-mission` agent reads the brief + AGENTS.md + project
   structure and emits mission.json + Roadmap doc.

Always review the generated mission.json before running it.

**Path 2: hand-write** when `draft`'s output is unsatisfactory or precise control is needed.
See `references/mission-config-schema.md` for the full schema.

### B.2 Decide field values (stack-agnostic)

Full schema in `references/mission-config-schema.md`. Key conventions:

| Field | Value rule |
|---|---|
| `name` | Short kebab-case name matching the filename |
| `extends` | `"base"` to inherit `missions/base.json` defaults (model, agent, maxCycles, commands) |
| `roadmapPath` | Output of Workflow A: `docs/backlog/{slug}-roadmap.md` |
| `plansDir` | `docs/plans/{mission-name}` — **each mission must have its own subdirectory** or plans from different missions will mix |
| `planGuide` | `docs/plans/00-plan-authoring-and-execution-guide.md` (fixed in this template) |
| `auditsDir` | `docs/audits/{mission-name}` (recommended per-mission; legacy default `audits`) |
| `contextDir` | `docs/context` (fixed) |
| `moduleDir` | Target module root relative to project (e.g. `src/auth`, `packages/api`, `tools/parser`). Cross-cutting work uses project root `.` |
| `commands.test` | **Required**. Read from `docs/context/project-context.md` "Verification Commands" if filled; otherwise infer from `package.json` / `pom.xml` / `Cargo.toml` / `pyproject.toml`. **Placeholder commands must be replaced before run.** |
| `commands.build` / `lint` / `typecheck` | Optional. Missing = step skips that command. |
| `commitFormat` | Read from `AGENTS.md` or `missions/base.json`. |
| `prompts.multiAudit` / `openAudit` | Optional paths to project-level audit prompts. Missing → corresponding audit step is skipped via `when`. |

**Stack command cheat-sheet** (replace brackets with real values):

| Stack | test | build | typecheck | lint |
|---|---|---|---|---|
| Node (pnpm) | `pnpm test` | `pnpm build` | `pnpm typecheck` | `pnpm lint` |
| Node (npm) | `npm test` | `npm run build` | `npx tsc --noEmit` | `npm run lint` |
| Python (pytest) | `pytest` | (omit) | (omit) | `ruff check .` |
| Java (Maven) | `mvn -pl {MODULE} -am test -T 4` | `mvn -pl {MODULE} -am clean package -DskipTests -T 4` | `mvn -pl {MODULE} -am test-compile -T 4` | `mvn -pl {MODULE} -am validate` |
| Go | `go test ./...` | `go build ./...` | `go vet ./...` | `golangci-lint run` |
| Rust | `cargo test` | `cargo build --release` | (use build) | `cargo clippy` |

### B.3 Validate the mission config

```bash
node tools/mission-driver/src/mission-check.mjs missions/<name>.json .
```

Checks required fields (`name`, `roadmapPath`, `plansDir`, `commands.test`) and path
existence. Fix failures and re-run.

### B.4 Pre-flight: plans directory and plan-guide

- Ensure `plansDir` exists (create empty dir if not; the engine scans it for `.md`).
- Ensure `planGuide` file exists. The template ships `docs/plans/00-plan-authoring-and-execution-guide.md`.
- Optional: place a `00-`-prefixed index file in `plansDir` (the engine skips `00-`-prefixed files when scanning for executable plans).

## Workflow C: Run and monitor the mission

**Trigger**: mission config validated, ready to launch the auto dev loop.

### C.1 Pre-flight checks

1. **Health pre-check**: the mission's CHECK step will run typecheck/build/test.
   For slow-compile stacks (Java, large monorepos), manually confirm baseline green first:

   ```bash
   <the mission's commands.typecheck or commands.test>
   ```

   If baseline is red, fix it before starting — otherwise CHECK wastes 3 retries.

2. **No leftover locks**: check `_tmp/` for orphan processes from a previous crashed run:

   ```bash
   node tools/mission-driver/src/reap-orphans.mjs --startup _tmp <PID>
   ```

3. **No conflicting active plans**: if `plansDir` already has `active`-status plans,
   the engine will execute them first.

### C.2 Launch commands

```bash
# Standard run (foreground, starts monitor on port 9300)
./tools/mission-driver.sh run <mission-name>

# Equivalent main-command form (no "run" keyword — same effect)
./tools/mission-driver.sh <mission-name>

# List all available missions
./tools/mission-driver.sh list

# List a mission's steps
./tools/mission-driver.sh list-steps <mission-name>

# Single-step debug (run only CHECK then exit)
./tools/mission-driver.sh run <mission-name> --step CHECK

# Resume from a specific step (run that step then continue the loop normally)
./tools/mission-driver.sh run <mission-name> --from-step EXEC_PLANS

# Dry-run (mock agent, no real model calls — verify flow orchestration)
./tools/mission-driver.sh run <mission-name> --dry-run

# Cap loops to prevent runaway
./tools/mission-driver.sh run <mission-name> --max-cycles 5 --max-total-steps 50

# Skip monitor (CI / background)
./tools/mission-driver.sh run <mission-name> --no-monitor

# Fast mode (skip fastSkipSteps — default skips DEEP_AUDIT)
./tools/mission-driver.sh run <mission-name> --fast
```

Optional environment variables: `OPENCODE_AGENT`, `OPENCODE_MODEL`,
`OPENCODE_PARSE_MODEL`, `MAX_CYCLES`, `MAX_TOTAL_STEPS`, `MONITOR_PORT`,
`MONITOR_DISABLE=1`, `PROJECT_ROOT`, `OPENCODE_PURE=1`.

> **`--step` vs `--from-step`**: `--step` runs ONE step then exits (debug, `maxSteps=1`).
> `--from-step` starts at that step then keeps looping normally (resume). They are
> mutually exclusive — both at once exits with error.
>
> **Platform note**: on Windows, run `./tools/mission-driver.sh` under Git Bash or WSL.

### C.3 Monitor a running mission

After launch, **do not** start the same mission again in the same opencode session
(nested subprocesses corrupt state). Monitor in a separate terminal or browser.

**Monitor dashboard** (recommended): open `http://localhost:9300` in a browser. If port
is taken (another monitor or mission running), it auto-increments (9301, 9302, …).

Or run standalone monitor for browsing history:
```bash
./tools/mission-driver.sh monitor
```

**Read state directly** (most authoritative):

```bash
# _tmp/<runDir>/run-state.json
# Fields: status, currentStep, steps[], auditRound, maxAuditRounds
```

**Tail logs**:
- Main flow log: `tail -f _tmp/<runDir>/<mission-name>.log` — step transitions, markers, retries
- Per-step agent log: `tail -f _tmp/<runDir>/oc-<STEP>-*.log` — the agent's actual output

### C.4 Interrupt and resume

**Graceful interrupt**: `Ctrl-C` or `kill -TERM <main-pid>`. `main.js` catches
SIGTERM/SIGINT, calls `runner.close()`, cleans up the process tree
(SIGTERM → 6s grace → SIGKILL).

**Resume**: re-run `./tools/mission-driver.sh <mission-name>`. The engine recovers from
disk state:
- Scans `plansDir` for `draft`/`active`-status plans (`active` runs first; `draft` is
  reviewed and promoted first).
- Incomplete plans resume from their checkbox progress (`[x]` / `[ ]`).
- Past `run-state.json` step history is for audit only — recovery is driven entirely
  by disk scan, not by replaying steps.

**Resume from a specific step** (debug):

```bash
./tools/mission-driver.sh run <mission-name> --from-step DRAFT_PLANS
```

### C.5 When stuck

Read `tools/mission-driver/TROUBLESHOOTING.md` in full. Quick triage:

| Symptom | Likely mode | Action |
|---|---|---|
| Step process 0% CPU, log mtime frozen >10min | Silent sub-agent stream stall | Wait for 60min watchdog, or `kill -TERM -<STEP_PID>` to advance flow |
| Main log shows `level=ERROR "stream error"` | Explicit model error | Usually auto-retries; on exhaustion the step fails and flow continues |
| `oc-<STEP>-*.log` has only the header, no body | Spawn failure | Check model id, `opencode` in PATH, permissions |
| Main log shows `max_cycles` / `max_total_steps` | Loop cap reached | Normal termination; check if roadmap has remaining `todo`, or raise caps |
| Main log shows `ping_pong` | Two-step death loop | Inspect step transitions and prompts for contradictions |
| Empty `<ts>-mission-driver` dirs piling up in `_tmp/` | Test pollution (historical) | Safe to delete: `find _tmp -maxdepth 1 -type d -empty -delete` |

Orphan-process cleanup:
```bash
# Clean up orphans from a previous crash on startup
node tools/mission-driver/src/reap-orphans.mjs --startup _tmp <RUN_PID>
# Clean up a specific process group
node tools/mission-driver/src/reap-orphans.mjs <PGID> _tmp/<run-dir>
```

## Workflow D: Postmortem

**Trigger**: mission completed (or aborted) and you want durable lessons.

```bash
./tools/mission-driver.sh analyze                  # postmortem the latest run
./tools/mission-driver.sh analyze <runId>          # specific run
```

`analyze` scans all events/logs, runs a postmortem agent, and writes a structured
report to `tools/mission-driver/memory/` as long-term reflexion memory. Future missions
in the same module auto-load this memory on startup.

## Command quick reference

| Purpose | Command |
|---|---|
| List missions | `./tools/mission-driver.sh list` |
| Generate mission.json + roadmap | `./tools/mission-driver.sh draft "<desc>" --target-file <doc>` |
| List steps | `./tools/mission-driver.sh list-steps <mission>` |
| Run mission | `./tools/mission-driver.sh run <mission>` |
| Single-step debug | `./tools/mission-driver.sh run <mission> --step <STEP>` |
| Resume from step | `./tools/mission-driver.sh run <mission> --from-step <STEP>` |
| Dry-run | `./tools/mission-driver.sh run <mission> --dry-run` |
| Cap loops | `./tools/mission-driver.sh run <mission> --max-cycles N` |
| Validate mission.json | `node tools/mission-driver/src/mission-check.mjs missions/<name>.json .` |
| Validate plan format | `node tools/mission-driver/src/plan-check.mjs <plan.md> --strict` |
| Standalone monitor | `./tools/mission-driver.sh monitor` |
| Postmortem | `./tools/mission-driver.sh analyze [<runId>]` |
| Clean orphan processes | `node tools/mission-driver/src/reap-orphans.mjs --startup _tmp <PID>` |

## Constraints and prohibitions

- **Do not** hand-edit a plan file the mission is currently executing (write race with the AI subprocess).
- **Do not** put implementation steps or checkboxes in the roadmap — it's an index layer, not a plan.
- **Do not** mark a roadmap work item `done` before closure audit passes.
- **Do not** launch a mission with `mission-check.mjs` failing (missing required fields crash the engine mid-run).
- **Do not** bypass git hooks via `--no-verify` or similar — BUILD_VERIFY respects hooks and fails on rejection.
- **Do not** nest mission launches in the same opencode session — the mission itself spawns child `opencode run` processes; nesting corrupts state. Launch in a fresh terminal.
- If you must edit the roadmap during a mission (add/remove/reorder items), **stop the mission first** (Ctrl-C), edit, then restart. The AI will not re-arbitrate priority mid-run.
- **Do not** start a mission while another mission on the same `missionsDir` is still running unless you intentionally want concurrency (port auto-increments; plan writes may conflict if plansDirs overlap).
- Replace placeholder verification commands in `missions/base.json` / `docs/context/project-context.md` before relying on them — placeholder commands cause silent CHECK failures.

## Output specification

After creating or modifying a mission config, always include these commands in the reply
(without waiting for the user to ask):

```bash
# Validate mission config
node tools/mission-driver/src/mission-check.mjs missions/<name>.json .

# Dry-run validation (recommended before first real run — no real model calls)
./tools/mission-driver.sh run <mission-name> --dry-run --no-monitor

# Real run (after validation passes)
./tools/mission-driver.sh run <mission-name>
```

After creating a roadmap, always output the roadmap file path for later reference.
