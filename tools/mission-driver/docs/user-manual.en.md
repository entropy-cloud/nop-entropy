# Mission-Driver User Manual

> A training handbook for new users. After reading this, you'll be ready to run your first mission.
>
> Companion docs: [`../README.md`](../README.md) (command cheat-sheet), [`../EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md) (internal execution deep dive), [`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) (diagnostics when stuck).

---

## 1. What is mission-driver?

**In one sentence**: mission-driver is an AI development-loop engine. Give it a mission config file plus a requirements/roadmap document, and it will autonomously drive AI agent subprocesses through the full closed loop of **state-check → review plans → execute plans → draft new plans → deep audit**, repeating until the task is complete or the audit budget is exhausted.

What it is **not**:
- Not a chatbot framework (it doesn't converse with you — it runs fully autonomously).
- Not a general-purpose agent runner (it's purpose-built for "long task, has plans, needs audit").
- Not an IDE plugin (it's a standalone CLI + web dashboard).

### 1.1 When to use / when not to use

| Scenario | Use it? | Why |
|----------|---------|-----|
| Refactor a module; expect 5-10 plans + audits | ✅ Yes | This is the sweet spot |
| Fix a single clear bug (under 10 minutes of work) | ❌ No | Just ask the AI directly — overhead not worth it |
| Turn an FSD (functional spec) into code | ✅ Yes | FSD → Roadmap → multiple plans → auto-execute |
| Rename a file | ❌ No | One command, done |
| Clean up a tech-debt list (20+ items) at once | ✅ Yes | Mission batches them, each with audit closure |
| Run a quick one-off script | ❌ No | Use bash directly |
| Add a feature to an existing module where docs + code + tests must stay in sync | ✅ Yes | Mission audits repeatedly to ensure no drift |

**Rule of thumb**: if the task takes **more than 1 hour** and has **clear acceptance criteria** (not "tweak until it looks right"), use mission-driver. For sub-hour tweaks, conversational development is faster.

### 1.2 How it works (the 30-second version)

```
Your mission.json + Roadmap document
        ↓
   mission-driver starts
        ↓
   ┌→ CHECK (deterministic-state gate: commands.check or git-clean check)
   │      ↓ pass
   │  REVIEW_PLANS (review draft-status plans)
   │      ↓ all_complete
   │  EXEC_PLANS (execute active-status plans; one subflow per plan)
   │      ↓ all_complete
   │  DRAFT_PLANS (draft new plans from roadmap)
   │      ↓ created → back to REVIEW_PLANS
   │      ↓ nothing (roadmap has nothing new)
   │  DEEP_AUDIT (deep audit: multi-audit + open-audit + draft remediation plans)
   └──── complete → back to REVIEW_PLANS (execute the audit-created plans)
                   ...
        ↓ maxAuditRounds exhausted + nothing outstanding → mission complete
```

Each box is a **step**, executed by an AI agent (`opencode run` subprocess) or a tool script. Arrows are **transitions**, chosen by the agent's emitted marker (`pass` / `created` / `nothing` / `complete`, etc.).

### Optional: switch to the pi driver

By default each AI step invokes `opencode run`. If you prefer [`pi`](https://github.com/earendil-works/pi-coding-agent), switch with one flag (opencode stays the default, unchanged):

```bash
./tools/mission-driver.sh run <mission> --driver pi --model zai-coding-cn/glm-5.2
```

- Prerequisite becomes the `pi` CLI on PATH (no longer `opencode`).
- With `--driver pi` the engine auto-applies pi defaults (`-p --append-system-prompt @<persona>` + stdin prompt + a tool allowlist) — no extra config needed.
- **Model id format differs**: opencode uses `zhipuai-coding-plan/glm-5.2`; pi uses its own `provider/model` (e.g. `zai-coding-cn/glm-5.2`).
- **Known limitation**: pi has no cross-step session continuity — each step starts a fresh process and recovers state from disk (roadmap/plans), consistent with how the prompts are designed.
- Full options: `tools/mission-driver/README.md` §Driver selection.

## 2. Core Concepts

You don't need to memorize this section — come back when you hit a term.

| Term | Meaning |
|------|---------|
| **Mission** | One task. Defined by `missions/<name>.json`: which flow to use, where the roadmap is, what the test command is, etc. |
| **Flow** | A state-machine definition describing how steps transition. Stored as `flows/*.json`. The default flow is `mission-driver`. |
| **Step** | A node in the state machine. Types: `agent` (AI subprocess), `tool` (shell command), `script` (inline JS), `subflow` (nested state machine), `group` (container). |
| **Plan** | An independent execution unit (a markdown file in `docs/plans/<mission>/`). Lifecycle: `draft` → `active` → `completed`. |
| **Roadmap** | A markdown doc listing all the work items the mission will deliver. DRAFT_PLANS reads this to draft plans. |
| **Marker** | An agent step's output tag (wrapped in `<AI_STEP_RESULT>...</AI_STEP_RESULT>`), deciding the next transition. E.g. `pass` / `fail` / `created` / `nothing` / `issues` / `complete`. |
| **Subflow** | A nested state machine run inside a step. E.g. `EXEC_PLANS` is a subflow that runs the `plan-execution` flow once per active plan. |
| **Audit** | Deep audit. Two flavors: multi-dimensional (checklist across dimensions), open-ended (find hidden risks without a checklist). |
| **Run** | One execution instance of a mission. Each run creates a directory `_tmp/<timestamp>-mission-driver/` holding all logs and state. |

---

## 3. The Typical Workflow (5 stages to your first mission)

### Stage A: Prepare the requirements document

mission-driver doesn't accept verbal requirements. You must first have a **structured source of truth**, one of:

1. **FSD (Functional Specification Document)** — best for new features. Put it in `docs/design/` or `docs/requirements/`.
2. **Bug list** — best for fix-driven work. Put it in `docs/bugs/` or as a collection of issues.
3. **Optimization checklist** — best for tech-debt cleanup, perf, doc sync.

This document is the mission's **source of truth**. The Roadmap is derived from it, and during audits agents will re-check it to confirm intended behavior.

> Tip: the more concrete the document, the better the mission runs. "Optimize performance" is too vague — agents will thrash. "Reduce list render from O(n²) to O(n); target 1000 items in <50ms" is verifiable and agents will one-shot it.

### Stage B: Generate Roadmap and mission.json

With a requirements doc in hand, generate the mission's two core configs.

**Option 1 (recommended): use the `draft` command**

```bash
./tools/mission-driver.sh draft "Add OAuth2 login to the user-service module; see docs/design/oauth-fsd.md"
```

The `draft` command:
1. Runs a brief agent that asks scope questions and emits a short brief (`docs/backlog/<slug>-brief.md`).
2. Runs a draft agent that generates mission.json + a Roadmap doc.

`--target-file` is an optional input aid — the description may reference any path (a single file, a directory, multiple files, or an abstract goal); `--target-file` just points the brief agent at one file or directory to ground the brief. `--flow-hint` names the flow:

```bash
./tools/mission-driver.sh draft "Implement X" --target-file docs/design/oauth-fsd.md --flow-hint mission-driver
```

The description may also reference a directory or multiple files directly, without `--target-file`:

```bash
./tools/mission-driver.sh draft "Read all requirement docs under docs/input/ and generate a roadmap"
```

**Option 2: hand-write**

See `tools/mission-driver/mission.json.example`. A minimal viable mission.json:

```json
{
  "extends": "base",
  "name": "my-mission",
  "description": "One sentence describing what this mission does",
  "flowName": "mission-driver",
  "roadmapPath": "docs/backlog/my-mission-roadmap.md",
  "plansDir": "docs/plans/my-mission",
  "commands": {
    "test": "pnpm test",
    "build": "pnpm build",
    "lint": "pnpm lint",
    "typecheck": "pnpm typecheck",
    "check": ""
  }
}
```

`extends: "base"` inherits shared defaults (model, agent, maxCycles, etc.) from `missions/base.json` — usually you don't need to redefine those. `commands.check` is the optional deterministic-state gate for the CHECK step; empty/omitted falls back to git conflict-marker detection (see [§5.1](#51-the-default-flows-5-steps)).

The Roadmap doc is markdown, roughly:

```markdown
# My Mission Roadmap

| WI | Status | Description |
|----|--------|-------------|
| 1  | todo   | Implement OAuth2 client |
| 2  | todo   | Integrate into login flow |
| 3  | todo   | Add unit tests and e2e tests |
```

### Stage C: Run the mission

```bash
./tools/mission-driver.sh run my-mission
# or the equivalent main-command form (no "run" keyword):
./tools/mission-driver.sh my-mission
```

On launch the engine will:
1. Create a run directory at `_tmp/<timestamp>-mission-driver/`.
2. Start the monitor dashboard (default `http://localhost:9300`).
3. Begin executing the flow from the CHECK step.
4. Write every step's logs, state, and event stream into the run directory.

**On a fresh mission**, the CHECK step's agent reads the mission config and roadmap to confirm the environment is ready. Then DRAFT_PLANS drafts the first batch of plans from the roadmap, REVIEW_PLANS reviews them, EXEC_PLANS executes them.

### Stage D: Open the Monitor

A monitor starts automatically when you run a mission. Or run it standalone:

```bash
./tools/mission-driver.sh monitor
```

Open `http://localhost:9300` in a browser:

- **Run List page** (`/`): all runs, status tags (running / completed / failed), current step, progress bar.
- **Run Detail page** (`/runs/:runId`):
  - Top timeline: each step's start/end/duration/marker.
  - Middle log viewer: click a step name to see the full agent log (xterm.js terminal style).
  - Right MissionConfig card: expand to see the parsed mission.json.
  - Bottom resource chart: memory, opencode RSS, process count curves (spot resource pressure).
  - Top-right Deep Audit tag: current audit round / maxAuditRounds.
- Live event stream (SSE): step starts/completions, transitions, heartbeats — no refresh needed.

> If you run multiple missions concurrently (or a mission + a standalone monitor), the port auto-increments (9300 → 9301 → 9302 …).

### Stage E: Postmortem after completion

```bash
./tools/mission-driver.sh analyze                        # postmortem the latest run
./tools/mission-driver.sh analyze 2026-07-21-095220-mission-driver   # specific run
```

`analyze` will:
1. Scan all events/logs in the run directory.
2. Run a postmortem agent that produces a structured report (highlights, problems, root causes, reusable lessons).
3. Write the report to `tools/mission-driver/memory/` as long-term memory that future missions of the same kind will read.

---

## 4. Command Reference

### 4.1 Main command: run

```bash
./tools/mission-driver.sh run <mission-name> [options]
./tools/mission-driver.sh    <mission-name> [options]   # equivalent (main-command form)
```

**Common flags**:

| Flag | Effect | Example |
|------|--------|---------|
| `--dry-run` | Use mock agent — no real model calls. Verifies flow orchestration. | `--dry-run` |
| `--step <STEP>` | Single-step mode: run only the specified step then stop (`maxSteps=1`, debug). | `--step CHECK` |
| `--from-step <STEP>` | Start at the specified step, then continue looping normally (no transition rewrites). | `--from-step DEEP_AUDIT` |
| `--no-monitor` | Don't start the monitor dashboard (CI / background). | `--no-monitor` |
| `--fast` | Fast mode: skip `fastSkipSteps` (default skips DEEP_AUDIT). | `--fast` |
| `--skip-steps <list>` | Explicitly skip step names (comma-separated; unioned with `--fast`). | `--skip-steps DEEP_AUDIT,CHECK` |
| `--model <id>` | Override the model ID. | `--model zhipuai-coding-plan/glm-4.7-flash` |
| `--parse-model <id>` | Override the model used for parse/correction paths (use a cheaper one). | `--parse-model gpt-4o-mini` |
| `--max-cycles <n>` | Max main-loop cycles. | `--max-cycles 5` |
| `--max-total-steps <n>` | Hard cap on total step count. | `--max-total-steps 100` |
| `--agent <name>` | Specify the sub-agent (default `build`). | `--agent refactor` |
| `--monitor-port <port>` | Specify the monitor port. | `--monitor-port 9400` |

**`--step` vs `--from-step` (important)**:

```bash
# Debug: run CHECK once, then stop and exit the mission
./tools/mission-driver.sh run my-mission --step CHECK

# Continue: start at EXEC_PLANS, then keep looping (DRAFT_PLANS → DEEP_AUDIT → ...)
# Useful when "the last DEEP_AUDIT created plans but EXEC_PLANS didn't finish, I want to pick up"
./tools/mission-driver.sh run my-mission --from-step EXEC_PLANS
```

The two flags are mutually exclusive — passing both exits with an error.

**Environment variables** (equivalents):

```bash
OPENCODE_MODEL=<id>             # = --model
OPENCODE_PARSE_MODEL=<id>       # = --parse-model
OPENCODE_AGENT=<name>           # = --agent
MAX_CYCLES=<n>                  # = --max-cycles
MAX_TOTAL_STEPS=<n>             # = --max-total-steps
MONITOR_PORT=<port>             # = --monitor-port
MONITOR_DISABLE=1               # = --no-monitor
PROJECT_ROOT=<path>             # override project root
OPENCODE_PURE=1                 # run opencode with --pure (skip external plugins)
```

### 4.2 draft: generate mission.json from a description

```bash
./tools/mission-driver.sh draft "<description>" [options]
```

Two stages:
1. **Brief stage**: produces a scope-gate brief (written to `docs/backlog/<slug>-brief.md`) — a brief agent judges whether the scope is clear.
2. **Draft stage**: based on the brief, generates mission.json + a Roadmap.

Flags: `--target-file <path>` (optional input aid — point at a target file or directory; the description may reference any path), `--flow-hint <name>` (name the flow), `--skip-brief` (skip the brief stage; collapse to single-stage draft).

### 4.3 analyze: postmortem

```bash
./tools/mission-driver.sh analyze              # latest run
./tools/mission-driver.sh analyze <runId>      # specific run
```

### 4.4 monitor: standalone monitor

```bash
./tools/mission-driver.sh monitor              # browse historical runs, no engine
./tools/mission-driver.sh monitor --dev        # dev mode (vite-served frontend on :5173)
./tools/mission-driver.sh monitor --monitor-port 9400
```

### 4.5 list / list-steps

```bash
./tools/mission-driver.sh list                 # list all available missions
./tools/mission-driver.sh list-steps my-mission   # list a mission's steps
```

---

## 5. Understanding the Flow: how the state machine runs

This section explains mission-driver's core loop. **Read this and you'll be able to predict what happens next when a mission is running.**

### 5.1 The default flow's 5 steps

`flows/mission-driver.json` defines the main flow with 5 core steps:

| Step | Type | What it does | Input | Output markers |
|------|------|--------------|-------|----------------|
| **CHECK** | agent | Deterministic-state gate: runs `commands.check` when configured (diagnose + fix + rerun if auto-fixable), else falls back to git conflict-marker detection. Does NOT run `commands.test` (that's BUILD_VERIFY's job). | mission.commands | `pass` / `needs_fix` / `fail` |
| **REVIEW_PLANS** | agent (forEach) | Review all `draft`-status plans. | `draftPlans()` | `all_complete` / `some_failed` / `all_failed` |
| **EXEC_PLANS** | subflow (forEach) | Execute all `active`-status plans. | `activePlans()` | `all_complete` / `some_failed` / `all_failed` |
| **DRAFT_PLANS** | agent | Draft new plans from the roadmap. | roadmap doc | `created` / `nothing` |
| **DEEP_AUDIT** | subflow | Run deep audit (multi-audit + open-audit). | the whole project | `complete` / `failed` |

### 5.2 State-transition diagram

```
                   ┌──────────────────────────────────────────┐
                   │                                          │
                   ▼                                          │
   ┌──── CHECK ────┴──── REVIEW_PLANS ──── EXEC_PLANS ──── DRAFT_PLANS ────┐
   │     │                  (forEach             (forEach         │        │
   │     │                   draftPlans)          activePlans)    │        │
   │     │ fail                │                    │            │        │
   │     ▼                     │ all_complete       │ all_complete│       │
   │   failed                  ▼                    ▼            │        │
   │                       EXEC_PLANS           DRAFT_PLANS       │        │
   │                                                                  │        │
   └──── pass (loop re-entry from terminal reconciliation)           │        │
                                                                       │        │
                                          ┌───────────────────────────┘        │
                                          │                                     │
                                          │ created → back to REVIEW_PLANS      │
                                          │                                     │
                                          │ nothing                              │
                                          ▼                                     │
                                       DEEP_AUDIT (subflow)                      │
                                          │                                     │
                                          │ complete → REVIEW_PLANS ─────────────┘
                                          │   (execute the audit-created plans)
                                          │
                                          │ failed → DRAFT_PLANS
                                          │
                                          └──→ (loop until maxAuditRounds is exhausted)
```

**First loop** (CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS):
- CHECK runs the deterministic-state gate (commands.check, else git-clean check).
- REVIEW_PLANS has nothing to review (no draft plans yet) — forEach is empty → `all_complete`.
- EXEC_PLANS has nothing to execute (no active plans yet) — forEach is empty → `all_complete`.
- DRAFT_PLANS drafts the first batch of plans from the roadmap → `created`.

**Second loop** (DRAFT_PLANS created → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS):
- REVIEW_PLANS reviews the freshly drafted plans, promotes them to `active`.
- EXEC_PLANS executes the active plans (one plan-execution subflow per plan).
- DRAFT_PLANS re-checks the roadmap → `created` if more, else `nothing`.

**Entering the audit loop** (DRAFT_PLANS nothing → DEEP_AUDIT):
- The DEEP_AUDIT subflow runs multi-audit + open-audit, finding gaps that weren't in the roadmap.
- Inside the subflow it drafts remediation plans, marks them `planned`.
- DEEP_AUDIT returns `complete` → back to REVIEW_PLANS (executes the remediation plans).
- DRAFT_PLANS re-checks the roadmap → maybe `nothing` again → DEEP_AUDIT again.
- **Loops until `maxAuditRounds` (default 3) is exhausted AND nothing is outstanding.**

### 5.3 Subflows: nested state machines

Some step types are `subflow` — a complete state machine nested inside a step of the main flow.

**EXEC_PLANS's subflow** (`plan-execution.json`): runs once per active plan:
```
EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY
```
- EXECUTE: the agent modifies code per the plan.
- CLOSURE_SCRIPT_CHECK: any scripts named in the plan actually run.
- CLOSURE_AUDIT: audit whether the plan's acceptance criteria are truly met.
- BUILD_VERIFY: run tests/build to confirm baseline isn't broken.

**DEEP_AUDIT's subflow** (`deep-audit-loop.json`):
```
CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS
```
- CHECK_OPEN_AUDITS: are there leftover open audits from prior runs?
- MULTI_AUDIT: checklist-based audit across dimensions (design, tests, architecture, routing, security, …).
- OPEN_AUDIT: open-ended search for hidden risks (not bound by a checklist).
- SCAN_NEW_RESULTS: draft remediation plans from the audit findings.

### 5.4 Plan lifecycle

```
   draft ──REVIEW_PLANS──→ active ──EXEC_PLANS──→ completed
     ↑                        │
     │                        └── (audit finds issues) ──→ back to draft for revision
     │
     └── created by DRAFT_PLANS / SCAN_NEW_RESULTS
```

- **draft**: freshly drafted, not yet reviewed. REVIEW_PLANS dispatches independent sub-agent reviewers; on approval, promotes to active.
- **active**: reviewed and ready to execute. EXEC_PLANS picks these up.
- **completed**: EXEC_PLANS finished and CLOSURE_AUDIT passed.

### 5.5 maxAuditRounds: the audit budget

The audit loop is not infinite. `flows/mission-driver.json` has `maxAuditRounds: 3`, meaning:
- DEEP_AUDIT runs up to 3 rounds.
- If a round finds new issues → creates remediation plans → next round's DRAFT_PLANS → EXEC_PLANS executes them.
- If 3 consecutive rounds find nothing new, or all plans are done → mission completes.

You can tune this. Audit-heavy work: raise it (5-10). Fast iteration: lower it (1-2).

---

## 6. Mission Configuration Deep Dive

### 6.1 mission.json fields

```json
{
  "extends": "base",                        // inherit base.json
  "name": "my-mission",                     // mission name (unique)
  "description": "One sentence.",           // shown to the agent as the mission's purpose
  "flowName": "mission-driver",             // which flow to use (default: mission-driver)
  "roadmapPath": "docs/backlog/roadmap.md", // roadmap doc path
  "plansDir": "docs/plans/my-mission",      // where plan files live
  "planGuide": "docs/plans/00-plan-...md",  // plan-authoring guide (agent consults this when drafting)
  "auditsDir": "docs/audits/my-mission",    // where audit reports go
  "contextDir": "docs/context",             // project context dir (agent reads on startup)
  "moduleDir": "tools/my-module",           // target module root (scope of code changes)
  "commands": {                             // verification commands
    "test": "pnpm test",
    "build": "pnpm build",
    "lint": "pnpm lint",
    "typecheck": "pnpm typecheck",
    "check": ""                             // optional deterministic-state gate for CHECK; empty/omitted = git conflict-marker fallback
  },
  "prompts": {                              // audit prompt templates
    "multiAudit": "docs/skills/multi-dimensional-audit-prompt.md",
    "openAudit": "docs/skills/open-ended-audit-prompt.md"
  },
  "commitFormat": "feat(<scope>): <desc>"   // commit message format
}
```

### 6.2 base.json shared defaults

`missions/base.json` lets multiple missions share config:

```json
{
  "model": "zhipuai-coding-plan/glm-5.2",
  "agent": "build",
  "maxCycles": 8,
  "maxInnerCycles": 6,
  "maxTotalSteps": 500,
  "fastSkipSteps": ["DEEP_AUDIT"],
  "contextDir": "docs/context",
  "commands": { "test": "...", "build": "..." }
}
```

Missions inherit via `"extends": "base"` (shallow merge: nested objects are replaced wholesale, not deep-merged).

### 6.3 Custom flows

If the default 5-stage flow doesn't fit, write your own `flows/my-flow.json`:

```json
{
  "name": "my-flow",
  "entry": "START",
  "maxCycleVisits": 10,
  "steps": {
    "START": {
      "type": "agent",
      "promptPath": "prompts/start.md",
      "transitions": {
        "ok": { "goto": "END" },
        "retry": { "retry": "START", "maxRetries": 3 }
      }
    },
    "END": { "type": "agent", "promptPath": "prompts/end.md", "transitions": { "ok": { "done": "completed" } } }
  }
}
```

Reference it in mission.json via `"flowName": "my-flow"`.

Full flow schema: see [`design/mission-design.md`](../design/mission-design.md).

---

## 7. Common Patterns & Recipes

### 7.1 Bug-fix mission

Requirements doc: `docs/bugs/2026-07-21-login-crash.md` (a bug report).

```bash
./tools/mission-driver.sh draft "Fix login crash bug #123; see docs/bugs/2026-07-21-login-crash.md" \
  --target-file docs/bugs/2026-07-21-login-crash.md
```

### 7.2 New-feature mission

Requirements doc: `docs/design/feature-fsd.md` (an FSD).

```bash
./tools/mission-driver.sh draft "Implement OAuth2 login; FSD at docs/design/oauth-fsd.md" \
  --target-file docs/design/oauth-fsd.md
```

### 7.3 Tech-debt cleanup mission

Requirements doc: `docs/backlog/tech-debt-2026-q3.md` (a checklist).

```bash
./tools/mission-driver.sh draft "Clean up Q3 tech-debt list" \
  --target-file docs/backlog/tech-debt-2026-q3.md
```

### 7.4 Audit-only (no execution)

```bash
./tools/mission-driver.sh run my-mission --step DEEP_AUDIT --dry-run --no-monitor
```

### 7.5 Resume: last run was Ctrl+C'd midway

```bash
# Check run-state.json for the last step reached
cat _tmp/<runId>/run-state.json | grep currentStep

# Resume from that step
./tools/mission-driver.sh run my-mission --from-step <that step>
```

Note: mission-driver doesn't support checkpoint-resume (each `--from-step` is a fresh run), but plan status persists in `docs/plans/`, so EXEC_PLANS won't re-execute already-completed plans.

---

## 8. Common Issues & Troubleshooting

### The mission seems stuck?

See [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md). Quick triage:
1. Is the step process still alive? (`ps aux | grep opencode`)
2. Is the step log still growing? (`tail -f _tmp/<runId>/oc-<STEP>-*.log`)
3. Is there a live network socket? (the model API may be rate-limiting)

### Can I run two missions at the same time?

Yes, but ports auto-increment (9300 → 9301). For different missions, use `--monitor-port` to avoid dashboard confusion.

### How do I see what the agent is doing?

Three ways:
1. The monitor dashboard's log viewer (most intuitive).
2. `tail -f _tmp/<runId>/<mission-name>.log` (step transitions / heartbeats).
3. `tail -f _tmp/<runId>/oc-<STEP>-*.log` (specific step's agent output).

### Frequent rate-limiting?

- Lower `maxCycles` (fewer rounds).
- Use `--parse-model` to route correction paths to a cheaper model.
- Run off-peak.

---

## 9. Going Deeper: execution internals

The above is enough to use mission-driver. If you want to understand the engine internals (subprocess spawning, heartbeats, watchdogs, atomic state writes, reflexion memory), read:

- [`EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md) — component layering, sequence diagrams, subprocess management.
- [`design/mission-design.md`](../design/mission-design.md) — full flow schema design.
- [`design/mission-driver-flow-design.md`](../design/mission-driver-flow-design.md) — design decisions behind the default flow.
- [`CONTEXT.md`](../CONTEXT.md) — project context for AI agents themselves to read (30-second overview of the tool).

---

## 10. Quick Reference Card

```bash
# See all commands at once
./tools/mission-driver.sh --help

# List missions
./tools/mission-driver.sh list

# Run a mission
./tools/mission-driver.sh run <name>
./tools/mission-driver.sh    <name>            # equivalent
./tools/mission-driver.sh    <name> --dry-run  # mock mode
./tools/mission-driver.sh    <name> --step CHECK          # single-step debug
./tools/mission-driver.sh    <name> --from-step DEEP_AUDIT # resume

# Generate a mission
./tools/mission-driver.sh draft "<description>" --target-file <fsd-or-bug.md>

# Monitor
./tools/mission-driver.sh monitor                  # standalone
# Open http://localhost:9300 in a browser

# Postmortem
./tools/mission-driver.sh analyze
./tools/mission-driver.sh analyze <runId>
```

---

**Happy mission driving! 🚀** Stuck? See [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md). Want deeper understanding? See [`EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md).
