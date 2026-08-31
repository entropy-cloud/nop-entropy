# Mission Driver Design

> Status: consensus reached (2 rounds independent review passed; minors fixed)
> Last Updated: 2026-06-20

## 1. Purpose

The mission driver is an **AI development loop engine**. It reads a **mission** (an explicit configuration file for a development objective), then loops through development -- drafting plans, executing, auditing, marking done -- until the roadmap is complete or cannot continue.

Generality is **layered**, not absolute:

| Layer | Generality | Notes |
|-------|-----------|-------|
| `engine.js` (flow state machine) | **Fully generic** | Runs any flow definition, zero project assumptions, this design does not change it |
| `plan-check.mjs` (plan format validation) | **Fully generic** | Fixed contract, validates any project's plans against the plan guide |
| `flow-loader.js` (plans scanning) | **Config-driven** | Plans directory comes from mission.json, no longer hardcoded |
| `config.js` (reads mission) | **Config-driven** | Reads mission.json, provides paths/commands |
| `flows/*.json` (development loop flow) | **Generic flow** | CHECK->EXEC_PLANS->DRAFT_PLANS->REVIEW_PLANS->AUDIT is a universal development loop |
| `prompts/*.md` (AI instructions) | **Semi-generic** | Paths/commands use mission.json variables; but audit/commit still contain project semantics, need adaptation during mission-draft |

**Does not claim "zero project assumptions"** -- the prompts layer still contains project semantics (source directory, commit format, roadmap data model). These are resolved via mission.json fields + mission-draft adaptation, not by pretending they don't exist.

## 2. Core Abstraction: Mission

A **mission** = an explicit configuration file for a development objective (`missions/<name>.json`), containing:

- **description** -- objective description
- **flowName** -- custom main flow name (optional, defaults to `mission-driver`). Custom flows are loaded from `missions/flows/<flowName>.json` first, then the tool's built-in `flows/`
- **roadmapPath** -- roadmap file path
- **plansDir** -- plans directory path
- **planGuide** -- plan authoring guide path (defaults to `plansDir/00-plan-authoring-and-execution-guide.md`)
- **auditsDir** -- audit results directory path (defaults to `audits`)
- **contextDir** -- project context directory (optional)
- **moduleDir** -- target module or project directory (for audit scope, e.g. `packages` or `src/main/java`)
- **commands** -- verification commands (install/test/typecheck/build/lint)
- **commitFormat** -- commit message format description (used by BUILD_VERIFY)
- **prompts** -- optional project-specific skill prompts: `multiAudit`, `openAudit` (paths to project-specific audit prompt files)
- **models** -- optional per-step model/variant overrides (see §2.1)

### 2.1 Per-Step Model & Variant

`models` maps a **step name** directly to `{ model, variant }` so different steps can run on different models / reasoning-effort levels. Step names are the keys in the flow JSON (`CHECK`, `DRAFT_PLANS`, `EXECUTE`, `CLOSURE_AUDIT`, `MULTI_AUDIT`, `OPEN_AUDIT`, etc.).

```json
"models": {
  "DRAFT_PLANS": { "model": "deepseek/deepseek-v4-flash", "variant": "max" },
  "MULTI_AUDIT": { "model": "zhipuai-coding-plan/glm-5.2", "variant": "high" }
}
```

Resolution order (per agent step):

1. `models[STEP].model` / `.variant` — if that step has an entry
2. global `--model` / `--variant` CLI flag (or `OPENCODE_MODEL` / `OPENCODE_VARIANT` env var)
3. built-in default `zhipuai-coding-plan/glm-5.2` (no variant)

**Omitting `models` entirely reproduces the original single-global-model behavior** (glm by default). `variant` maps to `opencode run --variant <level>` (provider-specific reasoning effort: `max`, `high`, `minimal`, ...). Only `model` and `variant` that are set are applied; an unset field falls through to the global/default value, so a step may override only the variant while keeping the global model, or only the model without setting a variant.

