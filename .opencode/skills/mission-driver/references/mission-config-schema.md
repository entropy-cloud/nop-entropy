# mission.json Schema (stack-agnostic)

> Reference doc for `SKILL.md` Workflow B. Full design: `tools/mission-driver/design/mission-design.md`.
> This file is **stack-agnostic** — project-specific values are read from `missions/base.json`,
> `docs/context/project-context.md`, and `AGENTS.md`, not hardcoded here.

## Table of contents

1. [Field definitions](#field-definitions)
2. [Required fields and validation rules](#required-fields-and-validation-rules)
3. [Example: Node / pnpm project](#example-node--pnpm-project)
4. [Example: Python project](#example-python-project)
5. [Example: Java / Maven project](#example-java--maven-project)
6. [Example: cross-cutting mission](#example-cross-cutting-mission)
7. [run-state.json (engine runtime state)](#run-statejson-engine-runtime-state)
8. [commands field rules](#commands-field-rules)
9. [prompts field and audit steps](#prompts-field-and-audit-steps)
10. [flowName and custom flows](#flowname-and-custom-flows)

---

## Field definitions

```jsonc
{
  // === Required ===
  "name": "{mission-name}",               // kebab-case, matches filename; used for log/runDir naming
  "roadmapPath": "{path/to/roadmap.md}",  // relative to project root; must exist
  "plansDir": "{path/to/plans-dir}",      // relative to project root; engine scans here for draft/active plans
  "commands": {
    "test": "{test command}"              // required; CHECK + BUILD_VERIFY + EXECUTE all use it
    // build / lint / typecheck optional; missing = step skips that command
  },

  // === Strongly recommended ===
  "extends": "base",                      // inherit shared defaults from missions/base.json
  "description": "{what this mission covers}", // printed on startup; referenced by audit prompts
  "planGuide": "{path/to/plan-guide.md}",      // default: plansDir + "/00-plan-authoring-and-execution-guide.md"
  "moduleDir": "{MODULE}",                     // audit focus scope; see below

  // === Optional ===
  "flowName": "{flow-name}",              // default "mission-driver"; see custom flows section
  "auditsDir": "{path/to/audits-dir}",    // default "audits"; DEEP_AUDIT writes here
  "contextDir": "{path/to/context-dir}",  // optional; missing = skip freshness gating
  "commands": {
    "build": "{build command}",
    "lint": "{lint command}",
    "typecheck": "{typecheck command}"
  },
  "prompts": {
    "multiAudit": "{path/to/multi-audit-prompt.md}",  // empty/omitted → skip MULTI_AUDIT step
    "openAudit": "{path/to/open-audit-prompt.md}"     // empty/omitted → skip OPEN_AUDIT step
  },
  "commitFormat": "{commit message format hint}" // BUILD_VERIFY consults this when committing
}
```

---

## Required fields and validation rules

`tools/mission-driver/src/mission-check.mjs` enforces:

| Field | Rule |
|---|---|
| `name` | Non-empty string |
| `roadmapPath` | Non-empty; must exist under projectRoot |
| `plansDir` | Non-empty; must exist under projectRoot |
| `commands` | Object |
| `commands.test` | Non-empty string |

Additional existence checks (when `projectRoot` is given): `roadmapPath`, `plansDir`,
`contextDir`, `moduleDir` paths must actually exist.

Validate command:
```bash
node tools/mission-driver/src/mission-check.mjs missions/<name>.json .
```

---

## Example: Node / pnpm project

A mission for refactoring the auth module of a Node project:

```json
{
  "extends": "base",
  "name": "auth-refactor",
  "description": "Refactor the auth module to use OAuth2 and add integration tests",
  "flowName": "mission-driver",
  "roadmapPath": "docs/backlog/auth-refactor-roadmap.md",
  "plansDir": "docs/plans/auth-refactor",
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "docs/audits/auth-refactor",
  "contextDir": "docs/context",
  "moduleDir": "src/auth",
  "commands": {
    "test": "pnpm test",
    "build": "pnpm build",
    "lint": "pnpm lint",
    "typecheck": "pnpm typecheck"
  },
  "prompts": {
    "multiAudit": "docs/skills/multi-dimensional-audit-prompt.md",
    "openAudit": "docs/skills/open-ended-audit-prompt.md"
  },
  "commitFormat": "feat(auth): <description>"
}
```

### plansDir convention

`plansDir` should be `docs/plans/{mission-name}` — **each mission gets its own subdirectory**.
The engine recursively scans `plansDir` for `.md` files, so mixing plans from multiple
missions in one directory causes cross-mission execution.

```bash
# Create the directory before launching (engine requires it to exist)
mkdir -p docs/plans/auth-refactor
```

---

## Example: Python project

```json
{
  "extends": "base",
  "name": "api-perf",
  "description": "Optimize API endpoint performance: target p99 < 100ms at 1000 rps",
  "roadmapPath": "docs/backlog/api-perf-roadmap.md",
  "plansDir": "docs/plans/api-perf",
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "docs/audits/api-perf",
  "contextDir": "docs/context",
  "moduleDir": "app/api",
  "commands": {
    "test": "pytest",
    "lint": "ruff check .",
    "typecheck": "mypy app/"
  },
  "commitFormat": "perf(api): <description>"
}
```

Note: Python projects typically omit `build` (no compile step). The mission's CHECK step
will run `test` + `lint` + `typecheck`; BUILD_VERIFY will run `test` only.

---

## Example: Java / Maven project

```json
{
  "extends": "base",
  "name": "payment-debt",
  "description": "Clean up payment module tech debt: hardcoded credentials, mock pollution, pom drift",
  "roadmapPath": "docs/backlog/payment-debt-roadmap.md",
  "plansDir": "docs/plans/payment-debt",
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "docs/audits/payment-debt",
  "contextDir": "docs/context",
  "moduleDir": "payment",
  "commands": {
    "test": "mvn -pl payment -am test -T 4",
    "build": "mvn -pl payment -am clean package -DskipTests -T 4",
    "lint": "mvn -pl payment -am validate",
    "typecheck": "mvn -pl payment -am test-compile -T 4"
  },
  "commitFormat": "<type>: [JIRA-XXXX] [payment] <description>"
}
```

Notes:
- `{MODULE}` in commands is the Maven `-pl` module name (e.g. `payment`), not a path prefix.
- `-am` (also-make) builds the module's dependencies too — critical for multi-module Maven projects.
- `-T 4` enables parallel build (4 threads); common convention.

---

## Example: cross-cutting mission

For work that spans multiple modules, use the project root and reactor-level commands:

```json
{
  "extends": "base",
  "name": "cross-module-cleanup",
  "description": "Cross-cutting cleanup spanning auth and payment modules",
  "roadmapPath": "docs/backlog/cross-module-cleanup-roadmap.md",
  "plansDir": "docs/plans/cross-module-cleanup",
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "docs/audits/cross-module-cleanup",
  "contextDir": "docs/context",
  "moduleDir": ".",
  "commands": {
    "test": "pnpm test",
    "build": "pnpm build",
    "typecheck": "pnpm typecheck"
  },
  "commitFormat": "<type>: <description>"
}
```

`moduleDir: "."` means the audit considers the whole project in scope. Use sparingly —
scoped audits are sharper.

---

## run-state.json (engine runtime state)

Engine runtime state lives in `_tmp/<runDir>/run-state.json` (NOT in the mission.json).
**Never hand-edit runtime state.** When creating a mission config, do not include any
runtime fields.

### How to read runtime state

Path: `_tmp/<runId>/run-state.json`. Structure:

```jsonc
{
  "missionName": "auth-refactor",
  "flowName": "mission-driver",
  "runId": "2026-07-21-095220-mission-driver",
  "pid": 4396,
  "status": "running",                    // running | completed | failed | max_cycles | max_total_steps | ...
  "startedAt": "2026-07-21T01:52:21.171Z",
  "updatedAt": "2026-07-21T06:11:38.005Z",
  "endedAt": null,                        // null while running; ISO timestamp on termination
  "currentStep": "DEEP_AUDIT",            // current step (may lag by a few seconds)
  "steps": [                              // visit log; audit-only, doesn't drive recovery
    {
      "name": "CHECK",
      "visits": 1,
      "status": "completed",
      "marker": "pass",
      "startedAt": "...",
      "endedAt": "...",
      "durationMs": 26649,
      "produced": []                      // .md files added to plansDir during this visit
    }
  ],
  "auditRound": 1,                        // current DEEP_AUDIT round (1-based)
  "maxAuditRounds": 3
}
```

### What to look for

1. **Current step**: `currentStep` (lagging by a few seconds during transitions).
2. **Still running?**: `status === "running"`. More reliable: `pgrep -af mission-driver/src/main.js`.
3. **Progress**: last few entries in `steps[]` — check `marker` and `produced`.
4. **Stuck?**: if last step's `endedAt` is far in the past but `status: "running"`, the
   engine may be hung. Cross-check main log and per-step log mtimes (see SKILL.md C.5).

### status values

| status | meaning | exit code |
|---|---|---|
| `running` | still executing | — |
| `completed` | normal completion (roadmap done or maxAuditRounds exhausted) | 0 |
| `single_step_done` | `--step` mode finished | 0 |
| `failed` | unrecoverable failure | 1 |
| `max_cycles` | single-step visit count exceeded `maxCycleVisits` | 2 |
| `max_total_steps` | total step count exceeded `maxTotalSteps` | 2 |
| `max_retries` | retry budget exhausted | 2 |
| `ping_pong` | two-step death loop detected | 2 |
| `unknown_step` / `unknown_type` / `no_transition` / `invalid_transition` | flow definition error | 1 |

---

## commands field rules

Each command is a **complete shell command string** (the engine splits by spaces,
first segment is the executable). Commands run with `cwd = projectRoot` (no `cd` needed).

| Field | Used by | Required? |
|---|---|---|
| `test` | CHECK + BUILD_VERIFY + EXECUTE (after each phase) | **Yes** |
| `build` | BUILD_VERIFY final build | No (skip if empty/missing) |
| `typecheck` | CHECK + EXECUTE for cross-module changes | No |
| `lint` | BUILD_VERIFY | No |

Rules:
- Empty string (`""`) or missing field = step skips that command.
- **Never** leave `test` empty — it's required.
- On Windows, commands run via `shell: true`, so executables must be in PATH.
- Placeholder commands (containing `<fill ...>`) cause silent CHECK failures — replace before run.

---

## prompts field and audit steps

`prompts.multiAudit` and `prompts.openAudit` are paths to **project-level audit skill
prompts**. They let the mission run customized multi-dimensional / open-ended audits
inside the DEEP_AUDIT subflow.

Mechanism:
- `prompts.multiAudit` non-empty → DEEP_AUDIT runs MULTI_AUDIT step (per the prompt)
- `prompts.openAudit` non-empty → DEEP_AUDIT runs OPEN_AUDIT step
- Either missing → corresponding step is skipped via `when` condition

Audit results write to `auditsDir/<TIMESTAMP>-{multi|open}-audit-<mission>.md` with header:
```
> Audit Status: open
> Audit Type: multi-dimensional | open-ended
> Mission: <mission-name>
```

The engine scans for `Audit Status: open` files, drafts remediation plans for them, then
flips the status to `planned`.

This template ships two default audit prompts:
- `docs/skills/multi-dimensional-audit-prompt.md`
- `docs/skills/open-ended-audit-prompt.md`

Reference them in `missions/base.json` or per-mission `prompts` block.

---

## flowName and custom flows

`flowName` defaults to `"mission-driver"`, loading the built-in `tools/mission-driver/flows/mission-driver.json`.

### Load priority (flows and prompts share the chain)

1. `<missionsDir>/flows/<flowName>.json` — project-level override
2. `<missionsDir>/prompts/<name>.md` — project-level prompt override
3. `tools/mission-driver/flows/<flowName>.json` — built-in
4. `tools/mission-driver/prompts/<name>.md` — built-in

### Customization scenarios

- **Tweak the main loop**: drop `missions/flows/mission-driver.json`; keep `flowName` default.
- **Use a totally different flow**: write `missions/flows/<custom>.json`; set `flowName: "<custom>"`.
- **Override one prompt** (e.g. execute.md): drop `missions/prompts/execute.md`; engine loads it first.
- **Override subflows**: `plan-execution.json` / `deep-audit-loop.json` can also be overridden in `missions/flows/`.

> Normal usage does NOT require custom flows. The built-in flow is battle-tested. Only
> override when you have a specific orchestration need (e.g. inserting a compliance audit step).

### Available steps (built-in mission-driver flow)

`./tools/mission-driver.sh list-steps <mission>` outputs:

- `CHECK` — health check (agent runs typecheck/build/test, self-diagnoses on fail)
- `REVIEW_PLANS` — review draft plans via sub-agent; promote to active (or passthrough if empty)
- `EXEC_PLANS` — run plan-execution subflow per active plan
- `DRAFT_PLANS` — draft 1-3 plans from roadmap + sub-agent review
- `DEEP_AUDIT` — run deep-audit-loop subflow (CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS)

plan-execution subflow steps: `EXECUTE` → `CLOSURE_SCRIPT_CHECK` → `CLOSURE_AUDIT` → `BUILD_VERIFY`.