This is a pure runtime pass-through: the engine resolves the per-step options and the runner adds `--variant` to the `opencode run` argv only when a variant is selected.

**A mission is fixed-value configuration** -- AI drafts it with all values pinned; the mission driver executes with zero runtime variable substitution. A project can have multiple missions.

## 3. Design Principles

### 3.1 Format Contract is Fixed, Not in Config

The plan format (`Plan Status` line, checklist, closure evidence) is a fixed contract, defined by the plan guide and validated by `plan-check.mjs`. **mission.json does not contain format configuration fields**.

### 3.2 Project/Mission-Specific Info All in mission.json (Fixed Values)

Paths, commands, source directory, commit format are all project-specific fixed values, written into mission.json.

### 3.3 AI Adapts, Program Loops

- AI does mission drafting (understands intent + project structure -> generates mission.json)
- Program does mission execution (reads mission.json -> loops -> terminates)

### 3.4 Roadmap is Human Alignment Point, Plan is AI Auto-Product

Humans maintain roadmap work items and ordering; AI drafts/executes plans in sequence (humans don't review individual plans); quality is ensured by closure audit.

### 3.5 No Parameter Passing Between Top-Level Steps

Top-level flow steps never use `append` transitions. Each step is self-contained: it reads its inputs from mission.json config (`delegates.vars`) or from files on disk (plan files, audit results, roadmap). This ensures any step can be run independently via `--step <STEP_NAME>` for debugging or targeted execution. Data flows through files, not through in-memory prompt buffers.

### 3.6 Expression Engine

The flow engine includes a lightweight expression evaluator (`src/expression.mjs`) used for:

- **`when` conditions**: String expressions like `"multiAuditPrompt != ''"` or `"openAudits().length > 0"`. Also supports backward-compatible object syntax `{var, present/empty/eq/ne}`.
- **`forEach` sources**: Expression function calls like `"activePlans()"` replace script+variable patterns. When the forEach string contains expression syntax (parens, operators), it's evaluated; otherwise treated as a variable name.

Expressions are evaluated with scoped access to flow vars (from mission.json + runtime) and pre-registered functions. They come from trusted flow JSON (developer-authored), never from AI output.

**Available expression functions** (registered in `src/flow-loader.js` `createExpressionFunctions`):

| Function | Returns | Description |
|----------|---------|-------------|
| `activePlans()` | `string[]` | Plan file paths with status: active |
| `draftPlans()` | `string[]` | Plan file paths with status matching any draft/active variant |
| `openAudits()` | `string[]` | Audit result file paths with `Audit Status: open` |

These eliminate dedicated scan-script steps. For example, `forEach: "activePlans()"` replaces the two-step pattern (script scan -> set items var -> forEach reads items).

### 3.7 Project-Level Override (flows + prompts)

Projects can override any built-in flow or prompt without forking the tool. The `missions/` directory supports two optional subdirectories:

```
missions/
├── <name>.json                (mission configs)
├── flows/                     (custom flow overrides)
│   ├── mission-driver.json    (override main flow)
│   └── plan-execution.json    (override subflow)
└── prompts/                   (custom prompt overrides)
    ├── execute.md             (override execute prompt)
    └── plan-review.md         (override review prompt)
```

**Loading priority** (same chain for flows and prompts):
1. `<missionsDir>/flows/<name>.json` or `<missionsDir>/prompts/<name>.md` — project override
2. `<toolDir>/flows/<name>.json` or `<toolDir>/prompts/<name>.md` — built-in default

The `flowName` field in mission.json (optional, defaults to `"mission-driver"`) selects which main flow to load. The same priority chain applies to subflows loaded at runtime.

## 4. Mission JSON Format

```json
{
  "name": "components",
  "description": "Implement all retained Flux components",
  "roadmapPath": "docs/components/roadmap.md",
  "plansDir": "docs/plans",
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "audits",
  "contextDir": "docs/context",
  "moduleDir": "packages",
  "commands": {
    "install": "pnpm install",
    "test": "pnpm test",
    "typecheck": "pnpm typecheck",
    "build": "pnpm build",
    "lint": "pnpm lint"
  },
  "prompts": {
    "multiAudit": "docs/skills/multi-dimensional-audit-prompt.md",
    "openAudit": "docs/skills/open-ended-audit-prompt.md"
  },
  "commitFormat": "imperative mood; feat(<scope>): <title>; body list deliverables"
}
```

- All fields are fixed values, determined when AI drafts.
- `commands` are **mission-level verification commands** (CHECK health check + BUILD_VERIFY final build + work-item-level verification all use them).
- `moduleDir` is used for audit/focused verification (multi-audit/open-audit prompts reference it to locate source code, replacing hardcoded `packages/{{shortName}}/src/`).
- `commitFormat` is the commit format description (used by BUILD_VERIFY for git commits, replacing hardcoded flux format).
- `contextDir` is optional -- when missing, freshness gating is skipped.
- `prompts.multiAudit` / `prompts.openAudit` are optional -- when empty, the corresponding audit step is skipped via `when` condition.
- Cross-package goals use global commands; single-package goals pin the package name.

## 5. Two-Phase Usage

### Phase 1: Mission Draft (AI)

```bash
./tools/mission-driver.sh draft "Implement all components"
```

AI reads project structure + user intent -> generates `missions/<name>.json`. See Section 9.

### Phase 2: Mission Execution (Program Loop)

```bash
./tools/mission-driver.sh run components
./tools/mission-driver.sh run components --dry-run
./tools/mission-driver.sh list missions
```

`./tools/mission-driver.sh` is a thin wrapper that calls `node tools/mission-driver/src/main.js`.

## 6. Flow Architecture

### 6.1 Current Architecture

Top-level flow (`mission-driver.json`):
```
CHECK (health check)
  -> EXEC_PLANS (group: SCAN_PLANS scans incomplete plans -> EXECUTE_EACH_PLAN forEach runs plan-execution subflow)
    -> DRAFT_PLANS (draft plans from roadmap items, with internal sub-agent review)
      -> REVIEW_PLANS (group: scan-reviewed-plans -> promote each to active)
        -> EXEC_PLANS (loop back to execute newly promoted plans)
      -> nothing -> AUDIT (deep-audit-loop subflow) -> DRAFT_PLANS
```

plan-execution subflow (`plan-execution.json`, called by EXEC_PLANS forEach):
```
EXECUTE -> CLOSURE_SCRIPT_CHECK (plan-check.mjs) -> CLOSURE_AUDIT (AI closure) -> BUILD_VERIFY (build+commit)
```

deep-audit-loop subflow (`deep-audit-loop.json`, triggered when DRAFT_PLANS has nothing to draft):
```
CHECK_OPEN_AUDITS -> MULTI_AUDIT -> OPEN_AUDIT -> SCAN_NEW_RESULTS -> DRAFT_FROM_AUDITS
```

**Audit steps are independent**: MULTI_AUDIT and OPEN_AUDIT run sequentially with no data dependency. Each is conditionally executed via `when` (skipped if the corresponding prompt is not configured in mission.json). A script scans audit result files for `Audit Status: open` to determine if plan drafting is needed.

### 6.2 Exit Mechanism

- `DRAFT_PLANS` `nothing` -> `AUDIT` -> `DRAFT_PLANS` -> ... until `maxAuditRounds` (default 3) or `maxCycleVisits` (default 30).
- When all active plans are executed, no roadmap backlog, and no new audit findings, the loop idles until limits are reached.

## 7. Component Responsibilities

| Component | Responsibility | Generality | Changed by This Design |
|-----------|---------------|-----------|----------------------|
| `engine.js` | Flow state machine | Fully generic | No |
| `plan-check.mjs` | Plan format validation | Fully generic | No |
| `config.js` | Reads mission.json; provides all fields + runDir/timestamp/maxSteps (engine needs) | Config-driven | Yes (remove findModuleDir, add mission reading) |
| `runner.js` | Calls opencode + runTool | Generic | No (already executes commands verbatim) |
| `flow-loader.js` | scanActivePlans uses mission.plansDir | Config-driven | Yes (plansDir from mission) |
| `prompts/*.md` | AI instructions | Semi-generic | Yes (variable-ize) |
| `flows/*.json` | Flow definitions | Generic flow | Yes (exit conditions + step names) |

## 8. Fixed Contracts vs Project-Specific

| Fixed Contract (existing or migration deliverable) | Project/Mission-Specific (mission.json fixed values) |
|---|---|
| Plan format -- plan guide + `plan-check.mjs` | roadmapPath |
| Status values draft/active/completed | plansDir |
| Work item granularity = one plan's scope | planGuide |
| | auditsDir |
| | contextDir |
| | moduleDir |
| | commands |
| | commitFormat |
| | prompts (multiAudit, openAudit) |

## 9. Mission Draft Step (AI)

`draft <description>` triggers the **two-stage brief→draft pipeline** implemented by `cmdDraftMission` in `src/main.js` (see `draft-robustness-design.md` for the controlling contract). Stage 1 renders `prompts/mission-brief.md` and asks the brief agent to produce a scope-gate brief; Stage 2 renders `prompts/mission-draft.md` and asks the draft agent to produce the mission.json + roadmap. The brief agent emits a structured gate marker — `<BRIEF_GATE>pass|blocked</BRIEF_GATE>` plus `<BRIEF_GATE_REASON>...</BRIEF_GATE_REASON>` — that the engine parses via `extractBriefGate`; `pass` (or a missing marker, for backward compatibility) advances to Stage 2, while `blocked` stops the pipeline with `draft-state.json` marked `status: "blocked"` and prints a pointer to the brief. All artifact paths (`{{backlogDir}}`, `{{missionsDir}}`) are injected as absolute template variables anchored at `projectRoot`, eliminating the split-brain between literal-relative and absolute-path resolution. `--skip-brief` collapses to the legacy single-stage draft.

The Stage 2 draft agent then executes:

1. Read user input (objective intent).
2. Read project structure: `AGENTS.md`, index files, `package.json`/`pom.xml`/`mvnw` (detect tech stack), directory structure.
3. Locate roadmap (from index/directories), plans directory, context directory, source directory.
4. Detect build commands (pnpm/mvnw/other) + commit format (from `git log` or AGENTS.md).
5. Determine mission name.
6. **Validate generated result**: use `mission-check.mjs` to validate mission.json structure, required fields, path existence.
7. Generate `missions/<name>.json`, output mission name.

## 10. Relationship to AGE

The mission driver operationalizes the AGE loop: roadmap (human alignment) -> AI drafts/executes plans (humans don't review) -> closure audit (quality fallback) -> mark done -> pick next.

## 11. Migration Plan

From current (hardcoded paths + module parameter) to mission-based. **In dependency order**:

1. **mission.json format definition + `mission-check.mjs` validator** (validates structure/required/path existence).
2. **`config.js` refactor**: read mission.json (by mission name -> `missions/<name>.json`), provide mission fields + engine-needed runDir/maxSteps/logFile; remove findModuleDir/readPackageFilter.
3. **Full prompt variable-ization**: all prompts use `{{roadmapPath}}` `{{plansDir}}` `{{testCmd}}` `{{moduleDir}}` `{{commitFormat}}` etc.; remove hardcoded.
4. **`flow-loader.js`**: scanActivePlans uses `mission.plansDir`.
5. **Flow refactor**: step naming, exit conditions, deep-audit-loop subflow.
6. **`runner.js`** already executes commands verbatim (verify no change needed).
7. **`mission-draft.md` prompt + `draft`/`list` commands**.
8. **Generate first mission** (`missions/components.json`) as example + regression test.

## 12. Open Questions

- Mission directory default location: `missions/` (project root) vs `tools/mission-driver/missions/`. Prefer project root `missions/`, `--missions-dir` can override.
- Multi-mission concurrency/dependencies: currently serial; concurrency and dependency modeling is future work.
